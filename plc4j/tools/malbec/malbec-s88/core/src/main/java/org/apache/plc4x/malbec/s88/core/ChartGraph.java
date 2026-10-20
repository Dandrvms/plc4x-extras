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
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;

/**
 * The steps and transitions of a chart as a graph, with how a token moves through it.
 */
public final class ChartGraph {

    private final Set<String> steps;
    private final Set<String> transitions;
    private final List<Junction> junctions;
    private final Map<String, List<Junction>> incoming;
    private final Map<String, List<Junction>> outgoing;
    private final boolean externalFlow;

    private ChartGraph(Set<String> steps, Set<String> transitions, List<Junction> junctions,
                       boolean externalFlow) {
        this.steps = Collections.unmodifiableSet(steps);
        this.transitions = Collections.unmodifiableSet(transitions);
        this.junctions = List.copyOf(junctions);
        this.externalFlow = externalFlow;
        Map<String, List<Junction>> in = new LinkedHashMap<>();
        Map<String, List<Junction>> out = new LinkedHashMap<>();
        for (Junction junction : junctions) {
            for (String from : junction.froms()) {
                out.computeIfAbsent(from, key -> new ArrayList<>()).add(junction);
            }
            for (String to : junction.tos()) {
                in.computeIfAbsent(to, key -> new ArrayList<>()).add(junction);
            }
        }
        this.incoming = freeze(in);
        this.outgoing = freeze(out);
    }

    public static ChartGraph of(S88ProcedureLogic chart) {
        Objects.requireNonNull(chart, "chart");
        Set<String> steps = new LinkedHashSet<>();
        for (S88ProcedureStep step : chart.getSteps()) {
            steps.add(step.getId());
        }
        Set<String> transitions = new LinkedHashSet<>();
        for (S88ProcedureTransition transition : chart.getTransitions()) {
            transitions.add(transition.getId());
        }
        Set<String> known = new LinkedHashSet<>(steps);
        known.addAll(transitions);

        List<Junction> junctions = new ArrayList<>();
        boolean externalFlow = false;
        for (S88ProcedureLink link : chart.getLinks()) {
            List<String> froms = internalEnds(link.getFrom(), known);
            List<String> tos = internalEnds(link.getTo(), known);
            if (hasInternalEnd(link) && hasExternalEnd(link)) {
                externalFlow = true;
            }
            if (!froms.isEmpty() && !tos.isEmpty()) {
                junctions.add(new Junction(link.getId(), froms, tos, link.getLinkType()));
            }
        }
        return new ChartGraph(steps, transitions, junctions, externalFlow);
    }

    public Set<String> steps() {
        return steps;
    }

    public Set<String> transitions() {
        return transitions;
    }

    public Set<String> nodes() {
        Set<String> nodes = new LinkedHashSet<>(steps);
        nodes.addAll(transitions);
        return Collections.unmodifiableSet(nodes);
    }

    public boolean isStep(String id) {
        return steps.contains(id);
    }

    public boolean isTransition(String id) {
        return transitions.contains(id);
    }

    public boolean hasNode(String id) {
        return steps.contains(id) || transitions.contains(id);
    }

    /** True when a line crosses the border of the procedure, so the flow cannot be followed here. */
    public boolean hasExternalFlow() {
        return externalFlow;
    }

    public List<Junction> inJunctions(String id) {
        return incoming.getOrDefault(id, List.of());
    }

    public List<Junction> outJunctions(String id) {
        return outgoing.getOrDefault(id, List.of());
    }

    public List<String> preds(String id) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (Junction junction : inJunctions(id)) {
            found.addAll(junction.froms());
        }
        return List.copyOf(found);
    }

    public List<String> succs(String id) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (Junction junction : outJunctions(id)) {
            found.addAll(junction.tos());
        }
        return List.copyOf(found);
    }

    public Set<String> reachableFrom(String id) {
        return walk(id, true);
    }

    public Set<String> reaching(String id) {
        return walk(id, false);
    }

    public Set<Edge> backEdges() {
        Set<Edge> back = new LinkedHashSet<>();
        Map<String, Integer> color = new LinkedHashMap<>();
        String begin = CreateMasterRecipeUseCase.BEGIN_ID;
        if (hasNode(begin)) {
            visit(begin, color, back);
        }
        for (String node : nodes()) {
            if (!color.containsKey(node)) {
                visit(node, color, back);
            }
        }
        return Collections.unmodifiableSet(back);
    }

    public boolean isSkip(String from, String to) {
        return isTransition(from) && isTransition(to) && succs(from).contains(to);
    }

    public Marking initialMarking() {
        return Marking.of(CreateMasterRecipeUseCase.BEGIN_ID);
    }

    public List<String> enabled(Marking marking) {
        List<String> enabled = new ArrayList<>();
        for (String transition : transitions) {
            if (!inboundOptions(transition, marking).isEmpty()) {
                enabled.add(transition);
            }
        }
        return List.copyOf(enabled);
    }

    /**
     * Fires one transition and takes the first of its possible outcomes.
     *
     * @throws IllegalArgumentException when the transition is not enabled by the marking
     */
    public Marking fire(Marking marking, String transition) {
        List<Firing> firings = fireTransition(transition, marking, Set.of());
        if (firings.isEmpty()) {
            throw new IllegalArgumentException("Transition '" + transition + "' is not enabled");
        }
        return firings.get(0).marking();
    }

    /** Every marking the flow can reach from here, one per transition and branch choice. */
    public List<Firing> successors(Marking marking) {
        Objects.requireNonNull(marking, "marking");
        Set<Firing> successors = new LinkedHashSet<>();
        for (String transition : enabled(marking)) {
            successors.addAll(fireTransition(transition, marking, Set.of()));
        }
        return List.copyOf(successors);
    }

    private List<Firing> fireTransition(String transition, Marking marking, Set<String> visited) {
        if (visited.contains(transition)) {
            return List.of();
        }
        Set<String> onPath = new LinkedHashSet<>(visited);
        onPath.add(transition);
        List<Firing> firings = new ArrayList<>();
        for (List<String> consumed : inboundOptions(transition, marking)) {
            Marking after = marking;
            for (String step : consumed) {
                after = after.remove(step);
            }
            firings.addAll(expand(transition, after, onPath));
        }
        return firings;
    }

    private List<Firing> expand(String transition, Marking marking, Set<String> onPath) {
        List<Junction> exits = outJunctions(transition);
        if (exits.isEmpty()) {
            return List.of(new Firing(marking, onPath));
        }
        Set<Firing> firings = new LinkedHashSet<>();
        for (Junction exit : exits) {
            List<String> stepTargets = new ArrayList<>();
            List<String> transitionTargets = new ArrayList<>();
            for (String target : exit.tos()) {
                if (isStep(target)) {
                    stepTargets.add(target);
                } else if (isTransition(target)) {
                    transitionTargets.add(target);
                }
            }
            if (!stepTargets.isEmpty()) {
                if (exit.kind().isDivergent() && exit.kind().isParallel()) {
                    Marking after = marking;
                    for (String step : stepTargets) {
                        after = after.add(step);
                    }
                    firings.add(new Firing(after, onPath));
                } else {
                    for (String step : stepTargets) {
                        firings.add(new Firing(marking.add(step), onPath));
                    }
                }
            }
            for (String target : transitionTargets) {
                firings.addAll(skipTo(target, marking, onPath));
            }
        }
        if (firings.isEmpty()) {
            firings.add(new Firing(marking, onPath));
        }
        return List.copyOf(firings);
    }

    private List<Firing> skipTo(String transition, Marking marking, Set<String> onPath) {
        if (onPath.contains(transition)) {
            return List.of();
        }
        Set<String> next = new LinkedHashSet<>(onPath);
        next.add(transition);
        return expand(transition, marking, next);
    }

    private List<List<String>> inboundOptions(String transition, Marking marking) {
        List<List<String>> options = new ArrayList<>();
        for (Junction entry : inJunctions(transition)) {
            List<String> sources = new ArrayList<>();
            List<String> active = new ArrayList<>();
            for (String from : entry.froms()) {
                if (!isStep(from)) {
                    continue;
                }
                sources.add(from);
                if (marking.has(from)) {
                    active.add(from);
                }
            }
            if (sources.isEmpty()) {
                continue;
            }
            if (entry.kind() == S88LinkType.PARALLEL_CONVERGENT) {
                if (active.size() == sources.size()) {
                    options.add(List.copyOf(sources));
                }
            } else {
                for (String source : active) {
                    options.add(List.of(source));
                }
            }
        }
        return options;
    }

    private Set<String> walk(String start, boolean forward) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String node = pending.removeFirst();
            if (!seen.add(node)) {
                continue;
            }
            for (String next : forward ? succs(node) : preds(node)) {
                if (!seen.contains(next)) {
                    pending.addLast(next);
                }
            }
        }
        return Collections.unmodifiableSet(seen);
    }

    private void visit(String node, Map<String, Integer> color, Set<Edge> back) {
        color.put(node, 1);
        for (Junction junction : outJunctions(node)) {
            for (String next : junction.tos()) {
                Integer state = color.get(next);
                if (state != null && state == 1) {
                    back.add(new Edge(node, next));
                } else if (state == null) {
                    visit(next, color, back);
                }
            }
        }
        color.put(node, 2);
    }

    private static List<String> internalEnds(List<S88IdRef> refs, Set<String> known) {
        List<String> ends = new ArrayList<>();
        for (S88IdRef ref : refs) {
            if (ref != null && ref.isInternal() && known.contains(ref.getValue())
                    && !ends.contains(ref.getValue())) {
                ends.add(ref.getValue());
            }
        }
        return ends;
    }

    private static boolean hasInternalEnd(S88ProcedureLink link) {
        return link.getFrom().stream().anyMatch(S88IdRef::isInternal)
                || link.getTo().stream().anyMatch(S88IdRef::isInternal);
    }

    private static boolean hasExternalEnd(S88ProcedureLink link) {
        return link.getFrom().stream().anyMatch(ref -> !ref.isInternal())
                || link.getTo().stream().anyMatch(ref -> !ref.isInternal());
    }

    private static Map<String, List<Junction>> freeze(Map<String, List<Junction>> map) {
        Map<String, List<Junction>> frozen = new LinkedHashMap<>();
        map.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }

    public record Junction(String linkId, List<String> froms, List<String> tos, S88LinkType kind) {
        public Junction {
            froms = List.copyOf(froms);
            tos = List.copyOf(tos);
            kind = kind != null ? kind : S88LinkType.CONTROL_LINK;
        }
    }

    public record Edge(String from, String to) {
    }

    public record Firing(Marking marking, Set<String> firedTransitions) {
        public Firing {
            Objects.requireNonNull(marking, "marking");
            firedTransitions = Collections.unmodifiableSet(new LinkedHashSet<>(firedTransitions));
        }
    }
}
