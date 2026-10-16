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
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
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

    /** A chart whose flow goes one of two ways, with a step on each side. */
    private static S88MasterRecipe withAnAlternativeBlock() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP");
        return recipe;
    }

    /** A chart whose flow splits in two and both sides run at once. */
    private static S88MasterRecipe withAParallelBlock() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP");
        return recipe;
    }

    /**
     * A chart whose flow splits in two, with steps on the side picked that have bars of their own.
     *
     * <p>A step alone on its branch has no flow of its own to put a block under, because the block
     * outside splits into it and comes back together out of it. Two more steps give that branch a
     * bar of its own after each, which is what a block can be put under.
     */
    private static S88MasterRecipe withAParallelBlockAndStepsOnOneSide() {
        S88MasterRecipe recipe = withAParallelBlock();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_1");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_2");
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

    /**
     * A parallel branch can be asked for on a step inside a selective block, but it does not get
     * there by changing what the block it is in is.
     *
     * <p>The line the flow gets to that step by is the one the block opens with, so widening it
     * with a third side and marking it parallel says the three branches run at once. The operator
     * asked for the opposite of that.
     */
    @Test
    void aParallelBranchInsideASelectiveBlockIsRefusedRatherThanChangingTheBlock() {
        S88MasterRecipe recipe = withAnAlternativeBlock();

        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_1"),
                "and it says so instead of turning the block the step is in into the other kind");
    }

    /** The other way round: a selective branch thrown at a step inside a parallel block. */
    @Test
    void aSelectiveBranchInsideAParallelBlockIsRefusedRatherThanChangingTheBlock() {
        S88MasterRecipe recipe = withAParallelBlock();

        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP_1"),
                "and it says so instead of writing over what the block it is in says");
    }

    /**
     * A step on no block at all has a branch made beside it either way, which is what opens a
     * block in the first place.
     */
    @Test
    void aStepOnNoBlockIsBranchedEitherWay() {
        S88MasterRecipe byTurns = withOneStep();
        ChartBranchingUseCase.addAlternativeBranch(byTurns, null, "BOX_STEP");
        assertEquals(List.of("BOX_STEP"), departuresOf(byTurns, "T_STEP"),
                "and the branch by turns keeps the step it was made on the bar it already had");
        assertEquals(List.of("BOX_STEP_1"), departuresOf(byTurns, "T_BRANCH"),
                "and puts the new one on a bar of its own");
        assertEquals(List.of("T1", "T_BRANCH_1"), arrivalsOf(byTurns, "END"),
                "and both come back together on the stop");

        S88MasterRecipe atOnce = withOneStep();
        ChartBranchingUseCase.addParallelBranch(atOnce, null, "BOX_STEP");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), departuresOf(atOnce, "T_STEP"),
                "and the parallel one runs the step and the new one at the same time, off the bar");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), arrivalsOf(atOnce, "T1"),
                "and they come back together where the step already ended");
    }
    
    /**
     * A step on a parallel branch is given another side of that same block, and not one of any
     * block it happens to sit next to.
     *
     * <p>What is under the step is a block of its own, which used to widen the block the step is
     * already in. Widening it there said the branches of the outer block ran at once when they go
     * one at a time, which is what the block of its own is for.
     */
    /**
     * A block put under a step keeps the step on its branch and gives it one beside it.
     *
     * <p>The block opens where the step already was and closes where the step already led to, so
     * everything it does stays inside the branch the step was on.
     */
    @Test
    void aBlockPutUnderAStepKeepsTheStepAndGivesItOneBesideIt() {
        S88MasterRecipe recipe = withOneStep();

        S88RecipeElement added = ChartBranchingUseCase.nestParallelBranch(recipe, null, "BOX_STEP");

        assertTrue(boxOf(recipe, "BOX_STEP"),
                "and the step the block goes under is still there");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), departuresOf(recipe, "T_STEP"),
                "and both sides come off the bar the step already had");
        assertEquals("STEP_1", added.getId(),
                "and the added step is the second side");
        assertEquals(List.of("BOX_STEP", "BOX_STEP_1"), arrivalsOf(recipe, "T1"),
                "and they come back together on the bar the step already ended on");
        assertEquals(1, StructureQueries.enclosingBlocks(
                recipe.getProcedureLogic(), "BOX_STEP").size(),
                "and the step is inside the block that was put under it");
    }

    /**
     * A block put under a step inside a branch stays in that branch.
     *
     * <p>The outer block is untouched: the new block opens and closes inside the branch, so what
     * comes back together is a bar of its own rather than the one the outer block uses.
     */
    @Test
    void aBlockPutUnderAStepInsideABranchStaysInThatBranch() {
        S88MasterRecipe recipe = withAParallelBlockAndStepsOnOneSide();

        S88RecipeElement added =
                ChartBranchingUseCase.nestParallelBranch(recipe, null, "BOX_STEP_2");

        assertEquals(2, StructureQueries.enclosingBlocks(
                        recipe.getProcedureLogic(), "BOX_STEP_2").size(),
                "and the step is inside the block it was on and the one put under it");
        assertEquals(S88LinkType.PARALLEL_DIVERGENT,
                StructureQueries.enclosingBlocks(recipe.getProcedureLogic(), added.getId() == null
                        ? "" : "BOX_" + added.getId()).get(0),
                "and the one put under it is the innermost, which is the one opened last");
        assertEquals(List.of("BOX_STEP_2", "BOX_" + added.getId()),
                arrivalsOf(recipe, "T_STEP_3"),
                "and both sides of the block come back to the bar the step already went to");
    }

    /**
     * A block put under a step of a branch by turns gives each side its own bar.
     *
     * <p>That is what makes it a block where only one side is taken, rather than one where both run.
     */
    @Test
    void aBlockOfTurnsPutUnderAStepGivesEachSideItsOwnBar() {
        S88MasterRecipe recipe = withOneStep();

        S88RecipeElement added =
                ChartBranchingUseCase.nestAlternativeBranch(recipe, null, "BOX_STEP");

        assertEquals("STEP_1", added.getId(), "and the added step is the second side");
        assertEquals(List.of("BOX_STEP"), departuresOf(recipe, "T_STEP"),
                "and the step keeps the bar it already had");
        assertEquals(List.of("BOX_STEP_1"), departuresOf(recipe, "T_BRANCH"),
                "and the new step has a bar of its own");
        assertEquals(List.of("T1", "T_BRANCH_1"), arrivalsOf(recipe, "END"),
                "and both come back together before the stop");
    }

    /** The other way round: a branch by turns put under a step of a parallel branch. */
    @Test
    void aBlockOfTurnsUnderAStepOfAParallelBranchStaysInThatBranch() {
        S88MasterRecipe recipe = withAParallelBlockAndStepsOnOneSide();

        S88RecipeElement added =
                ChartBranchingUseCase.nestAlternativeBranch(recipe, null, "BOX_STEP_2");

        assertEquals("STEP_4", added.getId(), "and there is one new step in it");
        List<S88LinkType> held = StructureQueries.enclosingBlocks(
                recipe.getProcedureLogic(), "BOX_" + added.getId());
        assertEquals(2, held.size(),
                "and it is inside the block it was made in and the one put under it");
        assertEquals(S88LinkType.SERIAL_DIVERGENT, held.get(0),
                "and the one put under it is the turns kind, which is the one asked for");
        assertEquals(S88LinkType.PARALLEL_DIVERGENT, held.get(1),
                "and the one it is inside of is the parallel one it was made in");
        assertEquals(List.of("T_STEP_2", "T_BRANCH"), departuresOf(recipe, "BOX_STEP_1"),
                "and both sides leave the step before the one picked, which is what gives each side"
                        + " a bar of its own to wait on");
        assertEquals(List.of("T_BRANCH"), arrivalsOf(recipe, "BOX_" + added.getId()),
                "and the new step has a bar of its own where it starts");
        assertEquals(List.of("T_BRANCH_1"), departuresOf(recipe, "BOX_" + added.getId()),
                "and another where it ends, which is what makes it a branch of its own");
    }

    /**
     * A step that is already on a branch point has nothing to put a block under.
     *
     * <p>There is no single flow to split.
     */
    @Test
    void aStepThatAlreadyGoesToMoreThanOnePlaceHasNothingToPutABlockUnder() {
        S88MasterRecipe recipe = withAParallelBlock();

        assertThrows(IllegalStateException.class,
                () -> ChartBranchingUseCase.nestParallelBranch(recipe, null, "BOX_STEP"),
                "and it says so rather than putting a second block on one that is not one flow");
    }

    @Test
    void aStepOnAParallelBranchIsGivenAnotherSideOfThatBlock() {
        S88MasterRecipe recipe = withAParallelBlock();

        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_1");

        assertEquals(List.of("BOX_STEP", "BOX_STEP_1", "BOX_STEP_2"),
                departuresOf(recipe, "T_STEP"),
                "and the block it was already in gains a third branch, rather than a block of the"
                        + " step's own under it");
        assertEquals(1, StructureQueries.enclosingBlocks(recipe.getProcedureLogic(),
                        "BOX_STEP_1").size(),
                "and the step is inside the one block it was already in, not inside two");
    }

    /** Whether the chart carries a box of that name. */
    private static boolean boxOf(S88MasterRecipe recipe, String boxId) {
        return recipe.getProcedureLogic().findStep(boxId).isPresent();
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