/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Repairs the parts of a chart that can be worked out from its links.
 * <p>
 * It removes endpoints that name nothing, merges lines with the same ends, drops branches that no
 * longer lead anywhere, and puts the link type back in step with the ends. It leaves the steps,
 * transitions and their conditions alone.
 */
public final class ChartNormalizer {

    private static final int MAX_PASSES = 20;

    private ChartNormalizer() {
        /* This utility class should not be instantiated */
    }

    /**
     * Repairs a chart in place.
     *
     * @param chart chart to repair, may be {@code null}
     * @return what could not be repaired, empty when the chart is clean
     */
    public static List<String> normalize(S88ProcedureLogic chart) {
        if (chart == null) {
            return List.of();
        }
        Set<String> problems = new LinkedHashSet<>();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = dropDanglingReferences(chart);
            changed |= mergeDuplicateLinks(chart);
            Set<String> reachable = reachableFromBegin(chart);
            changed |= dropDeadEnds(chart, reachable, problems);
            changed |= dropOrphans(chart, reachable);
            changed |= relinkChanged(chart);
            if (!changed) {
                break;
            }
        }
        relink(chart);
        return new ArrayList<>(problems);
    }

    /**
     * Repairs a copy of a chart and reports what could not be repaired.
     *
     * @param chart chart to inspect, may be {@code null}
     * @return what could not be repaired, empty when the chart is clean
     */
    public static List<String> problems(S88ProcedureLogic chart) {
        return normalize(RecipeDeepCopy.copyChart(chart));
    }

    /**
     * Sets each link type from the kinds of node at its ends.
     *
     * @param chart chart whose links are to be labelled, may be {@code null}
     */
    public static void relink(S88ProcedureLogic chart) {
        if (chart == null) {
            return;
        }
        for (S88ProcedureLink link : chart.getLinks()) {
            S88LinkType type = typeFor(chart, link);
            if (type != null) {
                link.setLinkType(type);
            }
        }
    }

    private static boolean dropDanglingReferences(S88ProcedureLogic chart) {
        boolean changed = false;
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            List<S88IdRef> from = existingEnds(chart, link.getFrom());
            List<S88IdRef> to = existingEnds(chart, link.getTo());
            if (from.isEmpty() || to.isEmpty()) {
                chart.removeLink(link);
                changed = true;
            } else if (from.size() != link.getFrom().size() || to.size() != link.getTo().size()) {
                replace(chart, link, from, to, link.getLinkType());
                changed = true;
            }
        }
        return changed;
    }

    private static List<S88IdRef> existingEnds(S88ProcedureLogic chart, List<S88IdRef> ends) {
        List<S88IdRef> kept = new ArrayList<>();
        for (S88IdRef end : ends) {
            if (end == null || end.getValue() == null || end.getValue().isBlank()) {
                continue;
            }
            if (!end.isInternal() || exists(chart, end)) {
                kept.add(end);
            }
        }
        return kept;
    }

    private static boolean exists(S88ProcedureLogic chart, S88IdRef end) {
        return switch (end.getType()) {
            case STEP -> chart.findStep(end.getValue()).isPresent();
            case TRANSITION -> chart.findTransition(end.getValue()).isPresent();
            case LINK -> chart.findLink(end.getValue()).isPresent();
            case OTHER -> false;
        };
    }

    private static boolean mergeDuplicateLinks(S88ProcedureLogic chart) {
        Map<String, S88ProcedureLink> seen = new LinkedHashMap<>();
        boolean changed = false;
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            S88ProcedureLink first = seen.putIfAbsent(wireKey(link), link);
            if (first != null) {
                chart.removeLink(link);
                changed = true;
            }
        }
        return changed;
    }

    private static String wireKey(S88ProcedureLink link) {
        return endsKey(link.getFrom()) + "->" + endsKey(link.getTo());
    }

    private static String endsKey(List<S88IdRef> ends) {
        Set<String> names = new TreeSet<>();
        for (S88IdRef end : ends) {
            names.add(end.getType() + ":" + end.getValue() + ":" + end.getScope());
        }
        return String.join(",", names);
    }

    private static boolean dropDeadEnds(S88ProcedureLogic chart, Set<String> reachable,
                                        Set<String> problems) {
        boolean changed = false;
        Map<String, Boolean> memo = new HashMap<>();
        for (S88ProcedureTransition transition : new ArrayList<>(chart.getTransitions())) {
            String id = transition.getId();
            if (chart.findTransition(id).isEmpty() || hasOutgoing(chart, id)) {
                continue;
            }
            if (reachable != null && !reachable.contains(id)) {
                continue;
            }
            if (removableDeadEnd(chart, id, reachable, new HashSet<>(), memo)) {
                removeNode(chart, id);
                changed = true;
            } else {
                problems.add("Transition '" + id + "' has no exit and cannot be repaired.");
            }
        }
        return changed;
    }

    private static boolean removableDeadEnd(S88ProcedureLogic chart, String id,
                                            Set<String> reachable, Set<String> visiting,
                                            Map<String, Boolean> memo) {
        Boolean known = memo.get(id);
        if (known != null) {
            return known;
        }
        if (!visiting.add(id)) {
            return false;
        }
        boolean removable = false;
        boolean hasPredecessor = false;
        for (S88ProcedureLink link : chart.getLinks()) {
            if (!names(link.getTo()).contains(id)) {
                continue;
            }
            for (S88IdRef end : link.getFrom()) {
                String from = end.getValue();
                if (reachable != null && !reachable.contains(from)) {
                    continue;
                }
                hasPredecessor = true;
                if (chart.findStep(from).isPresent()) {
                    if (!hasOtherOutgoing(chart, from, id)) {
                        visiting.remove(id);
                        memo.put(id, false);
                        return false;
                    }
                } else if (chart.findTransition(from).isPresent()) {
                    if (!hasOtherOutgoing(chart, from, id)
                            && !removableDeadEnd(chart, from, reachable, visiting, memo)) {
                        visiting.remove(id);
                        memo.put(id, false);
                        return false;
                    }
                } else {
                    visiting.remove(id);
                    memo.put(id, false);
                    return false;
                }
            }
        }
        removable = hasPredecessor;
        visiting.remove(id);
        memo.put(id, removable);
        return removable;
    }

    private static boolean hasOtherOutgoing(S88ProcedureLogic chart, String id, String notTo) {
        for (S88ProcedureLink link : chart.getLinks()) {
            if (!names(link.getFrom()).contains(id)) {
                continue;
            }
            for (S88IdRef end : link.getTo()) {
                if (!notTo.equals(end.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOutgoing(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink link : chart.getLinks()) {
            if (names(link.getFrom()).contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> reachableFromBegin(S88ProcedureLogic chart) {
        if (chart.findStep(CreateMasterRecipeUseCase.BEGIN_ID).isEmpty()) {
            return null;
        }
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        reached.add(CreateMasterRecipeUseCase.BEGIN_ID);
        pending.add(CreateMasterRecipeUseCase.BEGIN_ID);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            for (S88ProcedureLink link : chart.getLinks()) {
                if (!names(link.getFrom()).contains(id)) {
                    continue;
                }
                for (S88IdRef end : link.getTo()) {
                    String next = end.getValue();
                    if (!end.isInternal() || !exists(chart, end) || !reached.add(next)) {
                        continue;
                    }
                    pending.addLast(next);
                }
            }
        }
        return reached;
    }

    private static boolean dropOrphans(S88ProcedureLogic chart, Set<String> reachable) {
        if (reachable == null) {
            return false;
        }
        Set<String> orphans = new LinkedHashSet<>();
        for (S88ProcedureStep step : chart.getSteps()) {
            if (!reachable.contains(step.getId())
                    && !CreateMasterRecipeUseCase.BEGIN_ID.equals(step.getId())
                    && !CreateMasterRecipeUseCase.END_ID.equals(step.getId())) {
                orphans.add(step.getId());
            }
        }
        for (S88ProcedureTransition transition : chart.getTransitions()) {
            if (!reachable.contains(transition.getId())) {
                orphans.add(transition.getId());
            }
        }
        if (orphans.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            List<S88IdRef> from = without(link.getFrom(), orphans);
            List<S88IdRef> to = without(link.getTo(), orphans);
            if (from.size() == link.getFrom().size() && to.size() == link.getTo().size()) {
                continue;
            }
            chart.removeLink(link);
            changed = true;
            if (!from.isEmpty() && !to.isEmpty()) {
                chart.addLink(rebuilt(link, from, to, link.getLinkType()));
            }
        }
        for (String id : orphans) {
            chart.findStep(id).ifPresent(chart::removeStep);
            chart.findTransition(id).ifPresent(chart::removeTransition);
        }
        return changed;
    }

    private static List<S88IdRef> without(List<S88IdRef> ends, Set<String> names) {
        List<S88IdRef> kept = new ArrayList<>();
        for (S88IdRef end : ends) {
            if (!end.isInternal() || !names.contains(end.getValue())) {
                kept.add(end);
            }
        }
        return kept;
    }

    private static boolean relinkChanged(S88ProcedureLogic chart) {
        boolean changed = false;
        for (S88ProcedureLink link : chart.getLinks()) {
            S88LinkType type = typeFor(chart, link);
            if (type != null && link.getLinkType() != type) {
                link.setLinkType(type);
                changed = true;
            }
        }
        return changed;
    }

    private static S88LinkType typeFor(S88ProcedureLogic chart, S88ProcedureLink link) {
        List<S88IdRef> from = link.getFrom();
        List<S88IdRef> to = link.getTo();
        if (from.size() == 1 && to.size() == 1) {
            return S88LinkType.CONTROL_LINK;
        }
        if (from.size() == 1 && to.size() >= 2
                && isStep(chart, from.getFirst()) && allTransitions(chart, to)) {
            return S88LinkType.SERIAL_DIVERGENT;
        }
        if (from.size() >= 2 && to.size() == 1
                && allTransitions(chart, from) && isStep(chart, to.getFirst())) {
            return S88LinkType.SERIAL_CONVERGENT;
        }
        if (from.size() == 1 && to.size() >= 2
                && isTransition(chart, from.getFirst()) && allSteps(chart, to)) {
            return S88LinkType.PARALLEL_DIVERGENT;
        }
        if (from.size() >= 2 && to.size() == 1
                && allSteps(chart, from) && isTransition(chart, to.getFirst())) {
            return S88LinkType.PARALLEL_CONVERGENT;
        }
        return null;
    }

    private static boolean isStep(S88ProcedureLogic chart, S88IdRef end) {
        return chart.findStep(end.getValue()).isPresent();
    }

    private static boolean isTransition(S88ProcedureLogic chart, S88IdRef end) {
        return chart.findTransition(end.getValue()).isPresent();
    }

    private static boolean allSteps(S88ProcedureLogic chart, List<S88IdRef> ends) {
        return ends.stream().allMatch(end -> isStep(chart, end));
    }

    private static boolean allTransitions(S88ProcedureLogic chart, List<S88IdRef> ends) {
        return ends.stream().allMatch(end -> isTransition(chart, end));
    }

    private static void removeNode(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            if (!names(link.getFrom()).contains(id) && !names(link.getTo()).contains(id)) {
                continue;
            }
            List<S88IdRef> from = without(link.getFrom(), Set.of(id));
            List<S88IdRef> to = without(link.getTo(), Set.of(id));
            chart.removeLink(link);
            if (!from.isEmpty() && !to.isEmpty()) {
                chart.addLink(rebuilt(link, from, to, link.getLinkType()));
            }
        }
        chart.findStep(id).ifPresent(chart::removeStep);
        chart.findTransition(id).ifPresent(chart::removeTransition);
    }

    private static void replace(S88ProcedureLogic chart, S88ProcedureLink old,
                                List<S88IdRef> from, List<S88IdRef> to, S88LinkType type) {
        chart.removeLink(old);
        chart.addLink(rebuilt(old, from, to, type));
    }

    private static S88ProcedureLink rebuilt(S88ProcedureLink old, List<S88IdRef> from,
                                            List<S88IdRef> to, S88LinkType type) {
        S88ProcedureLink line = new S88ProcedureLink(old.getId());
        from.forEach(line::addFrom);
        to.forEach(line::addTo);
        line.setLinkType(type);
        line.setDepiction(old.getDepiction());
        line.setEvaluationOrder(old.getEvaluationOrder());
        old.getDescriptions().forEach(line::addDescription);
        return line;
    }

    private static Set<String> names(List<S88IdRef> ends) {
        Set<String> names = new HashSet<>();
        for (S88IdRef end : ends) {
            names.add(end.getValue());
        }
        return names;
    }
}
