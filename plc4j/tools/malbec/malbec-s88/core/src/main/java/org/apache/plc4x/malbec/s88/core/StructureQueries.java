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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;

/**
 * What can be said about the shape of a chart by reading it.
 *
 * <p>The rules the chart is drawn against live here, and the actions that change a chart ask them
 * here. A chart holds <em>blocks</em>: a parallel one opens where the flow splits and closes where it
 * is held in step again, and a selective one opens where the flow goes one way or the other. A block
 * that opens inside another one closes inside it, and a branch that leaves the block it opened in
 * leaves lines the chart cannot draw.
 *
 */
public enum StructureQueries {
    ;

    /**
     * The block the node is held in, which is the one nearest to it.
     *
     * <p>A node inside a block that is inside another one is held in both, and the nearest is the
     * one it sits on. A node outside every block is held in none.
     *
     * @param logic  the chart to read
     * @param nodeId id of a box or a bar
     * @return the kind of the nearest block, or {@code null} when there is none
     */
    public static S88LinkType blockOf(S88ProcedureLogic logic, String nodeId) {
        List<S88LinkType> held = enclosingBlocks(logic, nodeId);
        return held.isEmpty() ? null : held.getFirst();
    }

    /**
     * Whether the node sits on a parallel block, on either side of it.
     *
     * @param logic  the chart to read
     * @param nodeId id of a box or a bar
     * @return whether the flow runs in step where the node is
     */
    public static boolean isInsideParallel(S88ProcedureLogic logic, String nodeId) {
        return enclosingBlocks(logic, nodeId).stream()
                .anyMatch(S88LinkType::isParallel);
    }

    /**
     * Whether the node sits on a block where only one side is taken.
     *
     * @param logic  the chart to read
     * @param nodeId id of a box or a bar
     * @return whether the flow goes one way or the other where the node is
     */
    public static boolean isInsideSelective(S88ProcedureLogic logic, String nodeId) {
        return enclosingBlocks(logic, nodeId).stream()
                .anyMatch(kind -> kind.isDivergent() && !kind.isParallel());
    }

    /**
     * Every block that holds the node, the innermost first.
     *
     * <p>The walk goes back up the flow. Every line that splits the flow is a block the node is
     * inside, because the node is reached through one of its sides, and every such line that is
     * crossed adds the block it opens. The walk stops at the line that brings sides back together,
     * because by then the flow has come out of that block.
     *
     * <p>A bar the flow splits at is the opening of its own block, so it is inside it.
     *
     * @param logic  the chart to read
     * @param nodeId id of a box or a bar
     * @return the kinds of the blocks that hold it, innermost first
     */
    public static List<S88LinkType> enclosingBlocks(S88ProcedureLogic logic, String nodeId) {
        List<S88LinkType> found = new ArrayList<>();
        S88ProcedureLink firstStep = leavingFrom(logic, nodeId);
        if (firstStep != null && firstStep.isDivergent()) {
            found.add(firstStep.getLinkType());
        }
        Set<String> been = new LinkedHashSet<>();
        String at = nodeId;
        while (at != null && been.add(at)) {
            S88ProcedureLink back = arrivingAt(logic, at);
            if (back == null || back.isConvergent()) {
                return found;
            }
            if (back.isDivergent()) {
                found.add(back.getLinkType());
            }
            at = firstFrom(back);
        }
        return found;
    }

    /**
     * What the line the flow leaves the node by is.
     *
     * @param logic  the chart to read
     * @param nodeId id of a box or a bar
     * @return the line, or {@code null} when the node leads nowhere
     */
    public static S88ProcedureLink leavingFrom(S88ProcedureLogic logic, String nodeId) {
        return logic.getLinks().stream()
                .filter(link -> link.getFrom().stream()
                        .anyMatch(end -> nodeId.equals(end.getValue())))
                .findFirst()
                .orElse(null);
    }

    /**
     * What the line the flow arrives at the node by is.
     *
     * @param logic  the chart to read
     * @param nodeId id of a box or a bar
     * @return the line, or {@code null} when nothing leads to the node
     */
    public static S88ProcedureLink arrivingAt(S88ProcedureLogic logic, String nodeId) {
        return logic.getLinks().stream()
                .filter(link -> link.getTo().stream()
                        .anyMatch(end -> nodeId.equals(end.getValue())))
                .findFirst()
                .orElse(null);
    }

    /**
     * Every line of the chart that does not cross a bar.
     *
     * <p>A line has to cross a bar, because the flow leaves a box, waits at a bar and arrives at a
     * box again. A line that does not cross is one the drawing cannot follow, and it is what a
     * branch leaves behind when it is taken off without the line that held it. Ends that leave the
     * chart are left out, because nothing is known about what alternates outside it.
     *
     * @param logic the chart to read
     * @return the lines inside the chart that run between two boxes or two bars
     */
    public static List<S88ProcedureLink> linesThatDoNotCross(S88ProcedureLogic logic) {
        List<S88ProcedureLink> found = new ArrayList<>();
        for (S88ProcedureLink link : logic.getLinks()) {
            if (link.getFrom().isEmpty() || link.getTo().isEmpty()) {
                continue;
            }
            if (link.getFrom().stream().anyMatch(end -> !end.isInternal())
                    || link.getTo().stream().anyMatch(end -> !end.isInternal())) {
                continue;
            }
            boolean fromIsBoxes = allAreBoxes(link.getFrom());
            boolean toIsBoxes = allAreBoxes(link.getTo());
            if (fromIsBoxes == toIsBoxes) {
                if (!fromIsBoxes && isOptionalSkip(logic, link)) {
                    continue;
                }
                found.add(link);
            }
        }
        return found;
    }

    /**
     * Whether a line is the way round a step that can be left out.
     *
     * <p>The line leaves one side of a selective split and arrives at the bar where the other side
     * comes back together. It is the only line allowed between two bars, and only when it really
     * skips work: another side of the split has to reach a step that feeds the bar it arrives at.
     *
     * @param logic the chart to read
     * @param link  the line to look at
     * @return whether the line is an optional skip
     */
    public static boolean isOptionalSkip(S88ProcedureLogic logic, S88ProcedureLink link) {
        if (logic == null || link == null || link.isDivergent() || link.isConvergent()
                || link.getFrom().size() != 1 || link.getTo().size() != 1) {
            return false;
        }
        S88IdRef from = link.getFrom().getFirst();
        S88IdRef to = link.getTo().getFirst();
        if (from.getType() != S88IdRefType.TRANSITION
                || to.getType() != S88IdRefType.TRANSITION
                || logic.findTransition(from.getValue()).isEmpty()
                || logic.findTransition(to.getValue()).isEmpty()) {
            return false;
        }
        List<S88ProcedureLink> arriving = linksMeeting(logic, from.getValue(), true);
        List<S88ProcedureLink> leaving = linksMeeting(logic, from.getValue(), false);
        if (arriving.size() != 1 || leaving.size() != 1) {
            return false;
        }
        S88ProcedureLink split = arriving.getFirst();
        if (split.equals(link) || !split.isDivergent()
                || split.getLinkType().isParallel() || split.getTo().size() < 2) {
            return false;
        }
        for (S88IdRef side : split.getTo()) {
            if (side.getValue().equals(from.getValue())
                    || side.getType() != S88IdRefType.TRANSITION) {
                continue;
            }
            if (reachesAStepFeeding(logic, side.getValue(), to.getValue(), link)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one side of a split reaches a step that goes on to the given bar. */
    private static boolean reachesAStepFeeding(S88ProcedureLogic logic, String start,
                                               String targetBar, S88ProcedureLink skip) {
        for (S88ProcedureLink into : linksMeeting(logic, targetBar, true)) {
            if (into == skip || into.getFrom().size() != 1 || into.getTo().size() != 1) {
                continue;
            }
            S88IdRef from = into.getFrom().getFirst();
            if (from.getType() == S88IdRefType.STEP
                    && reachesWithout(logic, start, from.getValue(), skip)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the flow reaches one node from another without using the given line. */
    private static boolean reachesWithout(S88ProcedureLogic logic, String start, String target,
                                          S88ProcedureLink skip) {
        Deque<String> open = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        open.add(start);
        while (!open.isEmpty()) {
            String at = open.removeFirst();
            if (!seen.add(at)) {
                continue;
            }
            for (S88ProcedureLink link : logic.getLinks()) {
                if (link == skip) {
                    continue;
                }
                if (link.getFrom().stream()
                        .anyMatch(end -> end.isInternal() && at.equals(end.getValue()))) {
                    for (S88IdRef end : link.getTo()) {
                        if (!end.isInternal()) {
                            continue;
                        }
                        if (target.equals(end.getValue())) {
                            return true;
                        }
                        open.addLast(end.getValue());
                    }
                }
            }
        }
        return false;
    }

    /**
     * The bar of the loop connection a node belongs to.
     *
     * <p>A loop connection leaves a step, waits at a bar of its own and arrives at a step the flow
     * already went through. Its bar has one line in and one line out, and the step it leaves has
     * another way on, or the step it arrives at another way in.
     *
     * @param logic  the chart to read
     * @param nodeId a bar, or a line that ends at one
     * @return the bar of the loop connection, or {@code null} when the node is not part of one
     */
    public static String loopTransitionOf(S88ProcedureLogic logic, String nodeId) {
        String barId = barOf(logic, nodeId);
        if (barId == null) {
            return null;
        }
        List<S88ProcedureLink> arriving = linksMeeting(logic, barId, true);
        List<S88ProcedureLink> leaving = linksMeeting(logic, barId, false);
        if (arriving.size() != 1 || leaving.size() != 1) {
            return null;
        }
        S88ProcedureLink in = arriving.getFirst();
        S88ProcedureLink out = leaving.getFirst();
        if (in.getFrom().size() != 1 || out.getTo().size() != 1) {
            return null;
        }
        S88IdRef from = in.getFrom().getFirst();
        S88IdRef to = out.getTo().getFirst();
        if (!from.isInternal() || !to.isInternal()
                || logic.findStep(from.getValue()).isEmpty()
                || logic.findStep(to.getValue()).isEmpty()) {
            return null;
        }
        boolean rejoinsTheFlow = reaches(logic, to.getValue(), from.getValue());
        boolean atAJunction = incomingWays(logic, to.getValue()) > 1
                || outgoingWays(logic, from.getValue()) > 1;
        return rejoinsTheFlow && atAJunction ? barId : null;
    }

    /** The bar a node is, or the single bar a line ends at. */
    private static String barOf(S88ProcedureLogic logic, String nodeId) {
        if (nodeId == null) {
            return null;
        }
        if (logic.findTransition(nodeId).isPresent()) {
            return nodeId;
        }
        S88ProcedureLink line = logic.findLink(nodeId).orElse(null);
        if (line == null) {
            return null;
        }
        String bar = null;
        for (S88IdRef end : line.getFrom()) {
            if (end.getType() == S88IdRefType.TRANSITION) {
                if (bar != null) {
                    return null;
                }
                bar = end.getValue();
            }
        }
        for (S88IdRef end : line.getTo()) {
            if (end.getType() == S88IdRefType.TRANSITION) {
                if (bar != null) {
                    return null;
                }
                bar = end.getValue();
            }
        }
        return bar;
    }

    /** The lines that arrive at the bar, or the ones that leave it. */
    private static List<S88ProcedureLink> linksMeeting(
            S88ProcedureLogic logic, String barId, boolean arriving) {
        List<S88ProcedureLink> found = new ArrayList<>();
        for (S88ProcedureLink link : logic.getLinks()) {
            List<S88IdRef> ends = arriving ? link.getTo() : link.getFrom();
            if (ends.stream().anyMatch(end -> barId.equals(end.getValue()))) {
                found.add(link);
            }
        }
        return found;
    }

    /** How many places the flow can go on to from the node. */
    private static int outgoingWays(S88ProcedureLogic logic, String nodeId) {
        int ways = 0;
        for (S88ProcedureLink link : logic.getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> nodeId.equals(end.getValue()))) {
                ways += link.getTo().size();
            }
        }
        return ways;
    }

    /** How many places the flow can arrive from at the node. */
    private static int incomingWays(S88ProcedureLogic logic, String nodeId) {
        int ways = 0;
        for (S88ProcedureLink link : logic.getLinks()) {
            if (link.getTo().stream().anyMatch(end -> nodeId.equals(end.getValue()))) {
                ways += link.getFrom().size();
            }
        }
        return ways;
    }

    /** Whether the flow reaches one node from another by following the lines. */
    private static boolean reaches(S88ProcedureLogic logic, String start, String target) {
        Deque<String> open = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        open.add(start);
        while (!open.isEmpty()) {
            String at = open.removeFirst();
            if (at.equals(target)) {
                return true;
            }
            if (!seen.add(at)) {
                continue;
            }
            for (S88ProcedureLink link : logic.getLinks()) {
                if (link.getFrom().stream()
                        .anyMatch(end -> end.isInternal() && at.equals(end.getValue()))) {
                    link.getTo().stream()
                            .filter(S88IdRef::isInternal)
                            .forEach(end -> open.addLast(end.getValue()));
                }
            }
        }
        return false;
    }

    /**
     * Where the flow was when the line it is on now was left.
     *
     * @param link the line to read
     * @return the first end the flow leaves by, or {@code null} when it has none
     */
    private static String firstFrom(S88ProcedureLink link) {
        return link.getFrom().isEmpty() ? null : link.getFrom().getFirst().getValue();
    }

    private static boolean allAreBoxes(List<S88IdRef> ends) {
        return ends.stream().allMatch(end -> end.getType() == S88IdRefType.STEP);
    }
}
