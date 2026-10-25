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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;

/**
 * Checks that a chart describes a flow that can run: every node is connected, a step never holds
 * two tokens, and the flow can always reach the stop.
 */
public final class ChartSoundness {

    private static final int MAX_STATES = 10_000;
    private static final String BEGIN = CreateMasterRecipeUseCase.BEGIN_ID;
    private static final String END = CreateMasterRecipeUseCase.END_ID;

    private ChartSoundness() {
        /* This utility class should not be instantiated */
    }

    /** The errors that keep the chart from running, without the warnings. */
    public static List<String> check(S88ProcedureLogic chart) {
        return inspect(chart).errors();
    }

    public static Report inspect(S88ProcedureLogic chart) {
        Objects.requireNonNull(chart, "chart");
        ChartGraph graph = ChartGraph.of(chart);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        checkStructure(chart, graph, errors);
        if (graph.hasExternalFlow()) {
            warnings.add("The chart leaves this procedure, so only its structure was checked.");
            return new Report(errors, warnings);
        }
        if (!errors.isEmpty()) {
            return new Report(errors, warnings);
        }

        checkReachability(graph, errors, warnings);
        if (errors.isEmpty()) {
            explore(graph, errors, warnings);
        }
        return new Report(errors, warnings);
    }

    private static void checkStructure(S88ProcedureLogic chart, ChartGraph graph, List<String> errors) {
        for (String dangling : chart.getDanglingReferences()) {
            errors.add("A line names '" + dangling + "', which the chart does not carry.");
        }
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getFrom().isEmpty()) {
                errors.add("Line '" + link.getId() + "' has no start.");
            }
            if (link.getTo().isEmpty()) {
                errors.add("Line '" + link.getId() + "' has no end.");
            }
            checkEnds(link, graph, errors);
        }
        for (S88ProcedureStep step : chart.getSteps()) {
            String id = step.getId();
            if (!BEGIN.equals(id) && !hasIn(chart, id)) {
                errors.add("Step '" + id + "' has no line into it.");
            }
            if (!END.equals(id) && !hasOut(chart, id)) {
                errors.add("Step '" + id + "' has no line out of it.");
            }
        }
        for (S88ProcedureTransition transition : chart.getTransitions()) {
            String id = transition.getId();
            if (!hasIn(chart, id)) {
                errors.add("Transition '" + id + "' has no line into it.");
            }
            if (!hasOut(chart, id)) {
                errors.add("Transition '" + id + "' has no line out of it.");
            }
        }
    }

    private static void checkEnds(S88ProcedureLink link, ChartGraph graph, List<String> errors) {
        boolean fromStep = false;
        boolean fromTransition = false;
        boolean toStep = false;
        boolean toTransition = false;
        for (S88IdRef ref : link.getFrom()) {
            fromStep |= isStep(graph, ref);
            fromTransition |= isTransition(graph, ref);
        }
        for (S88IdRef ref : link.getTo()) {
            toStep |= isStep(graph, ref);
            toTransition |= isTransition(graph, ref);
        }
        if (fromStep && fromTransition) {
            errors.add("Line '" + link.getId() + "' mixes steps and transitions at its start.");
        }
        if (toStep && toTransition) {
            errors.add("Line '" + link.getId() + "' mixes steps and transitions at its end.");
        }
        if (fromStep && toStep) {
            errors.add("Line '" + link.getId() + "' runs from a step to a step.");
        }
    }

    private static void checkReachability(ChartGraph graph, List<String> errors,
                                          List<String> warnings) {
        if (!graph.hasNode(BEGIN)) {
            errors.add("The chart has no start step.");
        }
        if (!graph.hasNode(END)) {
            errors.add("The chart has no stop step.");
        }
        Set<String> fromStart = graph.reachableFrom(BEGIN);
        Set<String> toStop = graph.reaching(END);
        for (String node : graph.nodes()) {
            if (!fromStart.contains(node)) {
                errors.add("'" + node + "' is not reachable from the start.");
            }
            if (!toStop.contains(node)) {
                errors.add("'" + node + "' does not reach the stop.");
            }
        }
        for (String transition : graph.transitions()) {
            Set<String> neighbours = new LinkedHashSet<>(graph.preds(transition));
            neighbours.addAll(graph.succs(transition));
            long steps = neighbours.stream().filter(graph::isStep).count();
            if (isSelfLoop(graph, transition)) {
                continue;
            }
            if (steps < 2 && isSkip(graph, transition)) {
                warnings.add("Transition '" + transition + "' skips a step.");
            } else if (steps < 2) {
                errors.add("Transition '" + transition + "' does not join two steps.");
            }
        }
    }

    private static boolean isSelfLoop(ChartGraph graph, String transition) {
        List<String> before = graph.preds(transition);
        List<String> after = graph.succs(transition);
        return before.size() == 1 && after.size() == 1 && before.equals(after)
                && graph.isStep(before.getFirst());
    }

    private static void explore(ChartGraph graph, List<String> errors, List<String> warnings) {
        Map<String, Marking> states = new LinkedHashMap<>();
        Map<String, List<String>> reverse = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> fired = new LinkedHashSet<>();
        Marking initial = graph.initialMarking();
        states.put(initial.key(), initial);
        pending.add(initial.key());
        boolean truncated = false;
        boolean unsafe = false;
        boolean deadlock = false;
        boolean endWithOthers = false;

        while (!pending.isEmpty()) {
            String key = pending.removeFirst();
            Marking marking = states.get(key);
            if (carriesMoreThanOneToken(marking)) {
                unsafe = true;
            }
            if (marking.has(END) && marking.active().size() > 1) {
                endWithOthers = true;
            }
            boolean atStop = isAtStop(marking);
            List<ChartGraph.Firing> successors = graph.successors(marking);
            if (successors.isEmpty() && !atStop) {
                deadlock = true;
            }
            for (ChartGraph.Firing firing : successors) {
                fired.addAll(firing.firedTransitions());
                Marking next = firing.marking();
                if (carriesMoreThanOneToken(next)) {
                    unsafe = true;
                }
                if (next.has(END) && next.active().size() > 1) {
                    endWithOthers = true;
                }
                String nextKey = next.key();
                reverse.computeIfAbsent(nextKey, ignored -> new ArrayList<>()).add(key);
                if (states.containsKey(nextKey)) {
                    continue;
                }
                if (states.size() >= MAX_STATES) {
                    truncated = true;
                    break;
                }
                states.put(nextKey, next);
                pending.addLast(nextKey);
            }
            if (truncated) {
                break;
            }
        }

        if (unsafe) {
            errors.add("A step holds more than one token.");
        }
        if (endWithOthers) {
            errors.add("The stop is reached while other steps still hold a token.");
        }
        if (deadlock) {
            errors.add("The flow can come to a stop before the stop step.");
        }
        if (truncated) {
            warnings.add("The chart has more than " + MAX_STATES
                    + " markings; soundness could not be fully checked.");
            return;
        }
        checkEveryMarkingReachesTheStop(states, reverse, errors);
        for (String transition : graph.transitions()) {
            if (!fired.contains(transition)) {
                errors.add("Transition '" + transition + "' never fires.");
            }
        }
    }

    private static void checkEveryMarkingReachesTheStop(Map<String, Marking> states,
                                                        Map<String, List<String>> reverse,
                                                        List<String> errors) {
        Set<String> canReach = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (Map.Entry<String, Marking> entry : states.entrySet()) {
            if (isAtStop(entry.getValue())) {
                canReach.add(entry.getKey());
                pending.add(entry.getKey());
            }
        }
        if (canReach.isEmpty()) {
            errors.add("The flow never reaches the stop.");
            return;
        }
        while (!pending.isEmpty()) {
            String key = pending.removeFirst();
            for (String before : reverse.getOrDefault(key, List.of())) {
                if (canReach.add(before)) {
                    pending.addLast(before);
                }
            }
        }
        int unreported = 0;
        for (Map.Entry<String, Marking> entry : states.entrySet()) {
            if (canReach.contains(entry.getKey())) {
                continue;
            }
            if (unreported < 5) {
                errors.add("From " + entry.getValue() + " the flow cannot reach the stop.");
            }
            unreported++;
        }
        if (unreported > 5) {
            errors.add(unreported - 5 + " more markings cannot reach the stop.");
        }
    }

    private static boolean isSkip(ChartGraph graph, String transition) {
        return graph.succs(transition).stream().anyMatch(graph::isTransition)
                || graph.preds(transition).stream().anyMatch(graph::isTransition);
    }

    private static boolean isAtStop(Marking marking) {
        return marking.active().size() == 1 && marking.count(END) == 1;
    }

    private static boolean carriesMoreThanOneToken(Marking marking) {
        return marking.active().stream().anyMatch(step -> marking.count(step) > 1);
    }

    private static boolean hasIn(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink link : chart.getLinks()) {
            for (S88IdRef ref : link.getTo()) {
                if (id.equals(ref.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOut(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink link : chart.getLinks()) {
            for (S88IdRef ref : link.getFrom()) {
                if (id.equals(ref.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStep(ChartGraph graph, S88IdRef ref) {
        return ref != null && ref.isInternal() && graph.isStep(ref.getValue());
    }

    private static boolean isTransition(ChartGraph graph, S88IdRef ref) {
        return ref != null && ref.isInternal() && graph.isTransition(ref.getValue());
    }

    public record Report(List<String> errors, List<String> warnings) {
        public Report {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean isSound() {
            return errors.isEmpty();
        }
    }
}
