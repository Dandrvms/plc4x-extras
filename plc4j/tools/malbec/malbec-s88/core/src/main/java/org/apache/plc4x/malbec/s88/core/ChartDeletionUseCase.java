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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * Takes a box or a bar off a chart and joins the flow across it.
 *
 * <p>The chart is worked out on a copy and checked before the recipe is touched, so an edit that
 * cannot be joined again leaves the recipe as it was. Only problems the edit introduces are
 * refused; a chart that was already irregular can still be edited elsewhere.
 *
 * @see ChartBranchingUseCase
 * @see RecipeDeepCopy#copyChart(S88ProcedureLogic)
 */
public final class ChartDeletionUseCase {

    private static final int MAX_SMOOTHING_PASSES = 200;

    private ChartDeletionUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Takes a step off the chart and puts the flow back together behind it.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box to take off
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the box is where the flow starts or stops, or when
     *                                  removing it would leave a chart that cannot be drawn
     */
    public static void deleteBox(S88Recipe recipe, S88RecipeElement step, String boxId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to delete from.");
        }
        S88ProcedureStep box = chart.findStep(boxId).orElseThrow(
                () -> new IllegalArgumentException("This chart has no step called '" + boxId + "'."));
        refuseIfEnd(recipe, box);
        String elementId = box.getRecipeElementId();
        refuseIfNestedChartsNameIt(recipe, chart, boxId, elementId);

        List<String> before = ChartSoundness.check(chart);
        S88ProcedureLogic joined = RecipeDeepCopy.copyChart(chart);
        Set<String> drawnBefore = drawnElementIds(joined);
        deleteStep(joined, boxId);
        ChartNormalizer.normalize(joined);
        drawnBefore.removeAll(drawnElementIds(joined));
        drawnBefore.addAll(sweepUpWhatNothingReaches(joined));
        refuseNewProblems("Taking '" + boxId + "' off the chart", before, joined);

        copyOnto(joined, chart);
        recipe.findElement(elementId).ifPresent(recipe::removeRecipeElement);
        drawnBefore.forEach(id -> recipe.findElement(id).ifPresent(recipe::removeRecipeElement));
        recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
    }

    /**
     * Takes a bar off the chart.
     *
     * <p>A bar that skips a step or closes a loop goes on its own. The first bar of a selective
     * branch takes the whole branch with it. A bar in the middle of a run is refused, because
     * taking it off would leave a step with no bar to reach it.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param barId  bar to take off
     * @throws IllegalArgumentException when there is no chart or no such bar
     * @throws IllegalStateException    when removing the bar would leave a chart that cannot be
     *                                  drawn, or when the bar belongs to a step
     */
    public static void deleteTransition(S88Recipe recipe, S88RecipeElement step, String barId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to delete from.");
        }
        chart.findTransition(barId).orElseThrow(
                () -> new IllegalArgumentException("This chart has no transition called '" + barId
                        + "'."));

        List<String> before = ChartSoundness.check(chart);
        S88ProcedureLogic joined = RecipeDeepCopy.copyChart(chart);
        Set<String> drawnBefore = drawnElementIds(joined);
        deleteTransition(joined, barId);
        ChartNormalizer.normalize(joined);
        drawnBefore.removeAll(drawnElementIds(joined));
        drawnBefore.addAll(sweepUpWhatNothingReaches(joined));
        refuseNewProblems("Taking '" + barId + "' off the chart", before, joined);

        copyOnto(joined, chart);
        drawnBefore.forEach(id -> recipe.findElement(id).ifPresent(recipe::removeRecipeElement));
        recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
    }

    /**
     * Takes a whole line off the chart.
     *
     * <p>The chart is worked out on a copy and checked before the recipe is touched. A line that
     * would leave a step without a way in or out is refused.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param lineId line to take off
     * @throws IllegalArgumentException when there is no chart or no such line
     * @throws IllegalStateException    when the chart would no longer run
     */
    public static void deleteLink(S88Recipe recipe, S88RecipeElement step, String lineId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to delete from.");
        }
        if (chart.findLink(lineId).isEmpty()) {
            throw new IllegalArgumentException("This chart has no line called '" + lineId + "'.");
        }

        List<String> before = ChartSoundness.check(chart);
        S88ProcedureLogic joined = RecipeDeepCopy.copyChart(chart);
        joined.findLink(lineId).ifPresent(joined::removeLink);
        ChartNormalizer.normalize(joined);
        refuseNewProblems("Taking line '" + lineId + "' off the chart", before, joined);

        copyOnto(joined, chart);
        recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
    }

    private static void deleteTransition(S88ProcedureLogic chart, String barId) {
        ChartGraph graph = ChartGraph.of(chart);
        boolean loop = graph.backEdges().stream()
                .anyMatch(edge -> edge.from().equals(barId) || edge.to().equals(barId));
        boolean skip = graph.succs(barId).stream().anyMatch(graph::isTransition);
        if (loop || skip) {
            detach(chart, barId);
            chart.findTransition(barId).ifPresent(chart::removeTransition);
            return;
        }

        S88ProcedureLink opening = branchOpening(chart, barId);
        if (opening != null) {
            removeBranch(chart, opening, barId);
            return;
        }

        boolean afterAStep = graph.preds(barId).stream().anyMatch(graph::isStep);
        boolean beforeAStep = graph.succs(barId).stream().anyMatch(graph::isStep);
        if (afterAStep && beforeAStep) {
            throw new IllegalStateException("Transition '" + barId + "' sits between two steps."
                    + " Delete a step in its place. Nothing has been changed.");
        }
        throw new IllegalStateException("Transition '" + barId + "' is not a skip, a loop or the"
                + " first bar of a branch, so there is nothing this edit can take off. Nothing has"
                + " been changed.");
    }

    /**
     * Refuses a deletion that would leave a box in another chart working on a step that is gone.
     */
    private static void refuseIfNestedChartsNameIt(S88Recipe recipe, S88ProcedureLogic editing,
                                                   String boxId, String elementId) {
        List<String> pointing = new ArrayList<>();
        collectBoxes(pointing, elementId, recipe.getProcedureLogic(), editing, boxId);
        for (S88RecipeElement element : recipe.getAllElements()) {
            collectBoxes(pointing, elementId, element.getProcedureLogic(), editing, boxId);
        }
        if (!pointing.isEmpty()) {
            throw new IllegalStateException("Step '" + elementId + "' is still drawn in "
                + String.join(", ", pointing) + ", so taking it off this chart would leave a line"
                + " going to nothing. Take it off that chart first.");
        }
    }

    private static void collectBoxes(List<String> pointing, String elementId, S88ProcedureLogic chart,
                                     S88ProcedureLogic editing, String boxId) {
        if (chart == null || chart == editing) {
            return;
        }
        chart.getSteps().stream()
            .filter(box -> elementId.equals(box.getRecipeElementId()))
            .forEach(box -> pointing.add("'" + box.getId() + "'"));
    }

    /**
     * Where the step sits decides which bars stay and which are joined across it.
     */
    private static void deleteStep(S88ProcedureLogic chart, String boxId) {
        ChartGraph graph = ChartGraph.of(chart);
        List<String> before = transitionsAmong(graph.preds(boxId), graph);
        List<String> after = transitionsAmong(graph.succs(boxId), graph);
        Set<String> earlierBars = branchBars(chart, before);

        if (isWholeBranch(chart, boxId, before, after)) {
            detach(chart, boxId);
            chart.findStep(boxId).ifPresent(chart::removeStep);
            return;
        }

        detach(chart, boxId);
        chart.findStep(boxId).ifPresent(chart::removeStep);
        Set<String> bridges = new LinkedHashSet<>();
        for (String from : before) {
            for (String to : after) {
                if (!from.equals(to)) {
                    bridges.add(addBridge(chart, from, to));
                }
            }
        }
        smoothBridges(chart, bridges, earlierBars);
        dropSkipBars(chart);
    }

    /**
     * Drops the bars that only skip over steps once the step they skipped is gone. They leave a
     * bar pointing at a bar, which is a flow no later edit can branch from.
     */
    private static void dropSkipBars(S88ProcedureLogic chart) {
        boolean changed;
        do {
            changed = false;
            ChartGraph graph = ChartGraph.of(chart);
            for (String transition : graph.transitions()) {
                if (graph.succs(transition).isEmpty()
                        || !graph.succs(transition).stream().allMatch(graph::isTransition)
                        || graph.preds(transition).stream().noneMatch(graph::isStep)) {
                    continue;
                }
                detach(chart, transition);
                chart.findTransition(transition).ifPresent(chart::removeTransition);
                changed = true;
                break;
            }
        } while (changed);
        ChartNormalizer.relink(chart);
    }

    /**
     * A step that is all its branch has: the bars on either side are shared with the other
     * branches, so the step is taken out of them and nothing has to be joined across.
     */
    private static boolean isWholeBranch(S88ProcedureLogic chart, String boxId,
                                         List<String> before, List<String> after) {
        S88ProcedureLink arriving = onlyLink(chart, boxId, true);
        S88ProcedureLink leaving = onlyLink(chart, boxId, false);
        if (arriving != null && leaving != null
                && isBranchOpening(arriving) && isBranchClosing(leaving)) {
            return true;
        }
        if (before.size() != 1 || after.size() != 1) {
            return false;
        }
        String first = before.getFirst();
        String last = after.getFirst();
        if (first.equals(last)) {
            return false;
        }
        S88ProcedureLink opening = onlyLink(chart, first, true);
        S88ProcedureLink closing = onlyLink(chart, last, false);
        return opening != null && closing != null
                && isBranchOpening(opening) && isBranchClosing(closing)
                && onlyTransitionOut(chart, first).equals(boxId)
                && onlyTransitionIn(chart, last).equals(boxId);
    }

    private static boolean isBranchOpening(S88ProcedureLink link) {
        return link != null && link.isDivergent() && link.getFrom().size() == 1
                && link.getTo().size() > 1;
    }

    private static boolean isBranchClosing(S88ProcedureLink link) {
        return link != null && link.isConvergent() && link.getFrom().size() > 1
                && link.getTo().size() == 1;
    }

    private static String onlyTransitionOut(S88ProcedureLogic chart, String id) {
        List<String> targets = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> id.equals(end.getValue()))) {
                for (S88IdRef end : link.getTo()) {
                    if (chart.findTransition(end.getValue()).isPresent()) {
                        targets.add(end.getValue());
                    } else if (chart.findStep(end.getValue()).isPresent()) {
                        targets.add(end.getValue());
                    }
                }
            }
        }
        return targets.size() == 1 ? targets.getFirst() : "";
    }

    private static String onlyTransitionIn(S88ProcedureLogic chart, String id) {
        List<String> sources = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getTo().stream().anyMatch(end -> id.equals(end.getValue()))) {
                for (S88IdRef end : link.getFrom()) {
                    if (chart.findTransition(end.getValue()).isPresent()
                            || chart.findStep(end.getValue()).isPresent()) {
                        sources.add(end.getValue());
                    }
                }
            }
        }
        return sources.size() == 1 ? sources.getFirst() : "";
    }

    /** A bar that stands for a branch rather than for the run inside one. */
    private static Set<String> branchBars(S88ProcedureLogic chart, List<String> transitions) {
        Set<String> found = new LinkedHashSet<>();
        for (String title : transitions) {
            List<S88ProcedureLink> arriving = new ArrayList<>();
            for (S88ProcedureLink link : chart.getLinks()) {
                if (link.getTo().stream().anyMatch(end -> title.equals(end.getValue()))) {
                    arriving.add(link);
                }
            }
            if (arriving.size() > 1) {
                found.add(title);
            } else if (arriving.size() == 1
                    && (arriving.getFirst().getTo().size() > 1
                    || arriving.getFirst().getFrom().size() > 1)) {
                found.add(title);
            }
        }
        return found;
    }

    private static String addBridge(S88ProcedureLogic chart, String from, String to) {
        S88ProcedureLink line = new S88ProcedureLink(freeLineName(chart, from, to));
        line.addFrom(S88IdRef.transition(from));
        line.addTo(S88IdRef.transition(to));
        chart.addLink(line);
        return line.getId();
    }

    /**
     * Joins each pair of bars left facing each other by the step. Two bars in a row become one;
     * a bar that only fed the step is dropped, and so is one that only took from it.
     */
    private static void smoothBridges(S88ProcedureLogic chart, Set<String> bridges,
                                      Set<String> earlierBars) {
        for (int pass = 0; pass < MAX_SMOOTHING_PASSES; pass++) {
            ChartGraph graph = ChartGraph.of(chart);
            boolean changed = false;
            for (String bridgeId : List.copyOf(bridges)) {
                S88ProcedureLink bridge = chart.findLink(bridgeId).orElse(null);
                if (bridge == null || bridge.getFrom().size() != 1
                        || bridge.getTo().size() != 1) {
                    continue;
                }
                String from = bridge.getFrom().getFirst().getValue();
                String to = bridge.getTo().getFirst().getValue();
                if (!graph.isTransition(from) || !graph.isTransition(to) || from.equals(to)) {
                    continue;
                }
                List<String> succs = graph.succs(from);
                List<String> preds = graph.preds(to);
                boolean onlySucc = succs.size() == 1 && succs.contains(to);
                boolean onlyPred = preds.size() == 1 && preds.contains(from);
                if (onlySucc && onlyPred) {
                    if (earlierBars.contains(from)) {
                        mergeLaterIntoEarlier(chart, from, to);
                    } else {
                        mergeEarlierIntoLater(chart, from, to);
                    }
                    changed = true;
                    break;
                }
                if (onlySucc) {
                    if (earlierBars.contains(from)) {
                        mergeLaterIntoEarlier(chart, from, to);
                    } else {
                        mergeEarlierIntoLater(chart, from, to);
                    }
                    changed = true;
                    break;
                }
                if (onlyPred) {
                    mergeLaterIntoEarlier(chart, from, to);
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                combineBranches(chart);
                return;
            }
        }
        combineBranches(chart);
    }

    /**
     * Puts lines that leave one node for the same kind of node back into one line, which is what a
     * split or a join is. Deleting a step can leave the sides of a block on separate lines, and
     * read apart they would let a bar fire once for each side instead of once for the block.
     */
    private static void combineBranches(S88ProcedureLogic chart) {
        boolean changed;
        do {
            changed = combineByOneEnd(chart, true);
            changed |= combineByOneEnd(chart, false);
        } while (changed);
        ChartNormalizer.relink(chart);
    }

    private static boolean combineByOneEnd(S88ProcedureLogic chart, boolean byFrom) {
        Map<String, List<S88ProcedureLink>> buckets = new LinkedHashMap<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            List<S88IdRef> fixed = byFrom ? link.getFrom() : link.getTo();
            List<S88IdRef> moving = byFrom ? link.getTo() : link.getFrom();
            if (fixed.size() != 1 || moving.isEmpty()) {
                continue;
            }
            String kind = moving.stream()
                    .map(end -> String.valueOf(end.getType()))
                    .distinct()
                    .count() == 1 ? moving.getFirst().getType().name() : null;
            if (kind == null) {
                continue;
            }
            String key = (byFrom ? "F:" : "T:") + fixed.getFirst().getValue() + ":" + kind;
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(link);
        }
        boolean changed = false;
        for (Map.Entry<String, List<S88ProcedureLink>> bucket : buckets.entrySet()) {
            List<S88ProcedureLink> links = bucket.getValue();
            if (links.size() < 2) {
                continue;
            }
            S88ProcedureLink kept = links.getFirst();
            List<S88IdRef> from = new ArrayList<>(kept.getFrom());
            List<S88IdRef> to = new ArrayList<>(kept.getTo());
            for (S88ProcedureLink extra : links.subList(1, links.size())) {
                for (S88IdRef end : extra.getFrom()) {
                    if (!from.contains(end)) {
                        from.add(end);
                    }
                }
                for (S88IdRef end : extra.getTo()) {
                    if (!to.contains(end)) {
                        to.add(end);
                    }
                }
                chart.removeLink(extra);
                changed = true;
            }
            chart.removeLink(kept);
            chart.addLink(rebuilt(kept.getId(), from, to, kept.getLinkType(), kept));
        }
        return changed;
    }

    private static void mergeLaterIntoEarlier(S88ProcedureLogic chart, String earlier, String later) {
        if (!chart.findTransition(earlier).isPresent()
                || !chart.findTransition(later).isPresent()) {
            return;
        }
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            boolean fromEarlier = names(link.getFrom()).contains(earlier);
            boolean toLater = names(link.getTo()).contains(later);
            boolean fromLater = names(link.getFrom()).contains(later);
            boolean toEarlier = names(link.getTo()).contains(earlier);
            if (fromEarlier && toLater) {
                chart.removeLink(link);
                continue;
            }
            if (fromLater && toEarlier) {
                chart.removeLink(link);
                continue;
            }
            if (fromLater) {
                replaceEnd(chart, link, later, earlier, true);
            } else if (toLater) {
                replaceEnd(chart, link, later, earlier, false);
            }
        }
        chart.findTransition(later).ifPresent(chart::removeTransition);
    }

    private static void mergeEarlierIntoLater(S88ProcedureLogic chart, String earlier, String later) {
        if (!chart.findTransition(earlier).isPresent()
                || !chart.findTransition(later).isPresent()) {
            return;
        }
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            boolean fromEarlier = names(link.getFrom()).contains(earlier);
            boolean toLater = names(link.getTo()).contains(later);
            boolean fromLater = names(link.getFrom()).contains(later);
            boolean toEarlier = names(link.getTo()).contains(earlier);
            if (fromEarlier && toLater) {
                chart.removeLink(link);
                continue;
            }
            if (fromLater && toEarlier) {
                chart.removeLink(link);
                continue;
            }
            if (fromEarlier) {
                replaceEnd(chart, link, earlier, later, true);
            } else if (toEarlier) {
                replaceEnd(chart, link, earlier, later, false);
            }
        }
        chart.findTransition(earlier).ifPresent(chart::removeTransition);
    }

    private static void replaceEnd(S88ProcedureLogic chart, S88ProcedureLink link, String was,
                                   String now, boolean from) {
        List<S88IdRef> newFrom = new ArrayList<>();
        List<S88IdRef> newTo = new ArrayList<>();
        for (S88IdRef end : link.getFrom()) {
            newFrom.add(from && was.equals(end.getValue()) ? S88IdRef.transition(now) : end);
        }
        for (S88IdRef end : link.getTo()) {
            newTo.add(!from && was.equals(end.getValue()) ? S88IdRef.transition(now) : end);
        }
        chart.removeLink(link);
        chart.addLink(rebuilt(link.getId(), newFrom, newTo, link.getLinkType(), link));
    }

    /**
     * Takes off a whole branch that starts at the given bar and ends where the flow comes back
     * together.
     */
    private static void removeBranch(S88ProcedureLogic chart, S88ProcedureLink opening,
                                     String firstBar) {
        List<String> branch = new ArrayList<>();
        String node = firstBar;
        while (node != null) {
            branch.add(node);
            S88ProcedureLink leaving = onlyLink(chart, node, false);
            if (leaving == null) {
                throw new IllegalStateException("Branch '" + firstBar + "' does not lead back to the"
                        + " rest of the chart, so there is nothing this edit can take off."
                        + " Nothing has been changed.");
            }
            if (isBranchClosing(leaving)) {
                break;
            }
            if (leaving.getTo().size() != 1) {
                throw new IllegalStateException("Branch '" + firstBar + "' splits again before it"
                        + " comes back together, so it cannot be taken off as one branch."
                        + " Nothing has been changed.");
            }
            node = leaving.getTo().getFirst().getValue();
        }
        if (opening.getTo().size() <= 1) {
            throw new IllegalStateException("Branch '" + firstBar + "' is the only branch, so"
                    + " taking it off would leave nothing to take. Nothing has been changed.");
        }
        for (String taken : branch) {
            detach(chart, taken);
            chart.findStep(taken).ifPresent(chart::removeStep);
            chart.findTransition(taken).ifPresent(chart::removeTransition);
        }
        ChartNormalizer.relink(chart);
    }

    private static S88ProcedureLink branchOpening(S88ProcedureLogic chart, String barId) {
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.isDivergent() && link.getTo().size() > 1
                    && link.getTo().stream().anyMatch(end -> barId.equals(end.getValue()))) {
                return link;
            }
        }
        return null;
    }

    /**
     * Takes away what is left with nothing to do, and names the steps it took so the recipe lets go
     * of them too.
     */
    private static List<String> sweepUpWhatNothingReaches(S88ProcedureLogic chart) {
        List<String> taken = new ArrayList<>();
        int ceiling = chart.getSteps().size() + chart.getTransitions().size() + 1;
        for (int pass = 0; pass < ceiling; pass++) {
            ChartGraph graph = ChartGraph.of(chart);
            if (!graph.hasNode(CreateMasterRecipeUseCase.BEGIN_ID)
                    || !graph.hasNode(CreateMasterRecipeUseCase.END_ID)) {
                return taken;
            }
            Set<String> reachable = graph.reachableFrom(CreateMasterRecipeUseCase.BEGIN_ID);
            Set<String> reachingStop = graph.reaching(CreateMasterRecipeUseCase.END_ID);
            List<String> unreached = new ArrayList<>();
            for (String node : graph.nodes()) {
                if (node.equals(CreateMasterRecipeUseCase.BEGIN_ID)
                        || node.equals(CreateMasterRecipeUseCase.END_ID)) {
                    continue;
                }
                if (!reachable.contains(node) || !reachingStop.contains(node)) {
                    unreached.add(node);
                }
            }
            if (unreached.isEmpty()) {
                return taken;
            }
            for (String node : unreached) {
                chart.findStep(node).ifPresent(box -> taken.add(box.getRecipeElementId()));
                detach(chart, node);
                chart.findStep(node).ifPresent(chart::removeStep);
                chart.findTransition(node).ifPresent(chart::removeTransition);
            }
        }
        return taken;
    }

    private static void refuseNewProblems(String action, List<String> before,
                                          S88ProcedureLogic after) {
        List<String> introduced = new ArrayList<>(ChartSoundness.check(after));
        introduced.removeAll(before);
        if (!introduced.isEmpty()) {
            throw new IllegalStateException(action + " would leave it in a state that cannot be"
                    + " drawn: " + String.join(", and ", introduced)
                    + ". Nothing has been changed.");
        }
    }

    private static void refuseIfEnd(S88Recipe recipe, S88ProcedureStep box) {
        S88RecipeElement element = recipe.findElement(box.getRecipeElementId()).orElse(null);
        if (element != null && (element.getKind() == S88RecipeElementKind.BEGIN
                || element.getKind() == S88RecipeElementKind.END)) {
            throw new IllegalStateException("Step '" + box.getId() + "' is where the flow "
                    + (element.getKind() == S88RecipeElementKind.BEGIN ? "starts" : "stops")
                    + ", and a recipe has to have both.");
        }
    }

    private static Set<String> drawnElementIds(S88ProcedureLogic chart) {
        Set<String> ids = new LinkedHashSet<>();
        chart.getSteps().forEach(box -> ids.add(box.getRecipeElementId()));
        return ids;
    }

    /** Puts the chart that was worked out in place of the one the recipe is holding. */
    private static void copyOnto(S88ProcedureLogic from, S88ProcedureLogic to) {
        new ArrayList<>(to.getLinks()).forEach(to::removeLink);
        new ArrayList<>(to.getSteps()).forEach(to::removeStep);
        new ArrayList<>(to.getTransitions()).forEach(to::removeTransition);
        from.getSteps().forEach(to::addStep);
        from.getTransitions().forEach(to::addTransition);
        from.getLinks().forEach(to::addLink);
    }

    /** Takes one node off the chart, and takes away the lines that are left with nothing on one side. */
    private static void detach(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink line : new ArrayList<>(chart.getLinks())) {
            if (!names(line.getFrom()).contains(id) && !names(line.getTo()).contains(id)) {
                continue;
            }
            chart.removeLink(line);
            List<S88IdRef> from = new ArrayList<>();
            List<S88IdRef> to = new ArrayList<>();
            line.getFrom().stream().filter(end -> !id.equals(end.getValue())).forEach(from::add);
            line.getTo().stream().filter(end -> !id.equals(end.getValue())).forEach(to::add);
            if (!from.isEmpty() && !to.isEmpty()) {
                chart.addLink(rebuilt(line.getId(), from, to, line.getLinkType(), line));
            }
        }
    }

    private static String freeLineName(S88ProcedureLogic chart, String from, String to) {
        String candidate = from + "_TO_" + to;
        int more = 1;
        while (chart.findLink(candidate).isPresent()) {
            candidate = from + "_TO_" + to + "_" + more++;
        }
        return candidate;
    }

    private static S88ProcedureLink rebuilt(String id, List<S88IdRef> from, List<S88IdRef> to,
                                            S88LinkType type, S88ProcedureLink original) {
        S88ProcedureLink line = new S88ProcedureLink(id);
        from.forEach(line::addFrom);
        to.forEach(line::addTo);
        line.setLinkType(type);
        line.setDepiction(original.getDepiction());
        line.setEvaluationOrder(original.getEvaluationOrder());
        original.getDescriptions().forEach(line::addDescription);
        return line;
    }

    private static S88ProcedureLink onlyLink(S88ProcedureLogic chart, String id, boolean arriving) {
        S88ProcedureLink found = null;
        for (S88ProcedureLink line : chart.getLinks()) {
            boolean touches = arriving
                    ? line.getTo().stream().anyMatch(end -> id.equals(end.getValue()))
                    : line.getFrom().stream().anyMatch(end -> id.equals(end.getValue()));
            if (!touches) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = line;
        }
        return found;
    }

    private static List<String> transitionsAmong(List<String> nodes, ChartGraph graph) {
        List<String> found = new ArrayList<>();
        for (String node : nodes) {
            if (graph.isTransition(node) && !found.contains(node)) {
                found.add(node);
            }
        }
        return found;
    }

    private static List<String> names(List<S88IdRef> ends) {
        List<String> found = new ArrayList<>();
        for (S88IdRef end : ends) {
            found.add(end.getValue());
        }
        return found;
    }
}
