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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;

/**
 * Joins and separates nodes of a chart without assuming one line between each pair.
 *
 * <p>An edit is worked out on a copy. The recipe is touched only when the result keeps every node
 * reachable and reaches the stop.
 */
public final class ChartEditOps {

    private ChartEditOps() {
        /* This utility class should not be instantiated */
    }

    /**
     * Joins two nodes with a line.
     *
     * <p>Two steps cannot share a line. Two transitions share a line only as a skip. The new line
     * joins an existing line at the same side when that keeps the shape of the branch.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being edited, {@code null} for the chart of the recipe
     * @param fromId node the line leaves
     * @param toId   node the line reaches
     * @return the line that now joins the two nodes
     * @throws IllegalArgumentException when there is no chart, no node, or a line already joins them
     * @throws IllegalStateException    when the line would leave the chart unable to run
     */
    public static S88ProcedureLink connect(S88Recipe recipe, S88RecipeElement step,
                                           String fromId, String toId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        requireNode(chart, fromId);
        requireNode(chart, toId);
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("A line cannot leave '" + fromId + "' and reach the"
                    + " same node.");
        }
        if (chart.findStep(fromId).isPresent() && chart.findStep(toId).isPresent()) {
            throw new IllegalArgumentException("A line cannot run from step '" + fromId + "' to step '"
                    + toId + "'.");
        }
        for (S88ProcedureLink link : chart.getLinks()) {
            if (contains(link.getFrom(), fromId) && contains(link.getTo(), toId)) {
                throw new IllegalArgumentException("A line already joins '" + fromId + "' to '"
                        + toId + "'.");
            }
        }

        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        joined(edited, fromId, toId);
        ChartNormalizer.relink(edited);
        refuseNewProblems(chart, edited, "Joining '" + fromId + "' to '" + toId + "'");
        copyLinksOnto(edited, chart);
        announce(recipe);
        return linkBetween(chart, fromId, toId);
    }

    /**
     * Takes the line between two nodes away.
     *
     * <p>When the line joins more than one pair, only that pair is taken away. The edit is refused
     * when what is left has a node without a way in or out.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being edited, {@code null} for the chart of the recipe
     * @param fromId node the line leaves
     * @param toId   node the line reaches
     * @throws IllegalArgumentException when there is no chart, no node, or no line between them
     * @throws IllegalStateException    when the chart would no longer run
     */
    public static void disconnect(S88Recipe recipe, S88RecipeElement step,
                                  String fromId, String toId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        requireNode(chart, fromId);
        requireNode(chart, toId);
        if (linkBetween(chart, fromId, toId) == null) {
            throw new IllegalArgumentException("No line joins '" + fromId + "' to '" + toId + "'.");
        }

        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        separated(edited, fromId, toId);
        ChartNormalizer.relink(edited);
        refuseNewProblems(chart, edited, "Separating '" + fromId + "' from '" + toId + "'");
        copyLinksOnto(edited, chart);
        announce(recipe);
    }

    /**
     * Takes away the line that skips from one transition to another, or the empty branch left when
     * an optional branch was flattened.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being edited, {@code null} for the chart of the recipe
     * @param fromId transition the skip leaves, or the empty branch to take off
     * @param toId   transition the skip reaches, or the step the empty branch rejoins at
     * @throws IllegalArgumentException when there is no chart, no node, or no skip between them
     * @throws IllegalStateException    when the chart would no longer run
     */
    public static void removeSkip(S88Recipe recipe, S88RecipeElement step,
                                  String fromId, String toId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        requireNode(chart, fromId);
        requireNode(chart, toId);
        if (toId.equals(StructureQueries.emptyBranchTarget(chart, fromId))) {
            removeEmptyBranch(recipe, chart, fromId);
            return;
        }
        ChartGraph graph = ChartGraph.of(chart);
        if (!graph.isTransition(fromId) || !graph.isTransition(toId)
                || !graph.isSkip(fromId, toId)) {
            throw new IllegalArgumentException("No skip goes from '" + fromId + "' to '" + toId
                    + "'.");
        }

        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        separated(edited, fromId, toId);
        ChartNormalizer.normalize(edited);
        refuseNewProblems(chart, edited, "Taking the skip from '" + fromId + "' to '" + toId
                + "' off");
        copyOnto(edited, chart);
        announce(recipe);
    }

    /** Takes an empty branch off the split and the join that hold it. */
    private static void removeEmptyBranch(S88Recipe recipe, S88ProcedureLogic chart, String barId) {
        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        S88ProcedureLink split = edited.getLinks().stream()
                .filter(link -> link.isDivergent() && contains(link.getTo(), barId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The empty branch '" + barId
                        + "' has no split to come off."));
        S88ProcedureLink join = edited.getLinks().stream()
                .filter(link -> link.isConvergent() && contains(link.getFrom(), barId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The empty branch '" + barId
                        + "' has no join to come off."));

        replace(edited, split, split.getFrom(), without(split.getTo(), barId));
        replace(edited, join, without(join.getFrom(), barId), join.getTo());
        edited.findTransition(barId).ifPresent(edited::removeTransition);
        ChartNormalizer.normalize(edited);
        refuseNewProblems(chart, edited, "Taking the empty branch '" + barId + "' off");
        copyOnto(edited, chart);
        announce(recipe);
    }

    /**
     * Brings the ends of several branches together at a new transition.
     *
     * <p>The branches must end at the given steps. Their old transitions are left behind when they
     * lead nowhere else, and the new transition waits for all the steps.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being edited, {@code null} for the chart of the recipe
     * @param stepIds steps whose branches are brought together, at least two
     * @param barId  name for the transition that waits for the branches
     * @param toStepId step the flow reaches once the branches are together
     * @return the transition that waits for the branches
     * @throws IllegalArgumentException when the chart, a step, the name, or the target is missing,
     *                                  or a step does not end a branch
     * @throws IllegalStateException    when the result would not run
     */
    public static S88ProcedureTransition joinBranches(S88Recipe recipe, S88RecipeElement step,
                                                      List<String> stepIds, String barId,
                                                      String toStepId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        requireBranches(chart, stepIds);
        requireFreeName(chart, barId, "transition");
        if (chart.findStep(toStepId).isEmpty()) {
            throw new IllegalArgumentException("This chart has no step called '" + toStepId + "'.");
        }
        for (String stepId : stepIds) {
            outgoingFrom(chart, stepId);
        }

        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        for (String stepId : stepIds) {
            takeOutgoing(edited, stepId);
        }
        S88ProcedureTransition joined = new S88ProcedureTransition(barId, null);
        edited.addTransition(joined);
        S88ProcedureLink intoBar = new S88ProcedureLink(freeLineName(edited, barId, "STEP"));
        stepIds.forEach(stepId -> intoBar.addFrom(S88IdRef.step(stepId)));
        intoBar.addTo(S88IdRef.transition(barId));
        intoBar.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        edited.addLink(intoBar);
        S88ProcedureLink onwards = new S88ProcedureLink(freeLineName(edited, barId, toStepId));
        onwards.addFrom(S88IdRef.transition(barId));
        onwards.addTo(S88IdRef.step(toStepId));
        onwards.setLinkType(S88LinkType.CONTROL_LINK);
        edited.addLink(onwards);
        ChartNormalizer.normalize(edited);
        refuseNewProblems(chart, edited, "Joining the branches at '" + barId + "'");
        copyOnto(edited, chart);
        announce(recipe);
        return chart.findTransition(barId).orElseThrow();
    }

    /**
     * Sends the flow from a step through a new transition and back into the same step.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being edited, {@code null} for the chart of the recipe
     * @param stepId step the loop leaves and rejoins
     * @param barId  name for the transition that waits in the loop
     * @return the transition of the loop
     * @throws IllegalArgumentException when the chart, the step, or the name is missing, the step is
     *                                  the stop or already has a loop, or the name is taken
     * @throws IllegalStateException    when the step is inside a parallel block or the result would
     *                                  not run
     */
    public static S88ProcedureTransition loopStep(S88Recipe recipe, S88RecipeElement step,
                                                  String stepId, String barId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart.findStep(stepId).isEmpty()) {
            throw new IllegalArgumentException("This chart has no step called '" + stepId + "'.");
        }
        if (CreateMasterRecipeUseCase.END_ID.equals(stepId)) {
            throw new IllegalArgumentException("The stop step cannot be looped.");
        }
        if (StructureQueries.isInsideParallel(chart, stepId)) {
            throw new IllegalStateException("Step '" + stepId + "' is inside a parallel block,"
                    + " where a loop is not allowed.");
        }
        if (selfLoopAt(chart, stepId) != null) {
            throw new IllegalArgumentException("Step '" + stepId + "' already has a loop.");
        }
        requireFreeName(chart, barId, "transition");

        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        edited.addTransition(new S88ProcedureTransition(barId, null));
        S88ProcedureLink out = new S88ProcedureLink(freeLineName(edited, stepId, barId));
        out.addFrom(S88IdRef.step(stepId));
        out.addTo(S88IdRef.transition(barId));
        out.setLinkType(S88LinkType.CONTROL_LINK);
        edited.addLink(out);
        S88ProcedureLink back = new S88ProcedureLink(freeLineName(edited, barId, stepId));
        back.addFrom(S88IdRef.transition(barId));
        back.addTo(S88IdRef.step(stepId));
        back.setLinkType(S88LinkType.CONTROL_LINK);
        edited.addLink(back);
        ChartNormalizer.normalize(edited);
        refuseNewProblems(chart, edited, "Looping '" + stepId + "'");
        copyOnto(edited, chart);
        announce(recipe);
        return chart.findTransition(barId).orElseThrow();
    }

    /**
     * The transition of the loop that leaves and rejoins a step, or {@code null} when there is none.
     */
    static S88ProcedureTransition selfLoopAt(S88ProcedureLogic chart, String stepId) {
        ChartGraph graph = ChartGraph.of(chart);
        for (S88ProcedureTransition transition : chart.getTransitions()) {
            List<String> before = graph.preds(transition.getId());
            List<String> after = graph.succs(transition.getId());
            if (before.size() == 1 && after.size() == 1
                    && before.getFirst().equals(stepId)
                    && after.getFirst().equals(stepId)) {
                return transition;
            }
        }
        return null;
    }

    /**
     * Sends the flow from one transition into several steps at once.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being edited, {@code null} for the chart of the recipe
     * @param fromId transition the flow leaves
     * @param toStepIds steps the flow reaches, at least two
     * @return the line that now leaves the transition
     * @throws IllegalArgumentException when the chart, the transition, or a step is missing
     * @throws IllegalStateException    when the transition already has more than one exit or the
     *                                  result would not run
     */
    public static S88ProcedureLink splitAfter(S88Recipe recipe, S88RecipeElement step,
                                              String fromId, List<String> toStepIds) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart.findTransition(fromId).isEmpty()) {
            throw new IllegalArgumentException("This chart has no transition called '" + fromId
                    + "'.");
        }
        Set<String> unique = new LinkedHashSet<>(toStepIds);
        if (unique.size() < 2) {
            throw new IllegalArgumentException("A split needs at least two different steps.");
        }
        for (String stepId : unique) {
            if (chart.findStep(stepId).isEmpty()) {
                throw new IllegalArgumentException("This chart has no step called '" + stepId + "'.");
            }
            for (S88ProcedureLink line : chart.getLinks()) {
                if (contains(line.getTo(), stepId) && !contains(line.getFrom(), fromId)) {
                    throw new IllegalArgumentException("Step '" + stepId + "' already has another"
                            + " way in.");
                }
            }
        }
        List<S88ProcedureLink> exits = outgoingFrom(chart, fromId);
        if (exits.size() > 1) {
            throw new IllegalStateException("Transition '" + fromId + "' already has more than one"
                    + " exit.");
        }
        for (S88ProcedureLink exit : exits) {
            for (S88IdRef end : exit.getTo()) {
                if (!unique.contains(end.getValue())) {
                    throw new IllegalStateException("Transition '" + fromId + "' already leads to '"
                            + end.getValue() + "'.");
                }
            }
        }

        S88ProcedureLogic edited = RecipeDeepCopy.copyChart(chart);
        for (S88ProcedureLink exit : new ArrayList<>(edited.getLinks())) {
            if (contains(exit.getFrom(), fromId)) {
                edited.removeLink(exit);
            }
        }
        S88ProcedureLink split = new S88ProcedureLink(freeLineName(edited, fromId, "SPLIT"));
        split.addFrom(S88IdRef.transition(fromId));
        unique.forEach(stepId -> split.addTo(S88IdRef.step(stepId)));
        split.setLinkType(S88LinkType.PARALLEL_DIVERGENT);
        edited.addLink(split);
        ChartNormalizer.normalize(edited);
        refuseNewProblems(chart, edited, "Splitting after '" + fromId + "'");
        copyLinksOnto(edited, chart);
        announce(recipe);
        return linkBetween(chart, fromId, unique.iterator().next());
    }

    private static void requireBranches(S88ProcedureLogic chart, List<String> stepIds) {
        Set<String> unique = new LinkedHashSet<>(stepIds);
        if (unique.size() < 2) {
            throw new IllegalArgumentException("A join needs at least two different steps.");
        }
        for (String stepId : unique) {
            if (chart.findStep(stepId).isEmpty()) {
                throw new IllegalArgumentException("This chart has no step called '" + stepId + "'.");
            }
        }
    }

    private static void requireFreeName(S88ProcedureLogic chart, String id, String kind) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("The " + kind + " needs a name.");
        }
        if (chart.findStep(id).isPresent() || chart.findTransition(id).isPresent()) {
            throw new IllegalArgumentException("This chart already has a node called '" + id + "'.");
        }
    }

    private static List<S88ProcedureLink> outgoingFrom(S88ProcedureLogic chart, String id) {
        List<S88ProcedureLink> found = new ArrayList<>();
        for (S88ProcedureLink line : chart.getLinks()) {
            if (contains(line.getFrom(), id)) {
                found.add(line);
            }
        }
        return found;
    }

    private static void takeOutgoing(S88ProcedureLogic chart, String stepId) {
        for (S88ProcedureLink line : outgoingFrom(chart, stepId)) {
            List<S88IdRef> froms = without(line.getFrom(), stepId);
            chart.removeLink(line);
            if (!froms.isEmpty()) {
                chart.addLink(rebuilt(line, froms, line.getTo()));
            }
        }
    }

    private static S88ProcedureLogic chartOf(S88Recipe recipe, S88RecipeElement step) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to edit.");
        }
        return chart;
    }

    private static void requireNode(S88ProcedureLogic chart, String id) {
        if (id == null || (chart.findStep(id).isEmpty() && chart.findTransition(id).isEmpty())) {
            throw new IllegalArgumentException("This chart has no node called '" + id + "'.");
        }
    }

    private static void joined(S88ProcedureLogic chart, String fromId, String toId) {
        boolean fromStep = chart.findStep(fromId).isPresent();
        boolean toStep = chart.findStep(toId).isPresent();
        if (fromStep && !toStep) {
            S88ProcedureLink line = lineInto(chart, toId);
            if (line != null) {
                widenFrom(chart, line, fromId);
                return;
            }
        }
        if (!fromStep && toStep) {
            S88ProcedureLink line = lineOutOf(chart, fromId);
            if (line != null) {
                widenTo(chart, line, toId);
                return;
            }
        }
        S88ProcedureLink line = new S88ProcedureLink(freeLineName(chart, fromId, toId));
        line.addFrom(fromStep ? S88IdRef.step(fromId) : S88IdRef.transition(fromId));
        line.addTo(toStep ? S88IdRef.step(toId) : S88IdRef.transition(toId));
        chart.addLink(line);
    }

    private static S88ProcedureLink lineInto(S88ProcedureLogic chart, String transitionId) {
        for (S88ProcedureLink line : chart.getLinks()) {
            if (!contains(line.getTo(), transitionId)) {
                continue;
            }
            if (line.getFrom().stream().allMatch(end -> chart.findStep(end.getValue()).isPresent())) {
                return line;
            }
        }
        return null;
    }

    private static S88ProcedureLink lineOutOf(S88ProcedureLogic chart, String transitionId) {
        for (S88ProcedureLink line : chart.getLinks()) {
            if (!contains(line.getFrom(), transitionId)) {
                continue;
            }
            if (line.getTo().stream().allMatch(end -> chart.findStep(end.getValue()).isPresent())) {
                return line;
            }
        }
        return null;
    }

    private static void widenFrom(S88ProcedureLogic chart, S88ProcedureLink line, String fromId) {
        List<S88IdRef> froms = new ArrayList<>(line.getFrom());
        froms.add(S88IdRef.step(fromId));
        replace(chart, line, froms, line.getTo());
    }

    private static void widenTo(S88ProcedureLogic chart, S88ProcedureLink line, String toId) {
        List<S88IdRef> tos = new ArrayList<>(line.getTo());
        tos.add(S88IdRef.step(toId));
        replace(chart, line, line.getFrom(), tos);
    }

    private static void separated(S88ProcedureLogic chart, String fromId, String toId) {
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            if (!contains(link.getFrom(), fromId) || !contains(link.getTo(), toId)) {
                continue;
            }
            List<S88IdRef> froms = without(link.getFrom(), fromId);
            List<S88IdRef> tos = without(link.getTo(), toId);
            chart.removeLink(link);
            if (!froms.isEmpty() && !tos.isEmpty()) {
                chart.addLink(rebuilt(link, froms, tos));
            }
        }
    }

    private static List<S88IdRef> without(List<S88IdRef> ends, String id) {
        List<S88IdRef> kept = new ArrayList<>();
        for (S88IdRef end : ends) {
            if (!id.equals(end.getValue())) {
                kept.add(end);
            }
        }
        return kept;
    }

    private static void replace(S88ProcedureLogic chart, S88ProcedureLink line,
                                List<S88IdRef> froms, List<S88IdRef> tos) {
        chart.removeLink(line);
        chart.addLink(rebuilt(line, froms, tos));
    }

    private static S88ProcedureLink rebuilt(S88ProcedureLink line, List<S88IdRef> froms,
                                            List<S88IdRef> tos) {
        S88ProcedureLink rebuilt = new S88ProcedureLink(line.getId());
        froms.forEach(rebuilt::addFrom);
        tos.forEach(rebuilt::addTo);
        rebuilt.setLinkType(line.getLinkType());
        rebuilt.setDepiction(line.getDepiction());
        rebuilt.setEvaluationOrder(line.getEvaluationOrder());
        line.getDescriptions().forEach(rebuilt::addDescription);
        return rebuilt;
    }

    private static boolean contains(List<S88IdRef> ends, String id) {
        return ends.stream().anyMatch(end -> id.equals(end.getValue()));
    }

    private static S88ProcedureLink linkBetween(S88ProcedureLogic chart, String fromId,
                                                String toId) {
        for (S88ProcedureLink link : chart.getLinks()) {
            if (contains(link.getFrom(), fromId) && contains(link.getTo(), toId)) {
                return link;
            }
        }
        return null;
    }

    private static String freeLineName(S88ProcedureLogic chart, String fromId, String toId) {
        String stem = fromId + "_TO_" + toId;
        String candidate = stem;
        int more = 1;
        while (chart.findLink(candidate).isPresent()) {
            candidate = stem + "_" + more++;
        }
        return candidate;
    }

    private static void refuseNewProblems(S88ProcedureLogic before, S88ProcedureLogic after,
                                          String action) {
        List<String> introduced = new ArrayList<>(ChartSoundness.check(after));
        introduced.removeAll(ChartSoundness.check(before));
        if (!introduced.isEmpty()) {
            throw new IllegalStateException(action + " would leave it in a state that cannot be"
                    + " drawn: " + String.join(", and ", introduced)
                    + ". Nothing has been changed.");
        }
    }

    private static void copyLinksOnto(S88ProcedureLogic from, S88ProcedureLogic to) {
        new ArrayList<>(to.getLinks()).forEach(to::removeLink);
        from.getLinks().forEach(to::addLink);
    }

    private static void copyOnto(S88ProcedureLogic from, S88ProcedureLogic to) {
        new ArrayList<>(to.getLinks()).forEach(to::removeLink);
        new ArrayList<>(to.getSteps()).forEach(to::removeStep);
        new ArrayList<>(to.getTransitions()).forEach(to::removeTransition);
        from.getSteps().forEach(to::addStep);
        from.getTransitions().forEach(to::addTransition);
        from.getLinks().forEach(to::addLink);
    }

    private static void announce(S88Recipe recipe) {
        if (recipe != null) {
            recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
        }
    }
}
