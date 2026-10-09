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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking a step off the chart and joining the flow across it.
 *
 * <p>Every test ends by asking the chart whether anything is left pointing at nothing, because a
 * line that ends in mid-air is what an author sees when a deletion did not finish the job.
 */
class ChartDeletionUseCaseTest {

    private static S88MasterRecipe runOfTwoSteps() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP");
        return recipe;
    }

    private static S88MasterRecipe runOfThreeSteps() {
        S88MasterRecipe recipe = runOfTwoSteps();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_1");
        return recipe;
    }

    /** A parallel branch whose second branch has a step of its own behind the forked one. */
    private static S88MasterRecipe parallelWithASecondStepInOneBranch() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_2");
        return recipe;
    }

    /** A parallel branch whose second branch is long enough to have a step in the middle of it. */
    private static S88MasterRecipe parallelWithAThirdStepInOneBranch() {
        S88MasterRecipe recipe = parallelWithASecondStepInOneBranch();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_3");
        return recipe;
    }

    /** An alternative branch whose new branch has a second step of its own behind the first. */
    private static S88MasterRecipe alternativeWithASecondStepInItsBranch() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_2");
        return recipe;
    }

    /** An alternative branch whose new branch is long enough to have a step in the middle of it. */
    private static S88MasterRecipe alternativeWithAThirdStepInItsBranch() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_2");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_3");
        return recipe;
    }

    @Test
    void takingAStepOffAStraightRunJoinsWhatItJoined() {
        S88MasterRecipe recipe = runOfTwoSteps();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("BEGIN", "T_STEP", "BOX_STEP", "T1", "END"), flowOf(recipe),
                "and the step and the bar that was only waiting on it are gone, with the flow"
                        + " carrying on as it was before the step was put in");
        assertHoldsTogether(recipe);
    }

    @Test
    void theStepOfTheRecipeGoesWithItsBox() {
        S88MasterRecipe recipe = runOfTwoSteps();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_1");

        assertTrue(recipe.findElement("STEP_1").isEmpty(),
                "and the step itself is gone from the recipe, not only its box, because a step the"
                        + " recipe still carries is a step nothing draws");
    }

    @Test
    void takingTheLastStepOfAParallelBranchReconnectsItToWhereItConverged() {
        S88MasterRecipe recipe = parallelWithASecondStepInOneBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_3");

        assertEquals(List.of("BOX_STEP", "BOX_STEP_2"), arrivalsOf(recipe, "T_STEP_1"),
                "and the branch comes back to the bar the whole branch comes back to, rather than"
                        + " hanging under a bar nothing follows");
        assertEquals(List.of("T_STEP_1"), departuresOf(recipe, "BOX_STEP_2"),
                "and the step before it carries on to it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheFirstStepOfAParallelBranchLeavesTheRestOfItHangingOffTheFork() {
        S88MasterRecipe recipe = parallelWithASecondStepInOneBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");

        assertEquals(List.of("BOX_STEP", "BOX_STEP_3"), departuresOf(recipe, "T_STEP"),
                "and the rest of that branch is now hanging off the fork that opened it, rather"
                        + " than off nothing");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_3"), arrivalsOf(recipe, "T_STEP_1"),
                "and it comes back with the other branch, as it did before");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheOnlyStepOfABranchTakesThatBranchWithIt() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");

        assertEquals(List.of("BOX_STEP"), departuresOf(recipe, "T_STEP"),
                "and the fork is left with the one branch that is still there");
        assertEquals(List.of("BOX_STEP"), arrivalsOf(recipe, "T_STEP_1"),
                "and so is the convergence, so the chart is a straight run again rather than a"
                        + " fork with nothing on one side");
        assertFalse(chartOf(recipe).findStep("BOX_STEP_2").isPresent(),
                "and the step of that branch is gone from the recipe too");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheOnlyStepOfAnAlternativeBranchTakesItsTwoBarsWithIt() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");

        assertEquals(List.of("T_STEP_1"), departuresOf(recipe, "BOX_STEP"),
                "and the branch is gone from the split, because there is nothing left of it");
        assertFalse(chartOf(recipe).findTransition("T_BRANCH").isPresent(),
                "and the bar that waited on that branch is gone with it");
        assertFalse(chartOf(recipe).findTransition("T_BRANCH_1").isPresent(),
                "and so is the bar behind it, because a bar nothing arrives at waits on nothing");
        assertEquals(List.of("T_STEP"), departuresOf(recipe, "BEGIN"),
                "and the split is left with the one bar that is still there");
        assertEquals(List.of("BOX_STEP_1"), departuresOf(recipe, "T_STEP_1"),
                "and the other branch still leads to its own step");
        assertHoldsTogether(recipe);
    }

    @Test
    void aForkTakenApartBranchByBranchEndsUpAsTheRunItWas() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");
        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP");

        assertEquals(List.of("BEGIN", "T_STEP_1", "BOX_STEP_1", "T1", "END"), flowOf(recipe),
                "and nothing is left of either branch");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheFirstStepOfAParallelBranchLeavesTheForkCallingItABranch() {
        S88MasterRecipe recipe = parallelWithASecondStepInOneBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");

        assertEquals(S88LinkType.PARALLEL_DIVERGENT, typeOfLineLeaving(recipe, "T_STEP"),
                "and the line still says it splits, because there is still a branch to split into"
                        + " and a plain line would draw two diagonals with nothing across them");
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, typeOfLineArrivingAt(recipe, "T_STEP_1"),
                "and the line the branch comes back on still says it joins, for the same reason");
        assertFalse(chartOf(recipe).findTransition("T_STEP_3").isPresent(),
                "and the bar that was only waiting on the step that is gone goes with it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheLastStepOfAParallelBranchLeavesTheConvergenceCallingItAJoin() {
        S88MasterRecipe recipe = parallelWithASecondStepInOneBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_3");

        assertEquals(S88LinkType.PARALLEL_CONVERGENT, typeOfLineArrivingAt(recipe, "T_STEP_1"),
                "and the line still says it joins, because two branches are still coming back to"
                        + " that bar");
        assertEquals(S88LinkType.PARALLEL_DIVERGENT, typeOfLineLeaving(recipe, "T_STEP"),
                "and the line that splits is untouched, because both branches are still there");
        assertEquals(List.of("T_STEP_1"), departuresOf(recipe, "BOX_STEP_2"),
                "and the step before the one that is gone leads straight to the bar the whole branch"
                        + " comes back to");
        assertFalse(chartOf(recipe).findTransition("T_STEP_3").isPresent(),
                "and the bar that was only waiting on the step that is gone goes with it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheOnlyStepOfAParallelBranchLeavesALineThatNoLongerSplits() {
        S88MasterRecipe recipe = runOfTwoSteps();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");

        assertEquals(S88LinkType.CONTROL_LINK, typeOfLineLeaving(recipe, "T_STEP"),
                "and the line goes back to being an ordinary line, because there is only one way"
                        + " out of that bar now and a split with nothing on one side would draw a"
                        + " bar across a single line");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheFirstStepOfAnAlternativeBranchHandsTheBarToTheNextOne() {
        S88MasterRecipe recipe = alternativeWithASecondStepInItsBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_2");

        assertEquals(List.of("BOX_STEP_3"), departuresOf(recipe, "T_BRANCH"),
                "and the bar that waited on that branch now waits on the step behind the one that"
                        + " is gone");
        assertEquals(List.of("T_STEP", "T_BRANCH"), departuresOf(recipe, "BEGIN"),
                "and the branch is still one of the ways the flow goes, because there is still a"
                        + " step left in it");
        assertFalse(chartOf(recipe).findTransition("T_STEP_3").isPresent(),
                "and the bar between the two steps of that branch goes with the step before it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingTheLastStepOfAnAlternativeBranchHandsTheBarBehindToTheOneBefore() {
        S88MasterRecipe recipe = alternativeWithASecondStepInItsBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_3");

        assertEquals(List.of("T_BRANCH_1"), departuresOf(recipe, "BOX_STEP_2"),
                "and the step that is left in that branch carries on to the bar that ends it");
        assertEquals(List.of("BOX_STEP_2"), arrivalsOf(recipe, "T_BRANCH_1"),
                "and that bar still waits on the branch and nothing else");
        assertEquals(List.of("T_STEP_1", "T_BRANCH_1"), arrivalsOf(recipe, "BOX_STEP_1"),
                "and the branch still ends where it ended, joining the other one");
        assertFalse(chartOf(recipe).findTransition("T_STEP_3").isPresent(),
                "and the bar that was only waiting on the step that is gone goes with it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingAStepFromTheMiddleOfAnAlternativeBranchJoinsTheStepsAroundIt() {
        S88MasterRecipe recipe = alternativeWithAThirdStepInItsBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_3");

        assertEquals(List.of("T_STEP_4"), departuresOf(recipe, "BOX_STEP_2"),
                "and the step before the one that is gone carries on to the bar the one behind it"
                        + " waited at, because a step is always reached through a bar");
        assertEquals(List.of("BOX_STEP_2"), arrivalsOf(recipe, "T_STEP_4"),
                "and that bar waits on the step that is left and nothing else");
        assertEquals(List.of("T_STEP_4"), arrivalsOf(recipe, "BOX_STEP_4"),
                "and that step is still reached through a bar, as every step is");
        assertEquals(List.of("T_STEP", "T_BRANCH"), departuresOf(recipe, "BEGIN"),
                "and the branch is still a branch, because two of its three steps are still there");
        assertFalse(chartOf(recipe).findTransition("T_STEP_3").isPresent(),
                "and the bar that was only waiting on the step that is gone goes with it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingAMiddleStepOffAStraightRunJoinsTheStepsAroundIt() {
        S88MasterRecipe recipe = runOfThreeSteps();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("T_STEP_2"), departuresOf(recipe, "BOX_STEP"),
                "and the step before it carries on to the bar the one behind it waited at, because"
                        + " a step is always reached through a bar");
        assertEquals(List.of("T_STEP_2"), arrivalsOf(recipe, "BOX_STEP_2"),
                "and that step is still reached through a bar, as every step is");
        assertFalse(chartOf(recipe).findTransition("T_STEP_1").isPresent(),
                "and the bar that was only waiting on the step that is gone goes with it");
        assertHoldsTogether(recipe);
    }

    @Test
    void takingAStepFromTheMiddleOfAParallelBranchJoinsTheStepsAroundIt() {
        S88MasterRecipe recipe = parallelWithAThirdStepInOneBranch();

        ChartDeletionUseCase.deleteBox(recipe, null, "BOX_STEP_3");

assertEquals(List.of("T_STEP_4"), departuresOf(recipe, "BOX_STEP_2"),
                "and the step before the one that is gone carries on to the bar the one behind it"
                        + " waited at, because a step is always reached through a bar");
        assertEquals(List.of("BOX_STEP_2"), arrivalsOf(recipe, "T_STEP_4"),
                "and that bar waits on the step that is left and nothing else");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_4"), arrivalsOf(recipe, "T_STEP_1"),
                "and the branch still comes back with the other one, which it did before");
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, typeOfLineArrivingAt(recipe, "T_STEP_1"),
                "and the line it comes back on still says it joins, because two branches are still"
                        + " coming back to that bar");
        assertFalse(chartOf(recipe).findTransition("T_STEP_3").isPresent(),
                "and the bar that was only waiting on the step that is gone goes with it");
        assertHoldsTogether(recipe);
    }

    @Test
    void whereTheFlowStartsOrStopsNothingIsDeleted() {
        S88MasterRecipe recipe = runOfTwoSteps();

        assertThrows(IllegalStateException.class,
                () -> ChartDeletionUseCase.deleteBox(recipe, null, "BEGIN"),
                "and the start is kept, because a recipe with no start cannot run");
        assertThrows(IllegalStateException.class,
                () -> ChartDeletionUseCase.deleteBox(recipe, null, "END"),
                "and the stop is kept for the same reason");
        assertEquals(List.of("BEGIN", "T_STEP", "BOX_STEP", "T_STEP_1", "BOX_STEP_1", "T1", "END"),
                flowOf(recipe),
                "and the chart is exactly as it was, because both refusals happened before anything"
                        + " was touched");
    }

    /** Nothing pointing at nothing, every line still crossing, and no step the flow never reaches. */
    private static void assertHoldsTogether(S88MasterRecipe recipe) {
        assertEquals(List.of(), chartOf(recipe).getDanglingReferences(),
                "and nothing on the chart points at something that is not there");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(chartOf(recipe)),
                "and no line runs from a box to a box or from a bar to a bar");
        assertEquals(List.of(), RecipeConformance.of(recipe).excess(),
                "and the recipe still holds together as a recipe");
        assertOneLineEachWay(recipe);
    }

    /**
     * Every box and every bar is reached by one line and goes out by one line, except where the
     * flow starts and stops. A node with two lines arriving is a chart no later edit can branch
     * from, and it is silent: nothing complains until an author presses a button.
     */
    private static void assertOneLineEachWay(S88MasterRecipe recipe) {
        for (String box : namesOfSteps(recipe)) {
            if (box.equals("BEGIN")) {
                assertEquals(0, linesArrivingAt(recipe, box).size(),
                        "and nothing arrives at the start");
            } else {
                assertEquals(1, linesArrivingAt(recipe, box).size(),
                        "and exactly one line arrives at '" + box + "'");
            }
            if (box.equals("END")) {
                assertEquals(0, linesLeaving(recipe, box).size(), "and nothing leaves the stop");
            } else {
                assertEquals(1, linesLeaving(recipe, box).size(),
                        "and exactly one line leaves '" + box + "'");
            }
        }
        for (String bar : namesOfBars(recipe)) {
            assertEquals(1, linesArrivingAt(recipe, bar).size(),
                    "and exactly one line arrives at bar '" + bar + "'");
            assertEquals(1, linesLeaving(recipe, bar).size(),
                    "and exactly one line leaves bar '" + bar + "'");
        }
    }

    /** Lines are counted, not names: one line out of a split bar reaches two boxes at once. */
    private static List<S88ProcedureLink> linesArrivingAt(S88MasterRecipe recipe, String id) {
        return chartOf(recipe).getLinks().stream()
                .filter(link -> link.getTo().stream().anyMatch(end -> id.equals(end.getValue())))
                .toList();
    }

    private static List<S88ProcedureLink> linesLeaving(S88MasterRecipe recipe, String id) {
        return chartOf(recipe).getLinks().stream()
                .filter(link -> link.getFrom().stream().anyMatch(end -> id.equals(end.getValue())))
                .toList();
    }

    private static List<String> namesOfSteps(S88MasterRecipe recipe) {
        return chartOf(recipe).getSteps().stream().map(step -> step.getId()).toList();
    }

    private static List<String> namesOfBars(S88MasterRecipe recipe) {
        return chartOf(recipe).getTransitions().stream().map(bar -> bar.getId()).toList();
    }

    private static S88LinkType typeOfLineLeaving(S88MasterRecipe recipe, String id) {
        return chartOf(recipe).getLinks().stream()
                .filter(link -> link.getFrom().stream().anyMatch(end -> id.equals(end.getValue())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nothing leaves '" + id + "'"))
                .getLinkType();
    }

    private static S88LinkType typeOfLineArrivingAt(S88MasterRecipe recipe, String id) {
        return chartOf(recipe).getLinks().stream()
                .filter(link -> link.getTo().stream().anyMatch(end -> id.equals(end.getValue())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nothing arrives at '" + id + "'"))
                .getLinkType();
    }

    private static S88ProcedureLogic chartOf(S88MasterRecipe recipe) {
        return recipe.getProcedureLogic();
    }

    private static List<String> flowOf(S88MasterRecipe recipe) {
        List<String> flow = new ArrayList<>();
        flow.add("BEGIN");
        String next = "BEGIN";
        while (next != null) {
            String was = next;
            next = chartOf(recipe).getLinks().stream()
                    .filter(link -> link.getFrom().stream()
                            .anyMatch(end -> was.equals(end.getValue())))
                    .findFirst()
                    .map(link -> link.getTo().get(0).getValue())
                    .orElse(null);
            if (next != null) {
                flow.add(next);
            }
        }
        return flow;
    }

    private static List<String> arrivalsOf(S88MasterRecipe recipe, String id) {
        List<String> found = new ArrayList<>();
        for (S88ProcedureLink link : chartOf(recipe).getLinks()) {
            if (link.getTo().stream().anyMatch(end -> id.equals(end.getValue()))) {
                link.getFrom().forEach(end -> found.add(end.getValue()));
            }
        }
        return found.stream().filter(name -> !name.equals(id)).distinct().toList();
    }

    private static List<String> departuresOf(S88MasterRecipe recipe, String id) {
        List<String> found = new ArrayList<>();
        for (S88ProcedureLink link : chartOf(recipe).getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> id.equals(end.getValue()))) {
                link.getTo().forEach(end -> found.add(end.getValue()));
            }
        }
        return found.stream().filter(name -> !name.equals(id)).distinct().toList();
    }
}