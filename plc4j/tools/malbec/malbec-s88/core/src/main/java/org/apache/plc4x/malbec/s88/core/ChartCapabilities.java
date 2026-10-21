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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;

/**
 * What the chart orders can do with the current pick.
 *
 * <p>Every order runs on a copy first. The answer says whether the real order would go through and,
 * when it would not, gives the reason to show beside it. Enabling an order and running it therefore
 * use the same code.
 */
public final class ChartCapabilities {

    private ChartCapabilities() {
        /* This utility class should not be instantiated */
    }

    /** An order that changes the chart. */
    public enum ChartOp {
        DELETE,
        REMOVE_BRANCH,
        REMOVE_SKIP,
        MOVE_BRANCH_LEFT,
        MOVE_BRANCH_RIGHT,
        JOIN_BRANCHES,
        INSERT_BEFORE,
        INSERT_AFTER,
        INSERT_IN_BRANCH,
        INSERT_BEFORE_FORK,
        INSERT_AFTER_JOIN
    }

    /**
     * Whether an order can run, and why not when it cannot.
     *
     * @param enabled whether the order would go through
     * @param reason  what stops it, empty when it can run
     */
    public record Capability(boolean enabled, String reason) {

        public Capability {
            reason = reason == null ? "" : reason;
        }
    }

    /**
     * Tries an order on a copy of the recipe.
     *
     * @param recipe    recipe being edited, may be {@code null}
     * @param step      step whose chart is being edited, {@code null} for the recipe's chart
     * @param op        order to try
     * @param selection nodes or lines the operator picked
     * @return whether the order would run, and why not when it would not
     */
    public static Capability can(S88Recipe recipe, S88RecipeElement step, ChartOp op,
                                 Set<String> selection) {
        if (recipe == null) {
            return new Capability(false, "There is no recipe to edit.");
        }
        if (op == null) {
            return new Capability(false, "There is no order to run.");
        }
        try {
            S88Recipe trial = RecipeDeepCopy.copyRecipe(recipe);
            S88RecipeElement trialStep = null;
            if (step != null) {
                trialStep = trial.findElement(step.getId()).orElse(null);
                if (trialStep == null) {
                    return new Capability(false, "The step being edited is not in this recipe.");
                }
            }
            perform(trial, trialStep, op, selection == null ? Set.of() : selection);
            return new Capability(true, "");
        } catch (RuntimeException refusal) {
            return new Capability(false, reasonOf(refusal));
        }
    }

    /**
     * Runs an order on the recipe.
     *
     * @param recipe    recipe being edited, may be {@code null} when no change event is wanted
     * @param step      step whose chart is being edited, {@code null} for the recipe's chart
     * @param op        order to run
     * @param selection nodes or lines the operator picked
     * @return the node or bar the order made, {@code null} when it made none
     * @throws IllegalArgumentException when the chart or the pick is missing
     * @throws IllegalStateException    when the order would leave a chart that cannot run
     */
    public static String perform(S88Recipe recipe, S88RecipeElement step, ChartOp op,
                                 Set<String> selection) {
        if (op == null) {
            throw new IllegalArgumentException("There is no order to run.");
        }
        S88ProcedureLogic chart = EditProcedureLogicUseCase.existingChart(recipe, step);
        if (chart == null) {
            throw new IllegalArgumentException("There is no chart to edit.");
        }
        Set<String> picked = selection == null ? Set.of() : new LinkedHashSet<>(selection);
        return switch (op) {
            case DELETE -> {
                deletePicked(recipe, step, chart, picked);
                yield null;
            }
            case REMOVE_BRANCH -> {
                ChartBranchingUseCase.removeBranch(recipe, step, oneBox(chart, picked));
                yield null;
            }
            case REMOVE_SKIP -> {
                String[] skip = skipOf(chart, picked);
                ChartEditOps.removeSkip(recipe, step, skip[0], skip[1]);
                yield null;
            }
            case MOVE_BRANCH_LEFT -> {
                ChartBranchingUseCase.moveBranch(recipe, step, oneBox(chart, picked), false);
                yield null;
            }
            case MOVE_BRANCH_RIGHT -> {
                ChartBranchingUseCase.moveBranch(recipe, step, oneBox(chart, picked), true);
                yield null;
            }
            case JOIN_BRANCHES -> {
                List<String> boxes = boxesIn(chart, picked);
                if (boxes.size() < 2) {
                    throw new IllegalArgumentException("Pick two or more steps to join.");
                }
                String bar = EditProcedureLogicUseCase.freeName(chart, "T_JOIN", "transition");
                String target = commonStepAfter(chart, boxes);
                ChartEditOps.joinBranches(recipe, step, boxes, bar, target);
                yield bar;
            }
            case INSERT_BEFORE -> {
                String box = oneBox(chart, picked);
                String[] names = freeStepNames(recipe, chart);
                EditProcedureLogicUseCase.insertStepBefore(recipe, step, box, names[0], names[0],
                        names[1], null);
                yield names[0];
            }
            case INSERT_AFTER -> {
                String box = oneBox(chart, picked);
                yield BindRecipeStepUseCase.addEmptyStepAfter(recipe, step, box).getId();
            }
            case INSERT_IN_BRANCH -> {
                String branch = oneBar(chart, picked);
                String fork = forkOf(chart, branch);
                String[] names = freeStepNames(recipe, chart);
                EditProcedureLogicUseCase.insertStepAfter(recipe, step, fork, names[0], names[0],
                        names[1], null, InsertMode.IN_BRANCH, branch);
                yield names[0];
            }
            case INSERT_BEFORE_FORK -> {
                String box = stepBeforeFork(chart, picked);
                String[] names = freeStepNames(recipe, chart);
                EditProcedureLogicUseCase.insertStepAfter(recipe, step, box, names[0], names[0],
                        names[1], null);
                yield names[0];
            }
            case INSERT_AFTER_JOIN -> {
                String join = joinIn(chart, picked);
                String[] names = freeStepNames(recipe, chart);
                EditProcedureLogicUseCase.insertStepAfterTransition(recipe, step, join, names[0],
                        names[0], names[1], null);
                yield names[0];
            }
        };
    }

    private static String stepBeforeFork(S88ProcedureLogic chart, Set<String> picked) {
        S88ProcedureLink fork = forkIn(chart, picked);
        if (fork.getFrom().size() != 1) {
            throw new IllegalStateException("The fork leaves more than one node, so there is no one"
                    + " place to put a step before it.");
        }
        ChartGraph graph = ChartGraph.of(chart);
        String from = fork.getFrom().getFirst().getValue();
        if (graph.isStep(from)) {
            return from;
        }
        List<String> before = graph.preds(from);
        if (before.size() == 1 && graph.isStep(before.getFirst())) {
            return before.getFirst();
        }
        throw new IllegalStateException("The fork has no single step before it, so there is no one"
                + " place to put a step.");
    }

    private static S88ProcedureLink forkIn(S88ProcedureLogic chart, Set<String> picked) {
        if (picked.size() != 1) {
            throw new IllegalArgumentException("Pick one fork.");
        }
        S88ProcedureLink fork = forkAt(chart, picked.iterator().next());
        if (fork == null) {
            throw new IllegalArgumentException("The picked thing is not a fork.");
        }
        return fork;
    }

    private static S88ProcedureLink forkAt(S88ProcedureLogic chart, String id) {
        S88ProcedureLink line = chart.findLink(id).orElse(null);
        if (line != null && line.isDivergent()) {
            return line;
        }
        ChartGraph graph = ChartGraph.of(chart);
        for (ChartGraph.Junction junction : graph.outJunctions(id)) {
            if (junction.kind().isDivergent()) {
                S88ProcedureLink found = chart.findLink(junction.linkId()).orElse(null);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String joinIn(S88ProcedureLogic chart, Set<String> picked) {
        if (picked.size() != 1) {
            throw new IllegalArgumentException("Pick one join.");
        }
        String id = picked.iterator().next();
        S88ProcedureLink line = chart.findLink(id).orElse(null);
        if (line != null) {
            if (line.isConvergent()) {
                return joinTarget(chart, line);
            }
            throw new IllegalArgumentException("The picked line is not a join.");
        }
        ChartGraph graph = ChartGraph.of(chart);
        if (graph.inJunctions(id).stream()
                .anyMatch(junction -> junction.kind().isConvergent())) {
            return id;
        }
        S88ProcedureLink fork = forkAt(chart, id);
        if (fork != null) {
            return joinAfterFork(chart, fork);
        }
        throw new IllegalArgumentException("The picked thing is not a join, and it is not a fork"
                + " with a join after it.");
    }

    private static String joinTarget(S88ProcedureLogic chart, S88ProcedureLink join) {
        List<String> targets = join.getTo().stream()
                .map(S88IdRef::getValue)
                .filter(id -> chart.findTransition(id).isPresent())
                .distinct()
                .toList();
        if (targets.size() != 1) {
            throw new IllegalStateException("The join does not lead to one transition, so there is"
                    + " no one place to put a step after it.");
        }
        return targets.getFirst();
    }

    private static String joinAfterFork(S88ProcedureLogic chart, S88ProcedureLink fork) {
        ChartGraph graph = ChartGraph.of(chart);
        List<String> branches = fork.getTo().stream()
                .filter(S88IdRef::isInternal)
                .map(S88IdRef::getValue)
                .filter(graph::hasNode)
                .distinct()
                .toList();
        if (branches.size() < 2) {
            throw new IllegalStateException("The fork does not lead to two branches.");
        }
        for (var transition : chart.getTransitions()) {
            String candidate = transition.getId();
            boolean joins = graph.inJunctions(candidate).stream()
                    .anyMatch(junction -> junction.kind().isConvergent());
            if (joins && branches.stream()
                    .allMatch(branch -> graph.reachableFrom(branch).contains(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("The fork has no join after it, so there is no place to put"
                + " a step after its branches.");
    }

    private static void deletePicked(S88Recipe recipe, S88RecipeElement step,
                                     S88ProcedureLogic chart, Set<String> picked) {
        if (picked.isEmpty()) {
            throw new IllegalArgumentException("Pick something to take off the chart.");
        }
        if (picked.size() == 1) {
            String only = picked.iterator().next();
            if (StructureQueries.loopTransitionOf(chart, only) != null) {
                EditProcedureLogicUseCase.removeLoopLink(recipe, step, only);
                return;
            }
        }
        int taken = 0;
        for (S88ProcedureStep box : new ArrayList<>(chart.getSteps())) {
            if (picked.contains(box.getId()) && chart.findStep(box.getId()).isPresent()) {
                ChartDeletionUseCase.deleteBox(recipe, step, box.getId());
                taken++;
            }
        }
        for (var bar : new ArrayList<>(chart.getTransitions())) {
            if (picked.contains(bar.getId()) && chart.findTransition(bar.getId()).isPresent()) {
                ChartDeletionUseCase.deleteTransition(recipe, step, bar.getId());
                taken++;
            }
        }
        for (S88ProcedureLink line : new ArrayList<>(chart.getLinks())) {
            if (picked.contains(line.getId()) && chart.findLink(line.getId()).isPresent()) {
                ChartDeletionUseCase.deleteLink(recipe, step, line.getId());
                taken++;
            }
        }
        if (taken == 0) {
            throw new IllegalArgumentException("Nothing that was picked is on this chart.");
        }
    }

    private static String oneBox(S88ProcedureLogic chart, Set<String> picked) {
        List<String> boxes = boxesIn(chart, picked);
        if (boxes.size() != 1) {
            throw new IllegalArgumentException("Pick one step.");
        }
        return boxes.getFirst();
    }

    private static List<String> boxesIn(S88ProcedureLogic chart, Set<String> picked) {
        return chart.getSteps().stream()
                .map(S88ProcedureStep::getId)
                .filter(picked::contains)
                .toList();
    }

    private static String oneBar(S88ProcedureLogic chart, Set<String> picked) {
        List<String> bars = chart.getTransitions().stream()
                .map(bar -> bar.getId())
                .filter(picked::contains)
                .toList();
        if (bars.size() != 1) {
            throw new IllegalArgumentException("Pick one branch transition.");
        }
        return bars.getFirst();
    }

    private static String forkOf(S88ProcedureLogic chart, String branch) {
        ChartGraph graph = ChartGraph.of(chart);
        List<String> before = graph.preds(branch);
        if (before.size() != 1 || !graph.isStep(before.getFirst())) {
            throw new IllegalStateException("Transition '" + branch + "' is not the start of a branch.");
        }
        return before.getFirst();
    }

    private static String[] skipOf(S88ProcedureLogic chart, Set<String> picked) {
        ChartGraph graph = ChartGraph.of(chart);
        for (String id : picked) {
            if (graph.isTransition(id)) {
                for (String next : graph.succs(id)) {
                    if (graph.isTransition(next)) {
                        return new String[]{id, next};
                    }
                }
            }
            S88ProcedureLink line = chart.findLink(id).orElse(null);
            if (line != null) {
                for (S88IdRef from : line.getFrom()) {
                    for (S88IdRef to : line.getTo()) {
                        if (graph.isTransition(from.getValue())
                                && graph.isTransition(to.getValue())) {
                            return new String[]{from.getValue(), to.getValue()};
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException("The picked thing is not a skip.");
    }

    private static String commonStepAfter(S88ProcedureLogic chart, List<String> boxes) {
        ChartGraph graph = ChartGraph.of(chart);
        Set<ChartGraph.Edge> back = graph.backEdges();
        for (S88ProcedureStep candidate : chart.getSteps()) {
            String id = candidate.getId();
            if (boxes.contains(id)) {
                continue;
            }
            if (boxes.stream().allMatch(box -> reaches(graph, back, box, id))) {
                return id;
            }
        }
        throw new IllegalStateException("The picked steps have no step after them in common.");
    }

    private static boolean reaches(ChartGraph graph, Set<ChartGraph.Edge> back,
                                   String from, String to) {
        ArrayDeque<String> waiting = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        waiting.add(from);
        while (!waiting.isEmpty()) {
            String node = waiting.removeFirst();
            for (String next : graph.succs(node)) {
                if (back.contains(new ChartGraph.Edge(node, next))) {
                    continue;
                }
                if (next.equals(to)) {
                    return true;
                }
                if (seen.add(next)) {
                    waiting.addLast(next);
                }
            }
        }
        return false;
    }

    private static String[] freeStepNames(S88Recipe recipe, S88ProcedureLogic chart) {
        String stem = "STEP";
        int more = 1;
        while (true) {
            String step = more == 1 ? stem : stem + "_" + more;
            String bar = "T_" + step;
            if (recipe.findElement(step).isEmpty() && chart.findStep(step).isEmpty()
                    && chart.findTransition(step).isEmpty() && chart.findStep(bar).isEmpty()
                    && chart.findTransition(bar).isEmpty()) {
                return new String[]{step, bar};
            }
            more++;
        }
    }

    private static String reasonOf(RuntimeException refusal) {
        String message = refusal.getMessage();
        if (message == null || message.isBlank()) {
            return refusal.getClass().getSimpleName() + ".";
        }
        return message.replace(" Nothing has been changed.", "").trim();
    }
}
