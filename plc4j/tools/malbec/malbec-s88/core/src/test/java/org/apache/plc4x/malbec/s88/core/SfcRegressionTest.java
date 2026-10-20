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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edits that have to keep working once the chart stops being a single chain.
 *
 * <p>The tests that are switched off describe the behaviour the chart should have after the phase
 * named on each one. They are written first so that no later change can quietly keep the old
 * refusal and still look green.
 */
class SfcRegressionTest {

    // ========== The chart should keep working ==========

    @Test
    void aLoopDoesNotStopAStepBeingTakenOff() {
        S88MasterRecipe recipe = chain(3);
        EditProcedureLogicUseCase.joinStepsThroughATransition(recipe, null, "STEP_2", "STEP");

        ChartDeletionUseCase.deleteBox(recipe, null, "STEP_1");

        assertFalse(recipe.findElement("STEP_1").isPresent(),
                "and the step itself goes with the box");
        assertTrue(edges(recipe.getProcedureLogic()).containsAll(Set.of(
                        "STEP_2->T_STEP_2_STEP", "T_STEP_2_STEP->STEP")),
                "and the loop is still there: " + edges(recipe.getProcedureLogic()));
        assertWellFormed(recipe);
    }

    @Test
    void aLoopDoesNotStopAStepOfAParallelBranchBeingTakenOff() {
        S88MasterRecipe recipe = chain(3);
        EditProcedureLogicUseCase.joinStepsThroughATransition(recipe, null, "STEP_2", "STEP");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP_1");

        ChartDeletionUseCase.deleteBox(recipe, null, "STEP_3");

        assertFalse(recipe.findElement("STEP_3").isPresent(),
                "and the branch step goes with its box");
        assertTrue(edges(recipe.getProcedureLogic()).containsAll(Set.of(
                        "STEP_2->T_STEP_2_STEP", "T_STEP_2_STEP->STEP")),
                "and the loop is still there");
        assertWellFormed(recipe);
    }

    @Test
    void aSkipDoesNotStopTheStepAfterItBeingTakenOff() {
        S88MasterRecipe recipe = chain(3);
        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_1");

        ChartDeletionUseCase.deleteBox(recipe, null, "STEP_2");

        assertFalse(recipe.findElement("STEP_2").isPresent(),
                "and the step itself goes with the box");
        assertNoTransitionWithoutAnExit(recipe);
        assertWellFormed(recipe);
    }

    @Test
    void takingOffTheOptionalStepLeavesNoDeadEnd() {
        S88MasterRecipe recipe = chain(3);
        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_1");

        ChartDeletionUseCase.deleteBox(recipe, null, "STEP_1");

        assertFalse(recipe.findElement("STEP_1").isPresent(),
                "and the step itself goes with the box");
        assertTrue(edges(recipe.getProcedureLogic()).contains("STEP->T_STEP_1"),
                "and the flow still leaves the step before it: "
                        + edges(recipe.getProcedureLogic()));
        assertNoTransitionWithoutAnExit(recipe);
        assertWellFormed(recipe);
    }

    @Test
    void aStepFeedingADivergenceCanBeTakenOff() {
        S88MasterRecipe recipe = chain(3);
        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_2");

        ChartDeletionUseCase.deleteBox(recipe, null, "STEP_1");

        assertFalse(recipe.findElement("STEP_1").isPresent(),
                "and the step itself goes with the box");
        assertWellFormed(recipe);
    }

    @Test
    @Disabled("F4")
    void aStepCanBePutInFrontOfAStepThatSplits() {
        S88MasterRecipe recipe = divergenceAtAStep();

        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP");

        assertWellFormed(recipe);
    }

    @Test
    @Disabled("F4")
    void optionalBranchesCanBeNestedInAnAlternativeBranch() {
        S88MasterRecipe recipe = chain(3);
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "STEP_1");

        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_1");
        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_3");

        assertWellFormed(recipe);
    }

    // ========== What the code does today, written down before it changes ==========

    /**
     * The shape of a partial synchronization: three branches come back together on one transition
     * and split again from it, which is a many-to-many transition.
     */
    @Test
    void partialSynchronizationIsNotReportedAsABadlyNestedBlock() {
        S88MasterRecipe recipe = master("A", "B", "C", "D", "E");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        step(chart, "BEGIN");
        step(chart, "A");
        step(chart, "B");
        step(chart, "C");
        step(chart, "D");
        step(chart, "E");
        step(chart, "END");
        transition(chart, "T_OPEN");
        transition(chart, "T_MID");
        transition(chart, "T_CLOSE");
        line(chart, "L_BEGIN", S88IdRef.step("BEGIN"), S88IdRef.transition("T_OPEN"));
        split(chart, "L_OPEN", S88IdRef.transition("T_OPEN"), List.of("A", "B", "C"),
                S88LinkType.PARALLEL_DIVERGENT);
        join(chart, "L_MID", List.of("A", "B", "C"), "T_MID");
        split(chart, "L_SPLIT", S88IdRef.transition("T_MID"), List.of("D", "E"),
                S88LinkType.PARALLEL_DIVERGENT);
        join(chart, "L_CLOSE", List.of("D", "E"), "T_CLOSE");
        line(chart, "L_END", S88IdRef.transition("T_CLOSE"), S88IdRef.step("END"));

        assertFalse(excessMentions(recipe, "does not come back together"),
                "a partial synchronization is not a badly nested block: " + excess(recipe));
    }

    /**
     * A node may be reached by more than one line. The block it sits on is opened by any of them,
     * not only by the first line found.
     */
    @Test
    void enclosingBlocksReadsEveryLineIntoANode() {
        S88MasterRecipe recipe = master("X", "OTHER");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        step(chart, "BEGIN");
        step(chart, "X");
        step(chart, "OTHER");
        step(chart, "END");
        transition(chart, "T_OPEN");
        transition(chart, "T_ALT");
        transition(chart, "T_END");
        line(chart, "L_BEGIN_OPEN", S88IdRef.step("BEGIN"), S88IdRef.transition("T_OPEN"));
        line(chart, "L_BEGIN_ALT", S88IdRef.step("BEGIN"), S88IdRef.transition("T_ALT"));
        line(chart, "L_ALT", S88IdRef.transition("T_ALT"), S88IdRef.step("X"));
        split(chart, "L_OPEN", S88IdRef.transition("T_OPEN"), List.of("X", "OTHER"),
                S88LinkType.PARALLEL_DIVERGENT);
        line(chart, "L_X", S88IdRef.step("X"), S88IdRef.transition("T_END"));
        line(chart, "L_OTHER", S88IdRef.step("OTHER"), S88IdRef.transition("T_END"));
        line(chart, "L_END", S88IdRef.transition("T_END"), S88IdRef.step("END"));

        assertEquals(List.of(S88LinkType.PARALLEL_DIVERGENT),
                StructureQueries.enclosingBlocks(chart, "X"),
                "X sits on the parallel block opened at T_OPEN, whichever line into it is read"
                        + " first");
    }

    // ========== Building charts ==========

    /** A straight run with the given number of steps: BEGIN, STEP, STEP_1, ... END. */
    private static S88MasterRecipe chain(int steps) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("R", S88RecipeKind.CLASS);
        String previous = CreateMasterRecipeUseCase.BEGIN_ID;
        for (int i = 0; i < steps; i++) {
            S88RecipeElement added = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, previous);
            previous = added.getId();
        }
        return recipe;
    }

    /** A chart where a step splits into two transitions, each going to a step of its own. */
    private static S88MasterRecipe divergenceAtAStep() {
        S88MasterRecipe recipe = master("STEP", "STEP_1", "STEP_2");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        step(chart, "BEGIN");
        step(chart, "STEP");
        step(chart, "STEP_1");
        step(chart, "STEP_2");
        step(chart, "END");
        transition(chart, "T_STEP");
        transition(chart, "T_A");
        transition(chart, "T_B");
        transition(chart, "T_JOIN");
        line(chart, "L_BEGIN", S88IdRef.step("BEGIN"), S88IdRef.transition("T_STEP"));
        line(chart, "L_STEP", S88IdRef.transition("T_STEP"), S88IdRef.step("STEP"));
        split(chart, "L_SPLIT", S88IdRef.step("STEP"), List.of("T_A", "T_B"),
                S88LinkType.SERIAL_DIVERGENT);
        line(chart, "L_A", S88IdRef.transition("T_A"), S88IdRef.step("STEP_1"));
        line(chart, "L_B", S88IdRef.transition("T_B"), S88IdRef.step("STEP_2"));
        join(chart, "L_JOIN", List.of("STEP_1", "STEP_2"), "T_JOIN");
        line(chart, "L_END", S88IdRef.transition("T_JOIN"), S88IdRef.step("END"));
        return recipe;
    }

    private static S88MasterRecipe master(String... stepIds) {
        S88MasterRecipe recipe = new S88MasterRecipe("R", S88RecipeKind.CLASS);
        recipe.addRecipeElement(new S88RecipeElement("BEGIN", S88RecipeElementKind.BEGIN));
        recipe.addRecipeElement(new S88RecipeElement("END", S88RecipeElementKind.END));
        for (String id : stepIds) {
            recipe.addRecipeElement(new S88RecipeElement(id, S88RecipeElementKind.OPERATION));
        }
        recipe.setProcedureLogic(new S88ProcedureLogic());
        return recipe;
    }

    private static void step(S88ProcedureLogic chart, String id) {
        chart.addStep(new S88ProcedureStep(id, id));
    }

    private static void transition(S88ProcedureLogic chart, String id) {
        chart.addTransition(new S88ProcedureTransition(id, null));
    }

    private static void line(S88ProcedureLogic chart, String id, S88IdRef from, S88IdRef to) {
        S88ProcedureLink link = new S88ProcedureLink(id);
        link.addFrom(from);
        link.addTo(to);
        chart.addLink(link);
    }

    private static void split(S88ProcedureLogic chart, String id, S88IdRef from,
                              List<String> to, S88LinkType type) {
        S88ProcedureLink link = new S88ProcedureLink(id);
        link.addFrom(from);
        to.forEach(name -> link.addTo(S88IdRef.transition(name)));
        link.setLinkType(type);
        chart.addLink(link);
    }

    private static void join(S88ProcedureLogic chart, String id, List<String> from, String to) {
        S88ProcedureLink link = new S88ProcedureLink(id);
        from.forEach(name -> link.addFrom(S88IdRef.step(name)));
        link.addTo(S88IdRef.transition(to));
        link.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        chart.addLink(link);
    }

    // ========== Reading the chart ==========

    /** Every end of every line, written as {@code from->to}. */
    private static Set<String> edges(S88ProcedureLogic chart) {
        Set<String> found = new LinkedHashSet<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            for (S88IdRef from : link.getFrom()) {
                for (S88IdRef to : link.getTo()) {
                    found.add(from.getValue() + "->" + to.getValue());
                }
            }
        }
        return found;
    }

    private static void assertWellFormed(S88MasterRecipe recipe) {
        assertEquals(List.of(), ChartSoundness.check(recipe.getProcedureLogic()),
                "and the chart is sound");
    }

    private static void assertNoTransitionWithoutAnExit(S88MasterRecipe recipe) {
        for (S88ProcedureTransition bar : recipe.getProcedureLogic().getTransitions()) {
            assertFalse(departures(recipe.getProcedureLogic(), bar.getId()).isEmpty(),
                    "and no transition is left with nothing after it: '" + bar.getId() + "'");
        }
    }

    private static List<String> departures(S88ProcedureLogic chart, String id) {
        return chart.getLinks().stream()
                .filter(link -> leaves(link, id))
                .flatMap(link -> link.getTo().stream())
                .map(S88IdRef::getValue)
                .filter(name -> !name.equals(id))
                .toList();
    }

    private static boolean leaves(S88ProcedureLink link, String id) {
        return link.getFrom().stream().anyMatch(end -> id.equals(end.getValue()));
    }

    private static boolean excessMentions(S88MasterRecipe recipe, String fragment) {
        String needle = fragment.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return excess(recipe).stream()
                .map(line -> line.replaceAll("\\s+", " "))
                .anyMatch(line -> line.contains(needle));
    }

    private static List<String> excess(S88MasterRecipe recipe) {
        return RecipeConformance.of(recipe).excess();
    }
}
