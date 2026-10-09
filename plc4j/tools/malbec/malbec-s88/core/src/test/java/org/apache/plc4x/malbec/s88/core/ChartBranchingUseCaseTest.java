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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chart the buttons build, checked one press at a time.
 *
 * <p>Every test here calls the same use cases the toolbar buttons call, so what passes is what the
 * buttons do.
 */
class ChartBranchingUseCaseTest {

    private static S88MasterRecipe recipe() {
        return CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
    }

    private static S88MasterRecipe withOneStep() {
        S88MasterRecipe recipe = recipe();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        return recipe;
    }

    @Test
    void aStepGoesInAfterTheBoxThatIsPicked() {
        S88MasterRecipe recipe = recipe();

        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");

        assertEquals(List.of("BEGIN", "T_STEP", "BOX_STEP", "T1", "END"), flowOf(recipe),
                "and the flow goes through it, with a bar on each side, and what used to follow "
                        + "carries on behind it");
    }

    @Test
    void aStepCanBeAddedAsManyTimesAsAsked() {
        S88MasterRecipe recipe = recipe();
        String last = "BEGIN";

        for (int n = 0; n < 5; n++) {
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, last);
            last = n == 0 ? "BOX_STEP" : "BOX_STEP_" + n;
        }

        assertEquals(List.of("BEGIN", "T_STEP", "BOX_STEP", "T_STEP_1", "BOX_STEP_1",
                "T_STEP_2", "BOX_STEP_2", "T_STEP_3", "BOX_STEP_3", "T_STEP_4", "BOX_STEP_4",
                "T1", "END"), flowOf(recipe),
                "and five presses make five steps one under the other, each with its own bar, "
                        + "because the names move along instead of landing on the bar the previous "
                        + "press made");
    }

    @Test
    void aBarThatTookTheNameMovesTheWholeTripleAlong() {
        S88MasterRecipe recipe = recipe();
        EditProcedureLogicUseCase.addTransition(recipe, null, "T_STEP", null);

        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");

        assertEquals(List.of("BEGIN", "T_STEP_1", "BOX_STEP_1", "T1", "END"), flowOf(recipe),
                "and the step does not land on the bar that was already there, which is what used "
                        + "to stop the second press with a name already taken");
    }

    @Test
    void aParallelBranchTakesTheBarsItWasAlreadyGiven() {
        S88MasterRecipe recipe = withOneStep();

        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");

        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), departuresOf(recipe, "T_STEP"),
                "and no bar is made: the bar that fed the step now feeds both");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), arrivalsOf(recipe, "T1"),
                "and the bar behind it now receives both");
        assertEquals(S88LinkType.PARALLEL_DIVERGENT, leaving(recipe, "T_STEP").getLinkType(),
                "and the line out of that bar is the parallel kind, which is what draws one bar "
                        + "across the branches instead of two");
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, arriving(recipe, "T1").getLinkType(),
                "and the line into the bar behind is too");
    }

    @Test
    void aParallelBranchGrowsByOneEachTime() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");

        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("BOX_STEP", "BOX_STEP_1", "BOX_STEP_2"),
                departuresOf(recipe, "T_STEP"),
                "and a third press adds a third step to the same bar, rather than a branch inside "
                        + "the second one");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1", "BOX_STEP_2"), arrivalsOf(recipe, "T1"),
                "and all three come back to the same bar");
    }

    @Test
    void anAlternativeBranchGivesEachBranchABarOfItsOwn() {
        S88MasterRecipe recipe = withOneStep();

        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");

        assertEquals(List.of("T_STEP", "T_BRANCH"), departuresOf(recipe, "BEGIN"),
                "and the line that splits is the one leaving the step before the bar");
        assertEquals(S88LinkType.SERIAL_DIVERGENT, leaving(recipe, "BEGIN").getLinkType(),
                "and it is the selective kind, so the drawing puts one bar across and not two");
        assertEquals(List.of("BEGIN"), arrivalsOf(recipe, "T_STEP"),
                "and the bar the step already had arrives from where it always did");
        assertEquals(List.of("BOX_STEP_1"), departuresOf(recipe, "T_BRANCH"),
                "and the bar that was made leads to the new step");
        assertEquals(List.of("BOX_STEP_1"), arrivalsOf(recipe, "T_BRANCH_1"),
                "and the new step has a bar of its own behind it, which is where that branch's "
                        + "condition goes");
        assertEquals(List.of("END"), departuresOf(recipe, "T1"),
                "and the bar behind the first step still carries on to what followed");
        assertEquals(List.of("END"), departuresOf(recipe, "T_BRANCH_1"),
                "and so does the one behind the second");
        assertEquals(S88LinkType.SERIAL_CONVERGENT, leaving(recipe, "T_BRANCH_1").getLinkType(),
                "and the two arrive on one line, which is what says they come back together");
    }

    @Test
    void anAlternativeBranchGrowsByOneEachTime() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");

        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("T_STEP", "T_BRANCH", "T_BRANCH_2"), departuresOf(recipe, "BEGIN"),
                "and the third branch hangs off the same line as the first two");
        assertEquals(List.of("T1", "T_BRANCH_1", "T_BRANCH_3"), arrivalsOf(recipe, "END"),
                "and the three bars behind the branches come together onto what followed");
    }

    @Test
    void aStepCanBeAddedInsideAParallelBranchAndItStaysThere() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");

        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_1");

assertEquals(List.of("BOX_STEP", "BOX_STEP_2"), arrivalsOf(recipe, "T1"),
                "and the new step is the one that arrives at the bar the whole branch arrives at");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), departuresOf(recipe, "T_STEP"),
                "and the branch still leaves from the step it left from, which now carries on to"
                        + " the new step rather than straight to the convergence");
        assertEquals(List.of("T_STEP_2"), departuresOf(recipe, "BOX_STEP_1"),
                "and the branch carries on through a bar of its own to the step that was added");
    }

    @Test
    void aStepInsideAnAlternativeBranchKeepsThatBranchsOwnBar() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");

        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("BOX_STEP_2"), arrivalsOf(recipe, "T_BRANCH_1"),
                "and it arrives at the bar that ends that branch, not at the bar of another one");
        assertEquals(List.of("BOX_STEP"), arrivalsOf(recipe, "T1"),
                "and the first branch is untouched");
    }

    @Test
    void whereTheFlowStartsOrStopsThereIsNothingToBranch() {
        S88MasterRecipe recipe = recipe();

        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.addParallelBranch(recipe, null, "BEGIN"),
                "and the start is refused rather than branched, because there is no step before it "
                        + "for a branch to sit behind");
        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BEGIN"),
                "and the same for a branch of the other kind");
        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.addParallelBranch(recipe, null, "END"),
                "and the stop is refused, because a branch after it is a process that carries on "
                        + "after it has stopped");
        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.addAlternativeBranch(recipe, null, "END"),
                "and the same for a branch of the other kind");
    }

    @Test
    void aStepWhereTheFlowSplitsIsRefusedAndNothingIsTakenOffTheChart() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");
        List<String> before = List.copyOf(departuresOf(recipe, "BEGIN"));

        assertThrows(IllegalStateException.class,
                () -> BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN"),
                "and a step cannot go where the flow splits, because there is no single flow to "
                        + "put it in");
        assertEquals(before, departuresOf(recipe, "BEGIN"),
                "and the branch that was there is still there, so nothing was taken off behind the "
                        + "author's back");
    }

    @Test
    void aStepThatIsAlreadyOnAParallelBranchCanBeBranchedAgain() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");

        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("BOX_STEP_1", "BOX_STEP_2"), departuresOf(recipe, "T_BRANCH"),
                "and the nested parallel hangs off that branch's bar, which is what makes one "
                        + "branch of a branch");
        assertEquals(List.of("T_STEP", "T_BRANCH"), departuresOf(recipe, "BEGIN"),
                "and the branch it hangs off has not gained anything");
    }

    /** The nodes the flow goes through when there is only one at a time. */
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

/** Everything that arrives at one node, across the convergent lines as well as the ordinary one. */
    private static List<String> arrivalsOf(S88MasterRecipe recipe, String id) {
        List<String> found = new ArrayList<>();
        for (S88ProcedureLink link : chartOf(recipe).getLinks()) {
            if (link.getTo().stream().anyMatch(end -> id.equals(end.getValue()))) {
                link.getFrom().forEach(end -> found.add(end.getValue()));
            }
        }
        return found.stream().filter(name -> !name.equals(id)).distinct().toList();
    }

    /** Everything that leaves one node, across the divergent lines as well as the ordinary one. */
    private static List<String> departuresOf(S88MasterRecipe recipe, String id) {
        List<String> found = new ArrayList<>();
        for (S88ProcedureLink link : chartOf(recipe).getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> id.equals(end.getValue()))) {
                link.getTo().forEach(end -> found.add(end.getValue()));
            }
        }
        return found.stream().filter(name -> !name.equals(id)).distinct().toList();
    }

    private static S88ProcedureLink arriving(S88MasterRecipe recipe, String id) {
        return chartOf(recipe).getLinks().stream()
                .filter(link -> link.getTo().stream().anyMatch(end -> id.equals(end.getValue())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line arrives at " + id));
    }

    private static S88ProcedureLink leaving(S88MasterRecipe recipe, String id) {
        return chartOf(recipe).getLinks().stream()
                .filter(link -> link.getFrom().stream().anyMatch(end -> id.equals(end.getValue())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line leaves " + id));
    }

    private static S88ProcedureLogic chartOf(S88MasterRecipe recipe) {
        return recipe.getProcedureLogic();
    }

}