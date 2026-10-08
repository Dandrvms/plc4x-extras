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

import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adding a step, a fork and a join to a chart that is being drawn.
 * <p>
 * The chart a recipe is created with already has a start and a stop and one bar between them, so
 * these three are the shapes the buttons on the chart toolbar produce. What is checked is that each
 * leaves a chart the model still allows: every line from a box to a bar, and no line that goes
 * nowhere.
 */
class ChartEditingUseCaseTest {

    // ========== Putting a step after another one ==========

    @Test
    void aStepGoesAfterTheOneItWasAddedAfter() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        S88RecipeElement added = EditProcedureLogicUseCase.insertStepAfter(
                recipe, null, "BEGIN", "MIX", "BOX_MIX", "T_MIX", "AGITATOR");

        assertEquals("AGITATOR", added.getEquipmentClassId());
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertTrue(chart.findStep("BOX_MIX").isPresent(), "and it has a box on the chart");
        assertTrue(chart.findTransition("T_MIX").isPresent(), "waiting on a bar of its own");
    }

    @Test
    void aStepAddedAfterAnotherTakesOverWhereTheFlowWasGoing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, null, "BEGIN", "MIX", "BOX_MIX", "T_MIX", "AGITATOR");

        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertEquals(List.of("T_MIX"), targetsOf(chart, "BEGIN"),
                "the flow leaves the start for the new step's bar");
        assertEquals(List.of("T1"), targetsOf(chart, "BOX_MIX"),
                "and the new step carries on to the bar it was put in front of");
        assertEquals(List.of("END"), targetsOf(chart, "T1"),
                "and from there to the stop, exactly as before");
    }

    @Test
    void onlyOneBarIsAddedBecauseTheOneThatWasThereIsTheOneToWaitAtNext() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, null, "BEGIN", "MIX", "BOX_MIX", "T_MIX", "AGITATOR");

        assertEquals(2, recipe.getProcedureLogic().getTransitions().size(),
                "the one that was there and the new one. Two more would leave the flow going from a"
                        + " bar to a bar, which a chart cannot have");
    }

    @Test
    void theFlowStillRunsFromTheStartToTheStopAfterAStepIsPutIn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, null, "BEGIN", "MIX", "BOX_MIX", "T_MIX", "AGITATOR");

        assertTrue(EditProcedureLogicUseCase.isChartBipartite(recipe.getProcedureLogic()));
        assertTrue(RecipeConformance.of(recipe).deficit().isEmpty(),
                "and nothing is reported missing, because the start and the stop are still joined");
    }

    @Test
    void aStepPutAfterABoxNothingLeavesYetCarriesOnFromTheNewBox() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        // The ordinary case: the operator put a box on the chart and has not joined it up yet.
        addOperation(recipe, "COOL");

        EditProcedureLogicUseCase.insertStepAfter(
                recipe, null, "BOX_COOL", "DRY", "BOX_DRY", "T_DRY", "DRYER");

        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertEquals(List.of("T_DRY"), targetsOf(chart, "BOX_COOL"),
                "the box that had nothing now waits at the new bar");
        assertTrue(targetsOf(chart, "BOX_DRY").isEmpty(),
                "and the new step is the end of the flow, waiting for the operator to carry it on");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(chart));
    }

    @Test
    void aStepThatCannotBePutAfterABoxThatAlreadySplitsInTwo() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.selectiveFork(recipe, null, "BEGIN", "T_ONE", "T_TWO");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, null, "BEGIN", "MIX", "BOX_MIX", "T_A", "AGITATOR"));

        assertTrue(failure.getMessage().contains("places"),
                "and it says why, because which side the step belongs on is the author's choice");
    }

    @Test
    void aStepThatCannotBePutAfterChangesNothing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "MIX");
        var chart = recipe.getProcedureLogic();
        var before = List.copyOf(chart.getLinks());

        // "MIX" is already a step of the recipe, so this cannot be done. The line that leaves BEGIN
        // used to be taken off first, which left a chart with the flow going nowhere.
        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, null, "BEGIN", "MIX", "BOX_QUENCH", "T_QUENCH", "QUENCHER"));

        assertEquals(before, List.copyOf(chart.getLinks()),
                "a change that was refused may not have taken anything off the chart");
        assertEquals(List.of("T1"), targetsOf(chart, "BEGIN"),
                "and the flow still goes where it went");
    }

    @Test
    void aStepWithABoxNameAlreadyTakenChangesNothing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "MIX");
        var chart = recipe.getProcedureLogic();
        var before = List.copyOf(chart.getLinks());

        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, null, "BEGIN", "QUENCH", "BOX_MIX", "T_QUENCH", "QUENCHER"));

        assertEquals(before, List.copyOf(chart.getLinks()),
                "a box name that is taken is refused before the chart is touched");
    }

    @Test
    void aStepCannotBePutAfterABoxThatIsNotThere() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, null, "NOPE", "MIX", "BOX_MIX", "T_A", "AGITATOR"));
    }

    @Test
    void aStepAddedToTheChartIsAlsoAddedToTheRecipe() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, null, "BEGIN", "MIX", "BOX_MIX", "T_MIX", "AGITATOR");

        assertEquals(3, recipe.getRecipeElements().size(),
                "the start, the new step and the stop");
        assertEquals(S88RecipeElementKind.OPERATION, recipe.findElement("MIX").orElseThrow().getKind());
    }

    // ========== Splitting and joining, in the two shapes there are ==========

    /** A step leaving towards two bars is a selective split: one line, two arrivals, one taken. */
    @Test
    void aStepLeavingTowardsTwoBarsIsOneSelectiveLine() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        S88ProcedureLink split = EditProcedureLogicUseCase.selectiveFork(
                recipe, null, "BEGIN", "T_ONE", "T_TWO");

        assertEquals(List.of("BEGIN"), split.getFrom().stream().map(S88IdRef::getValue).toList());
        assertEquals(List.of("T_ONE", "T_TWO"),
                split.getTo().stream().map(S88IdRef::getValue).toList(),
                "one line with two arrivals, because that is what says only one of them is taken");
        assertEquals(S88LinkType.SERIAL_DIVERGENT, split.getLinkType(),
                "and the type is what says which one, rather than that both are");
        assertFalse(split.getLinkType().isParallel(), "which is the whole difference between them");
        assertEquals(1, linksLeaving(recipe.getProcedureLogic(), "BEGIN").stream()
                        .filter(line -> line.getTo().contains(S88IdRef.transition("T_ONE")))
                        .count(),
                "one line out of the step rather than two, because two lines out of a step say"
                        + " nothing about how many of them are taken");
    }

    /** A bar leaving towards two steps is a parallel split: both at once. */
    @Test
    void aBarLeavingTowardsTwoStepsIsOneParallelLine() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        S88ProcedureLink split = EditProcedureLogicUseCase.parallelFork(
                recipe, null, "T1", "HEAT", "COOL");

        assertEquals(S88LinkType.PARALLEL_DIVERGENT, split.getLinkType());
        assertTrue(split.getLinkType().isParallel(), "both branches run at once");
        assertEquals(List.of("BOX_HEAT", "BOX_COOL"),
                split.getTo().stream().map(S88IdRef::getValue).toList(),
                "one line going to both steps");
        assertTrue(recipe.findElement("HEAT").isPresent(),
                "and the steps the branches work on are steps of the recipe, or the chart would be"
                        + " naming boxes that work on nothing");
        assertTrue(recipe.findElement("COOL").isPresent(), "both of them");
        assertTrue(recipe.getProcedureLogic().findStep("BOX_HEAT").orElseThrow()
                        .getRecipeElementId().equals("HEAT"),
                "and each box works on the step it was named after");
    }

    /**
     * A bar on a chart that reads anywhere near right already leads somewhere, so a split of it that
     * dropped what it used to lead to would leave the rest of the recipe unreachable.
     */
    @Test
    void aSplitOfABarThatAlreadyLedSomewhereCarriesItOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        assertEquals(List.of("END"), targetsOf(recipe.getProcedureLogic(), "T1"),
                "and the bar does lead somewhere to begin with");

        EditProcedureLogicUseCase.parallelFork(recipe, null, "T1", "HEAT", "COOL");

        assertEquals(List.of("BOX_HEAT", "BOX_COOL"),
                targetsOf(recipe.getProcedureLogic(), "T1"),
                "so both branches leave it");
        String carriedOn = targetsOf(recipe.getProcedureLogic(), "BOX_HEAT").get(0);
        assertEquals(carriedOn, targetsOf(recipe.getProcedureLogic(), "BOX_COOL").get(0),
                "and the two of them come back together on one bar, because two branches that never"
                        + " meet are two recipes rather than one");
        assertEquals(List.of("END"), targetsOf(recipe.getProcedureLogic(), carriedOn),
                "and what the bar used to lead to is now behind that, rather than stranded");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(recipe.getProcedureLogic()),
                "and a chart with a parallel branch in it is still one where every line crosses a"
                        + " bar");
    }

    /** Two steps onto one bar is a join, and the type says whether both are waited for. */
    @Test
    void twoStepsJoiningOntoOneBarIsOneLineWithTwoDepartures() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "HEAT");
        addOperation(recipe, "COOL");

        S88ProcedureLink join = EditProcedureLogicUseCase.joinOnto(
                recipe, null, "BOX_HEAT", "BOX_COOL", "T_BOTH", S88LinkType.PARALLEL_CONVERGENT);

        assertEquals(2, join.getFrom().size(), "one line with two departures");
        assertEquals(List.of("T_BOTH"), join.getTo().stream().map(S88IdRef::getValue).toList());
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, join.getLinkType(),
                "which is what says both are waited for rather than whichever gets there first");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(recipe.getProcedureLogic()),
                "and a chart with a join in it is still one where every line crosses a bar");
    }

    /** Two steps joining onto one bar that leads nowhere yet. */
    @Test
    void aJoinOfTwoStepsThatLeadNowhereIsOneLineWaitingForBoth() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "HEAT");
        addOperation(recipe, "COOL");

        EditProcedureLogicUseCase.joinOnto(
                recipe, null, "BOX_HEAT", "BOX_COOL", "T_BOTH", S88LinkType.PARALLEL_CONVERGENT);

        assertLeadsOnlyTo(recipe.getProcedureLogic(), "BOX_HEAT",
                "and each of them now leads into the join", "T_BOTH");
        assertLeadsOnlyTo(recipe.getProcedureLogic(), "BOX_COOL",
                "and the other one too", "T_BOTH");
        assertTrue(targetsOf(recipe.getProcedureLogic(), "T_BOTH").isEmpty(),
                "and the join leads nowhere until the author says where the flow goes on, because"
                        + " a step always leads to a bar and so there is nothing here that could be"
                        + " carried on for them");
    }

    @Test
    void aJoinOfOneBoxWithItselfIsRefused() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "MIX");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.joinOnto(
                        recipe, null, "BOX_MIX", "BOX_MIX", "T_X", null));

        assertTrue(failure.getMessage().contains("itself"),
                "a join of one box with itself is a box with nowhere to go");
    }

    /** Every split the module can make has to come out as a chart the drawing can read. */
    @Test
    void everySplitTheModuleCanMakeIsStillAReadableChart() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.selectiveFork(recipe, null, "BEGIN", "T_A", "T_B");

        assertTrue(EditProcedureLogicUseCase.isChartBipartite(recipe.getProcedureLogic()),
                "a selective split still crosses a bar on the way out");
        RecipeConformance report = RecipeConformance.of(recipe);
        assertTrue(report.excess().isEmpty(),
                "and what it leaves behind is not something the conformance rules call broken: "
                        + report.excess());
    }

    // ========== Renaming and taking away ==========

    @Test
    void aBoxIsGivenAnotherNameAndTheLinesIntoItFollow() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.renameStep(recipe, null, "BEGIN", "START");

        assertTrue(recipe.getProcedureLogic().findStep("BEGIN").isEmpty(),
                "and the old name is not left behind as a second box");
        assertTrue(recipe.getProcedureLogic().findStep("START").isPresent(),
                "and the chart finds it under the new name");
        assertLeadsOnlyTo(recipe.getProcedureLogic(), "START",
                "and the line leaving it goes on as before, because a line naming a box that is"
                        + " not there is not a chart anybody can draw", "T1");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(recipe.getProcedureLogic()));
    }

    @Test
    void aBarIsGivenAnotherNameAndTheLinesIntoItFollow() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.renameTransition(recipe, null, "T1", "WHEN_HOT");

        assertTrue(recipe.getProcedureLogic().findTransition("T1").isEmpty());
        assertEquals(List.of("WHEN_HOT"), targetsOf(recipe.getProcedureLogic(), "BEGIN"),
                "and the line leaving the box before it follows it");
        assertEquals(List.of("END"), targetsOf(recipe.getProcedureLogic(), "WHEN_HOT"),
                "and so does the line leaving it");
    }

    @Test
    void aBoxCannotBeGivenANameSomethingElseAlreadyHas() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "HEAT");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.renameStep(recipe, null, "BEGIN", "T1"));

        assertTrue(failure.getMessage().contains("already has a box"),
                "and it says which, rather than swapping two boxes behind the operator's back");
    }

    @Test
    void aBoxThatIsStillJoinedCannotBeTakenOff() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.removeStep(recipe, null, "BEGIN"));

        assertTrue(failure.getMessage().contains("Take those off first"),
                "and it says what to do about it, because taking the box would leave lines with"
                        + " nothing at one end");
    }

    @Test
    void aBarThatIsStillJoinedCannotBeTakenOff() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.removeTransition(recipe, null, "T1"));

        assertTrue(failure.getMessage().contains("Take those off first"),
                "a bar is no different from a box here, and taking one would strand its lines");
    }

    @Test
    void aBarNothingIsJoinedToIsTakenOffAndItSaysSo() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.addTransition(recipe, null, "T_SPARE");

        assertTrue(EditProcedureLogicUseCase.removeTransition(recipe, null, "T_SPARE"));
        assertFalse(EditProcedureLogicUseCase.removeTransition(recipe, null, "T_SPARE"),
                "and asking again says there was no such bar rather than failing");
    }

    // ========== What a bar waits on ==========

    @Test
    void aBarIsGivenWhatItWaitsOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.setCondition(recipe, null, "T1", "Reports/STATE#=#COMPLETE");

        S88ProcedureTransition bar = recipe.getProcedureLogic().findTransition("T1").orElseThrow();
        assertTrue(bar.isGuarded(), "so it is not crossed straight away");
        assertEquals("Reports/STATE#=#COMPLETE", bar.getCondition(),
                "and the text it carries is the text that was asked for");
    }

    @Test
    void aBarCanBeGivenNothingToWaitOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.setCondition(recipe, null, "T1", "Reports/STATE#=#COMPLETE");

        EditProcedureLogicUseCase.setCondition(recipe, null, "T1", "  ");

        S88ProcedureTransition bar = recipe.getProcedureLogic().findTransition("T1").orElseThrow();
        assertFalse(bar.isGuarded(), "and then it is crossed as soon as the step before is done");
    }

    @Test
    void somethingThatIsNotAComparisonIsRefused() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.setCondition(recipe, null, "T1", "just some words"));

        assertTrue(failure.getMessage().contains("ADDRESS"),
                "and it says how a comparison is written, because a bar that waits on something"
                        + " nobody can read is a recipe nobody can run");
    }

        /**
     * Asserts that the flow leaving a box or bar goes where the test says, and nowhere else.
     *
     * <p>
     * Written for charts where more than one line can leave the same node, where listing what it
     * leads to says nothing about whether anything else leads anywhere else.
     */
    private static void assertLeadsOnlyTo(S88ProcedureLogic chart, String from, String because,
                                       String... to) {
        assertEquals(List.of(to), targetsOf(chart, from).stream().distinct().sorted().toList(),
                because + ": " + from + " leads nowhere else");
    }

    private static List<S88ProcedureLink> linksLeaving(S88ProcedureLogic chart, String name) {
        List<S88ProcedureLink> leaving = new java.util.ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> name.equals(end.getValue()))) {
                leaving.add(link);
            }
        }
        return leaving;
    }

    /** A box on the chart working on a step of the recipe of the same name. */
    private static void addOperation(S88MasterRecipe recipe, String id) {
        CreateRecipeElementUseCase.forEquipmentClass(
                recipe, null, id, S88RecipeElementKind.OPERATION, "MACHINE");
        EditProcedureLogicUseCase.addStep(recipe, null, "BOX_" + id, id);
    }

    /**
     * What the lines leaving a box arrive at, in the order the chart holds them.
     * <p>
     * Written here rather than using the chart's own {@code linksFrom}, because that one answers a
     * wider question than its name suggests: it gives the lines touching the box at either end,
     * which is not where the flow goes.
     */
    private static List<String> targetsOf(S88ProcedureLogic chart, String boxId) {
        List<String> targets = new java.util.ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            boolean leavesHere = link.getFrom().stream()
                    .anyMatch(end -> boxId.equals(end.getValue()));
            if (leavesHere) {
                link.getTo().forEach(end -> targets.add(end.getValue()));
            }
        }
        return targets;
    }
}