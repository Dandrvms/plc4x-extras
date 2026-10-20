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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                recipe, operationOf(recipe), "BEGIN", "MIX", "MIX", "T_MIX", "AGITATOR");

        assertEquals("AGITATOR", added.getEquipmentClassId());
        S88ProcedureLogic chart = Levels.chartOf(recipe, "PASO");
        assertTrue(chart.findStep("MIX").isPresent(), "and it has a box on the chart");
        assertTrue(chart.findTransition("T_MIX").isPresent(), "waiting on a bar of its own");
    }

    @Test
    void aStepAddedAfterAnotherTakesOverWhereTheFlowWasGoing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "MIX", "MIX", "T_MIX", "AGITATOR");

        S88ProcedureLogic chart = Levels.chartOf(recipe, "PASO");
        assertEquals(List.of("T_MIX"), targetsOf(chart, "BEGIN"),
                "the flow leaves the start for the new step's bar");
        assertEquals(List.of("T1"), targetsOf(chart, "MIX"),
                "and the new step carries on to the bar it was put in front of");
        assertEquals(List.of("END"), targetsOf(chart, "T1"),
                "and from there to the stop, exactly as before");
    }

    @Test
    void onlyOneBarIsAddedBecauseTheOneThatWasThereIsTheOneToWaitAtNext() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "MIX", "MIX", "T_MIX", "AGITATOR");

        assertEquals(2, Levels.chartOf(recipe, "PASO").getTransitions().size(),
                "the one that was there and the new one. Two more would leave the flow going from a"
                        + " bar to a bar, which a chart cannot have");
    }

    @Test
    void theFlowStillRunsFromTheStartToTheStopAfterAStepIsPutIn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "MIX", "MIX", "T_MIX", "AGITATOR");

        assertTrue(EditProcedureLogicUseCase.isChartBipartite(Levels.chartOf(recipe, "PASO")));
        assertTrue(RecipeConformance.of(recipe).excess().isEmpty(),
                "and nothing is reported wrong, because the step went into the chart its level"
                        + " calls for");
    }

    @Test
    void aStepPutAfterABoxNothingLeavesYetCarriesOnFromTheNewBox() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        // The ordinary case: the operator put a box on the chart and has not joined it up yet.
        addOperation(recipe, "COOL");

        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "COOL", "DRY", "DRY", "T_DRY", "DRYER");

        S88ProcedureLogic chart = Levels.chartOf(recipe, "PASO");
        assertEquals(List.of("T_DRY"), targetsOf(chart, "COOL"),
                "the box that had nothing now waits at the new bar");
        assertTrue(targetsOf(chart, "DRY").isEmpty(),
                "and the new step is the end of the flow, waiting for the operator to carry it on");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(chart));
    }

    @Test
    void aStepPutAfterABoxThatAlreadySplitsGoesInFrontOfTheSplit() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.selectiveFork(recipe, operationOf(recipe), "BEGIN", "T_ONE", "T_TWO");

        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "MIX", "MIX", "T_A", "AGITATOR");

        S88ProcedureLogic chart = Levels.chartOf(recipe, "PASO");
        assertEquals(List.of("T_A"), targetsOf(chart, "BEGIN"),
                "and the flow waits at the new bar");
        assertEquals(List.of("MIX"), targetsOf(chart, "T_A"),
                "and the new step is the one the flow reaches first");
        assertTrue(targetsOf(chart, "MIX").containsAll(List.of("T_ONE", "T_TWO")),
                "and both branches still leave from the split");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(chart),
                "and every line still joins a step and a bar");
    }

    @Test
    void aStepCanBePutIntoTheChosenBranchOfAFork() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        S88RecipeElement operation = operationOf(recipe);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, operation, "BEGIN");
        ChartBranchingUseCase.addAlternativeBranch(recipe, operation, "STEP");

        EditProcedureLogicUseCase.insertStepAfter(recipe, operation, "BEGIN", "MIX", "MIX",
                "T_MIX", "AGITATOR", InsertMode.IN_BRANCH, "T_STEP");

        S88ProcedureLogic chart = Levels.chartOf(recipe, "PASO");
        assertEquals(List.of("MIX"), targetsOf(chart, "T_STEP"),
                "and the chosen branch reaches the new step first");
        assertEquals(List.of("T_MIX"), targetsOf(chart, "MIX"));
        assertEquals(List.of("STEP"), targetsOf(chart, "T_MIX"),
                "and then carries on to the step that was there");
        assertEquals(List.of("STEP_1"), targetsOf(chart, "T_BRANCH"),
                "and the other branch is left as it was");
        assertTrue(ChartSoundness.check(chart).isEmpty(), "and the chart still runs");
    }

    @Test
    void aStepThatCannotBePutAfterChangesNothing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "MIX");
        var chart = Levels.chartOf(recipe, "PASO");
        var before = List.copyOf(chart.getLinks());

        // "MIX" is already a step of the recipe, so this cannot be done. The line that leaves BEGIN
        // used to be taken off first, which left a chart with the flow going nowhere.
        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, operationOf(recipe), "BEGIN", "MIX", "QUENCH", "T_QUENCH", "QUENCHER"));

        assertEquals(before, List.copyOf(chart.getLinks()),
                "a change that was refused may not have taken anything off the chart");
        assertEquals(List.of("T1"), targetsOf(chart, "BEGIN"),
                "and the flow still goes where it went");
    }

    @Test
    void aStepWithABoxNameAlreadyTakenChangesNothing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "MIX");
        var chart = Levels.chartOf(recipe, "PASO");
        var before = List.copyOf(chart.getLinks());

        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, operationOf(recipe), "BEGIN", "QUENCH", "MIX", "T_QUENCH", "QUENCHER"));

        assertEquals(before, List.copyOf(chart.getLinks()),
                "a box name that is taken is refused before the chart is touched");
    }

    @Test
    void aStepCannotBePutAfterABoxThatIsNotThere() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, operationOf(recipe), "NOPE", "MIX", "MIX", "T_A", "AGITATOR"));
    }

    @Test
    void aStepAddedToTheChartIsAlsoAddedToTheRecipe() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "MIX", "MIX", "T_MIX", "AGITATOR");

        assertEquals(3, recipe.getRecipeElements().size(),
                "the start, the new step and the stop");
        assertEquals(S88RecipeElementKind.PHASE, recipe.findElement("MIX").orElseThrow().getKind());
    }

    // ========== Splitting and joining, in the two shapes there are ==========

    /** A step leaving towards two bars is a selective split: one line, two arrivals, one taken. */
    @Test
    void aStepLeavingTowardsTwoBarsIsOneSelectiveLine() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        S88ProcedureLink split = EditProcedureLogicUseCase.selectiveFork(
                recipe, operationOf(recipe), "BEGIN", "T_ONE", "T_TWO");

        assertEquals(List.of("BEGIN"), split.getFrom().stream().map(S88IdRef::getValue).toList());
        assertEquals(List.of("T_ONE", "T_TWO"),
                split.getTo().stream().map(S88IdRef::getValue).toList(),
                "one line with two arrivals, because that is what says only one of them is taken");
        assertEquals(S88LinkType.SERIAL_DIVERGENT, split.getLinkType(),
                "and the type is what says which one, rather than that both are");
        assertFalse(split.getLinkType().isParallel(), "which is the whole difference between them");
        assertEquals(1, linksLeaving(Levels.chartOf(recipe, "PASO"), "BEGIN").stream()
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
                recipe, operationOf(recipe), "T1", "HEAT", "COOL");

        assertEquals(S88LinkType.PARALLEL_DIVERGENT, split.getLinkType());
        assertTrue(split.getLinkType().isParallel(), "both branches run at once");
        assertEquals(List.of("HEAT", "COOL"),
                split.getTo().stream().map(S88IdRef::getValue).toList(),
                "one line going to both steps");
        assertTrue(recipe.findElement("HEAT").isPresent(),
                "and the steps the branches work on are steps of the recipe, or the chart would be"
                        + " naming boxes that work on nothing");
        assertTrue(recipe.findElement("COOL").isPresent(), "both of them");
        assertTrue(Levels.chartOf(recipe, "PASO").findStep("HEAT").orElseThrow()
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
        operationOf(recipe);
        assertEquals(List.of("END"), targetsOf(Levels.chartOf(recipe, "PASO"), "T1"),
                "and the bar does lead somewhere to begin with");

        EditProcedureLogicUseCase.parallelFork(recipe, operationOf(recipe), "T1", "HEAT", "COOL");

        assertEquals(List.of("HEAT", "COOL"),
                targetsOf(Levels.chartOf(recipe, "PASO"), "T1"),
                "so both branches leave it");
        String carriedOn = targetsOf(Levels.chartOf(recipe, "PASO"), "HEAT").get(0);
        assertEquals(carriedOn, targetsOf(Levels.chartOf(recipe, "PASO"), "COOL").get(0),
                "and the two of them come back together on one bar, because two branches that never"
                        + " meet are two recipes rather than one");
        assertEquals(List.of("END"), targetsOf(Levels.chartOf(recipe, "PASO"), carriedOn),
                "and what the bar used to lead to is now behind that, rather than stranded");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(Levels.chartOf(recipe, "PASO")),
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
                recipe, operationOf(recipe), "HEAT", "COOL", "T_BOTH", S88LinkType.PARALLEL_CONVERGENT);

        assertEquals(2, join.getFrom().size(), "one line with two departures");
        assertEquals(List.of("T_BOTH"), join.getTo().stream().map(S88IdRef::getValue).toList());
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, join.getLinkType(),
                "which is what says both are waited for rather than whichever gets there first");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(Levels.chartOf(recipe, "PASO")),
                "and a chart with a join in it is still one where every line crosses a bar");
    }

    /** Two steps joining onto one bar that leads nowhere yet. */
    @Test
    void aJoinOfTwoStepsThatLeadNowhereIsOneLineWaitingForBoth() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "HEAT");
        addOperation(recipe, "COOL");

        EditProcedureLogicUseCase.joinOnto(
                recipe, operationOf(recipe), "HEAT", "COOL", "T_BOTH", S88LinkType.PARALLEL_CONVERGENT);

        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "HEAT",
                "and each of them now leads into the join", "T_BOTH");
        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "COOL",
                "and the other one too", "T_BOTH");
        assertTrue(targetsOf(Levels.chartOf(recipe, "PASO"), "T_BOTH").isEmpty(),
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
                        recipe, operationOf(recipe), "MIX", "MIX", "T_X", null));

        assertTrue(failure.getMessage().contains("itself"),
                "a join of one box with itself is a box with nowhere to go");
    }

    /** Every split the module can make has to come out as a chart the drawing can read. */
    @Test
    void everySplitTheModuleCanMakeIsStillAReadableChart() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.selectiveFork(recipe, operationOf(recipe), "BEGIN", "T_A", "T_B");

        assertTrue(EditProcedureLogicUseCase.isChartBipartite(Levels.chartOf(recipe, "PASO")),
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
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "QUENCH", "QUENCH", "T_QUENCH",
                "QUENCH_CLASS");

        EditProcedureLogicUseCase.renameStep(recipe, operationOf(recipe), "QUENCH", "COOL");

        assertTrue(Levels.chartOf(recipe, "PASO").findStep("QUENCH").isEmpty(),
                "and the old name is not left behind as a second box");
        assertTrue(Levels.chartOf(recipe, "PASO").findStep("COOL").isPresent(),
                "and the chart finds it under the new name");
        assertTrue(recipe.findElement("COOL").isPresent(),
                "and the step itself is called that now, so the tree and the chart agree");
        assertTrue(recipe.findElement("QUENCH").isEmpty(),
                "rather than the box and the step it works on drifting apart");
        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "T_QUENCH",
                "and the line into it follows, because a line naming a box that is not there is not"
                        + " a chart anybody can draw", "COOL");
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(Levels.chartOf(recipe, "PASO")));
    }

    @Test
    void theBeginningAndTheEndOfAChartKeepTheirNames() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.renameStep(recipe, operationOf(recipe), "BEGIN",
                        "START"));

        assertTrue(failure.getMessage().contains("beginning of a chart"),
                "and it says why, rather than giving it the name of a chart it no longer starts");
        assertTrue(Levels.chartOf(recipe, "PASO").findStep("BEGIN").isPresent(),
                "and the chart still starts where it started");
    }

    @Test
    void aBarIsGivenAnotherNameAndTheLinesIntoItFollow() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.renameTransition(recipe, operationOf(recipe), "T1", "WHEN_HOT");

        assertTrue(Levels.chartOf(recipe, "PASO").findTransition("T1").isEmpty());
        assertEquals(List.of("WHEN_HOT"), targetsOf(Levels.chartOf(recipe, "PASO"), "BEGIN"),
                "and the line leaving the box before it follows it");
        assertEquals(List.of("END"), targetsOf(Levels.chartOf(recipe, "PASO"), "WHEN_HOT"),
                "and so does the line leaving it");
    }

    @Test
    void aStepCannotBeGivenANameSomethingElseAlreadyHas() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "QUENCH", "QUENCH", "T_QUENCH",
                "QUENCH_CLASS");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.renameStep(recipe, operationOf(recipe), "QUENCH",
                        "T1"));

        assertTrue(failure.getMessage().contains("already has a step"),
                "and it says which, rather than swapping two steps behind the operator's back");
    }

    @Test
    void aBoxThatIsStillJoinedCannotBeTakenOff() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.removeStep(recipe, operationOf(recipe), "BEGIN"));

        assertTrue(failure.getMessage().contains("Take those off first"),
                "and it says what to do about it, because taking the box would leave lines with"
                        + " nothing at one end");
    }

    @Test
    void aBarThatIsStillJoinedCannotBeTakenOff() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.removeTransition(recipe, operationOf(recipe), "T1"));

        assertTrue(failure.getMessage().contains("Take those off first"),
                "a bar is no different from a box here, and taking one would strand its lines");
    }

    @Test
    void aBarNothingIsJoinedToIsTakenOffAndItSaysSo() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.addTransition(recipe, operationOf(recipe), "T_SPARE");

        assertTrue(EditProcedureLogicUseCase.removeTransition(recipe, operationOf(recipe), "T_SPARE"));
        assertFalse(EditProcedureLogicUseCase.removeTransition(recipe, operationOf(recipe), "T_SPARE"),
                "and asking again says there was no such bar rather than failing");
    }

    // ========== What a bar waits on ==========

    @Test
    void aBarIsGivenWhatItWaitsOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.setCondition(recipe, operationOf(recipe), "T1", "Reports/STATE#=#COMPLETE");

        S88ProcedureTransition bar = Levels.chartOf(recipe, "PASO").findTransition("T1").orElseThrow();
        assertTrue(bar.isGuarded(), "so it is not crossed straight away");
        assertEquals("Reports/STATE#=#COMPLETE", bar.getCondition(),
                "and the text it carries is the text that was asked for");
    }

    @Test
    void aBarCanBeGivenNothingToWaitOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.setCondition(recipe, operationOf(recipe), "T1", "Reports/STATE#=#COMPLETE");

        EditProcedureLogicUseCase.setCondition(recipe, operationOf(recipe), "T1", "  ");

        S88ProcedureTransition bar = Levels.chartOf(recipe, "PASO").findTransition("T1").orElseThrow();
        assertFalse(bar.isGuarded(), "and then it is crossed as soon as the step before is done");
    }

    @Test
    void somethingThatIsNotAComparisonIsRefused() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.setCondition(recipe, operationOf(recipe), "T1", "just some words"));

        assertTrue(failure.getMessage().contains("ADDRESS"),
                "and it says how a comparison is written, because a bar that waits on something"
                        + " nobody can read is a recipe nobody can run");
    }

    /** The step whose chart these tests draw on, made if it is not there. */
    private static S88RecipeElement operationOf(S88MasterRecipe recipe) {
        if (recipe.findElement("TRAMO").isEmpty()) {
            Levels.unitProcedure(recipe, "TRAMO", null);
        }
        if (recipe.findElement("PASO").isEmpty()) {
            Levels.operation(recipe, "TRAMO", "PASO");
        }
        return Levels.owner(recipe, "PASO");
    }

    /** A box on the chart working on a step of the recipe of the same name. */
    private static void addOperation(S88MasterRecipe recipe, String id) {
        S88RecipeElement operation = operationOf(recipe);
        Levels.phase(recipe, "PASO", id, "MACHINE");
        EditProcedureLogicUseCase.addStep(recipe, operation, id, id);
    }

    // ========== Joining two steps ==========

    @Test
    void joiningTwoStepsPutsATransitionOfItsOwnBetweenThem() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "COOL");
        addOperation(recipe, "DRY");
        S88ProcedureLogic chart = Levels.chartOf(recipe, "PASO");

        S88ProcedureTransition made = EditProcedureLogicUseCase.joinStepsThroughATransition(
                recipe, operationOf(recipe), "COOL", "DRY");

        assertEquals(List.of(made.getId()), targetsOf(chart, "COOL"),
                "the join leaves the first step for a transition of its own");
        assertEquals(List.of("DRY"), targetsOf(chart, made.getId()),
                "and that transition arrives at the second step");
        assertNull(made.getCondition(), "which starts with nothing to wait on, drawn as TRUE");
    }

    @Test
    void aStepCannotBeJoinedToItself() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "COOL");

        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.joinStepsThroughATransition(
                        recipe, operationOf(recipe), "COOL", "COOL"));
    }

    @Test
    void aSecondJoinBetweenTheSameTwoStepsGetsNamesOfItsOwn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        addOperation(recipe, "COOL");
        addOperation(recipe, "DRY");

        S88ProcedureTransition first = EditProcedureLogicUseCase.joinStepsThroughATransition(
                recipe, operationOf(recipe), "COOL", "DRY");
        S88ProcedureTransition second = EditProcedureLogicUseCase.joinStepsThroughATransition(
                recipe, operationOf(recipe), "COOL", "DRY");

        assertNotEquals(first.getId(), second.getId(),
                "the second join gets a transition name of its own");
    }

    /**
     * The step goes between the bar it waits at and the bar the flow used to reach.
     *
     * <p>
     * Written as a whole because the shape is the thing: a box, a bar, a box, and the bar that was
     * already there. A chain that leaves the new bar with nothing after it is a chart where the flow
     * stops there, and the step the operator just added is somewhere the flow never goes.
     */
    @Test
    void aStepPutAfterABoxGoesBetweenTheBarItWaitsAtAndTheOneItUsedToReach() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.insertStepAfter(
                recipe, operationOf(recipe), "BEGIN", "QUENCH", "QUENCH", "T_QUENCH", "QUENCH_CLASS");

        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "BEGIN", "and it waits at a new bar", "T_QUENCH");
        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "T_QUENCH",
                "and the new bar is crossed into the new step", "QUENCH");
        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "QUENCH",
                "and the new step leads on to the bar that was already there", "T1");
        assertLeadsOnlyTo(Levels.chartOf(recipe, "PASO"), "T1", "and the flow goes on as before", "END");
    }

    /** A step that works on nothing has nothing to do after the stop of the process. */
    @Test
    void aStepCannotBePutAfterTheEndOfTheProcess() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        recipe, operationOf(recipe), "END", "QUENCH", "QUENCH", "T_QUENCH", "QUENCH_CLASS"));

        assertTrue(failure.getMessage().contains("stops"),
                "and it says why, because a step after the stop is a process with a second end");
        assertEquals(4, recipe.getAllElements().size(),
                "and nothing was added, because the refusal comes before anything is touched");
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

    /**
     * The lines leaving a box or a bar, in the order the chart holds them.
     */
    private static List<S88ProcedureLink> linksLeaving(S88ProcedureLogic chart, String name) {
        List<S88ProcedureLink> leaving = new java.util.ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> name.equals(end.getValue()))) {
                leaving.add(link);
            }
        }
        return leaving;
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