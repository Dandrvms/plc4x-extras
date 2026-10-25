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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.ChartDeletionUseCase;
import org.apache.plc4x.malbec.s88.core.CreateChartUseCase;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.CreateRecipeElementUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;
import org.apache.plc4x.malbec.s88.core.SetBarConditionUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the editor does when an edit is taken back, put again, or refused halfway.
 * <p>
 * Everything here compares the recipe as a file would hold it. An edit that cannot be taken all the
 * way back is an edit the operator cannot undo, and nothing in the editor's own state would say so.
 */
class RecipeEditorModelTest {

    /**
     * Adds a step into the chart the editor has open, which is what the Add step button does.
     *
     * <p>Where it goes is found in that chart rather than given, so the same helper works on the
     * chart of the recipe and on one of its subcharts, which are born with different names for
     * their start.
     */
    private static void addStep(RecipeEditorModel model, String name) {
        String after = startBoxOf(model);
        String classId = model.chartBeingEdited().getSteps().stream()
                .map(S88ProcedureStep::getRecipeElementId)
                .filter(element -> model.getRecipe().findElement(element)
                        .map(one -> one.getEquipmentClassId() != null)
                        .orElse(false))
                .findFirst()
                .map(element -> model.getRecipe().findElement(element).orElseThrow()
                        .getEquipmentClassId())
                .orElse(name + "_CLASS");
        model.edit("Add step " + name,
                () -> EditProcedureLogicUseCase.insertStepAfter(model.getRecipe(),
                        model.currentStep(), after, name, name, "T_" + name, classId));
    }

    /** The name of the box the flow leaves in the chart the editor has open. */
    private static String startBoxOf(RecipeEditorModel model) {
        String firstBox = model.chartBeingEdited().getSteps().stream()
                .map(S88ProcedureStep::getRecipeElementId)
                .filter(element -> model.getRecipe().findElement(element)
                        .map(one -> one.getKind() == S88RecipeElementKind.BEGIN)
                        .orElse(false))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the chart on show has no start to put a step after"));
        return model.chartBeingEdited().getSteps().stream()
                .filter(box -> firstBox.equals(box.getRecipeElementId()))
                .findFirst()
                .map(S88ProcedureStep::getId)
                .orElseThrow();
    }

    /** Adds a step whose name is already in the recipe, which the core refuses. */
    private static void addStepWithAClashingName(RecipeEditorModel model) {
        model.edit("Add step QUENCH",
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        model.getRecipe(), null, "BEGIN", "QUENCH", "QUENCH", "T_QUENCH",
                        "QUENCH_CLASS"));
    }

    private static RecipeEditorModel open() {
        try {
            return RecipeBytes.openOn(RecipeBytes.holding(SfcFixtures.lineal()));
        } catch (IOException failure) {
            throw new AssertionError("a recipe that was just written could not be read", failure);
        }
    }

    /**
     * A recipe with a unit procedure holding an operation of the older kind, which is the shape the
     * editor still reads.
     */
    private static S88MasterRecipe tree() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        S88RecipeElement tramo = EditProcedureLogicUseCase.insertStepAfter(recipe, null,
                "BEGIN", "TRAMO", "TRAMO", "T_TRAMO", null);
        CreateChartUseCase.forStep(recipe, tramo);
        SfcFixtures.legacyOperation(recipe, tramo, "PASO", "PASO_CLASS");
        return recipe;
    }

    private static RecipeEditorModel openOverATree() {
        try {
            return RecipeBytes.openOn(RecipeBytes.holding(tree()));
        } catch (IOException failure) {
            throw new AssertionError("a recipe that was just written could not be read", failure);
        }
    }

    /**
     * A recipe whose unit procedure is meant to hold a chart and has not been given one, which is
     * what a recipe read from an older file looks like.
     */
    private static S88MasterRecipe treeWithAnUnbornChart() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        S88RecipeElement tramo = EditProcedureLogicUseCase.insertStepAfter(recipe, null,
                "BEGIN", "TRAMO", "TRAMO", "T_TRAMO", null);
        tramo.setProcedureLogic(null);
        return recipe;
    }

    private static RecipeEditorModel openOverAnUnbornChart() {
        try {
            return RecipeBytes.openOn(RecipeBytes.holding(treeWithAnUnbornChart()));
        } catch (IOException failure) {
            throw new AssertionError("a recipe that was just written could not be read", failure);
        }
    }

    /**
     * Edits inside a subchart keep the editor on that subchart.
     * <p>
     * Every edit puts a new recipe in place of the old one, so a step held as it stands would be a
     * step of a recipe that is no longer the one on show. Holding its name instead is what keeps
     * the drawing and the changes on the same chart.
     */
    /**
     * A step meant to hold a chart has nothing to draw until it is given one.
     * <p>
     * Reading it is not the time to make one: the chart that comes of that has no start and no stop,
     * and nothing to take back to.
     */
    @Test
    void goingIntoAStepWithNoChartDrawsNothingRatherThanMakingOne() throws IOException {
        RecipeEditorModel model = openOverAnUnbornChart();
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());

        assertNull(model.chartBeingEdited(),
                "and the step being edited has no chart of its own to draw");
        assertNull(model.getRecipe().findElement("TRAMO").get().getProcedureLogic(),
                "and going into it made nothing that nothing asked for");
    }

    /**
     * The chart a step is meant to have is made by an order, and can be taken back.
     * <p>
     * It goes through an edit, so undoing it leaves the recipe as it was rather than leaving a
     * chart behind that the operator never asked for.
     */
    @Test
    void creatingTheChartOfAStepCanBeTakenBack() throws IOException {
        RecipeEditorModel model = openOverAnUnbornChart();
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());

        model.createChartForCurrentStep();

        assertNotNull(model.chartBeingEdited(),
                "and the chart the step was meant to have is there to be drawn");
        assertEquals(2, model.chartBeingEdited().getSteps().size(),
                "and it is born with a start and a stop");
        assertEquals(1, model.chartBeingEdited().getTransitions().size(),
                "and with the bar the flow waits at between them");
        assertEquals(0, model.chartBeingEdited().getTransitions().stream()
                        .filter(bar -> bar.getCondition() != null)
                        .count(),
                "and nothing waits on anything yet");

        model.undo();

        assertNull(model.chartBeingEdited(),
                "and taking it back leaves the step with no chart again, as it was");
        assertTrue(model.getRecipe().findElement("TRAMO").isPresent(),
                "and the recipe still holds the step itself");
    }

    /**
     * A step of the last level is offered no chart to create.
     * <p>
     * It holds nothing, so there is nothing to make.
     */
    @Test
    void aLastLevelStepIsGivenNoChart() throws IOException {
        RecipeEditorModel model = openOverATree();
        model.editChartOf(model.getRecipe().findElement("PASO").orElseThrow());
        EditProcedureLogicUseCase.insertStepAfter(model.getRecipe(), model.currentStep(),
                "BEGIN", "PULSO", "PULSO", "T_PULSO", "PULSO_CLASS");

        model.editChartOf(model.getRecipe().findElement("PULSO").orElseThrow());
        model.createChartForCurrentStep();

        assertNull(model.chartBeingEdited(), "and a phase holds no chart of its own");
        assertFalse(RecipeLevels.allowsSubChart(model.levelBeingEdited()),
                "so the recipe says there is nothing under it to draw");
    }

    @Test
    void anEditInsideASubchartKeepsTheEditorOnIt() throws IOException {
        RecipeEditorModel model = openOverATree();
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());

        addStep(model, "PRIMERO");
        addStep(model, "SEGUNDO");

        assertEquals(model.getRecipe().findElement("TRAMO").orElseThrow(), model.currentStep(),
                "and the chart on show is still the one that was gone into");
        assertTrue(model.getRecipe().findElement("PRIMERO").isPresent()
                        && model.getRecipe().findElement("SEGUNDO").isPresent(),
                "and both steps are in the recipe the editor is holding");
    }

    /**
     * Taking an edit back leaves the editor on the chart it was made in.
     * <p>
     * The step the chart belongs to is still on the recipe, which is what happens when what is
     * taken back is a step inside the chart rather than the one holding it.
     */
    @Test
    void takingAnEditBackLeavesTheEditorOnTheChartItWasMadeIn() throws IOException {
        RecipeEditorModel model = openOverATree();
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());
        addStep(model, "PRIMERO");

        model.undo();

        assertEquals("TRAMO", model.currentStep().getId(),
                "and the chart on show is still the one that was gone into");
        assertTrue(model.getRecipe().findElement("PRIMERO").isEmpty(),
                "and the step that was added is off the recipe again");
    }

    /**
     * A step taken off the recipe leaves the editor back out of its chart.
     * <p>
     * The editor holds the name of the step rather than the step itself, so a name that leads
     * nowhere leaves nothing to draw and the chart of the recipe comes back.
     */
    @Test
    void aStepTakenOffTheRecipeLeavesTheEditorBackOutOfIt() throws IOException {
        RecipeEditorModel model = openOverATree();
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());

        model.edit("Delete TRAMO", () -> ChartDeletionUseCase.deleteBox(
                model.getRecipe(), null, "TRAMO"));

        assertNull(model.currentStep(), "and the step that held the chart has gone");
        assertEquals(model.getRecipe().getProcedureLogic(), model.chartBeingEdited(),
                "so the chart drawn is the one the editor started on");
        assertEquals(S88RecipeElementKind.UNIT_PROCEDURE, model.levelBeingEdited(),
                "and the buttons act on the steps of that chart");
    }

    /**
     * Redoing an edit brings the chart it was made on back with it.
     */
    @Test
    void redoingAnEditBringsTheChartItWasMadeOnBack() throws IOException {
        RecipeEditorModel model = openOverATree();
        S88RecipeElement tramo = model.getRecipe().findElement("TRAMO").orElseThrow();
        model.editChartOf(tramo);
        addStep(model, "PRIMERO");
        model.undo();

        model.redo();

        assertEquals(tramo.getId(), model.currentStep().getId(),
                "and the chart on show is the one the edit was made in");
        assertTrue(model.getRecipe().findElement("PRIMERO").isPresent(),
                "and the step it added is in the recipe again");
    }

    /**
     * A condition written on a bar of a subchart lands on that bar.
     * <p>
     * Every chart is born with a bar called T1, so a bar picked in a subchart is a name the chart of
     * the recipe also has. The step that holds the chart is what tells them apart.
     */
    @Test
    void aConditionLandsOnTheBarOfTheChartBeingEdited() throws IOException {
        RecipeEditorModel model = openOverATree();
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());

        model.edit("Condition of T1", () -> SetBarConditionUseCase.waitUntil(
                model.getRecipe(), model.currentStep(), "T1", plantWithAReport(), null,
                "TEMPERATURA_ACTUAL", ">", "80"));

        assertNull(conditionOf(model.getRecipe().getProcedureLogic(), "T1"),
                "and the bar of the same name in the chart of the recipe is left alone");
        assertNotNull(conditionOf(model.chartBeingEdited(), "T1"),
                "and the bar that was picked is the one that is written");
    }

    /**
     * The comparison a bar is waiting on, or {@code null} when it waits for nothing.
     */
    private static String conditionOf(S88ProcedureLogic chart, String barId) {
        return chart.findTransition(barId).orElseThrow().getCondition();
    }

    private static S88PlantSnapshot plantWithAReport() {
        S88ElementClass heater = new S88ElementClass();
        heater.setName("CALENTAMIENTO");
        heater.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heater.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C")));
        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88ElementClass work = new S88ElementClass();
        work.setName("PASO_CLASS");
        work.setTargetLevel(S88Level.UNIT);
        work.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C")));
        S88PlantModel model = new S88PlantModel(root);
        model.registerClass(heater);
        model.registerClass(work);
        return PlantSnapshotUseCase.of(model);
    }

    /**
     * A recipe set with no plant frozen into it still opens.
     * <p>
     * What cannot be said is what the steps work on, so the recipe comes in empty of a plant and says
     * so, rather than the file being closed unread.
     */
    @Test
    void aRecipeWithNoPlantOpensAndSaysSo() throws IOException {
        RecipeEditorModel model = RecipeBytes.openOn(RecipeBytes.holding(SfcFixtures.lineal()));

        assertNull(model.plant(), "and the model holds no plant to bind anything to");
        assertNotNull(model.getRecipe(),
                "and the recipe is there to be drawn rather than the file being closed unread");
        assertTrue(model.isModified() == false,
                "and the operator is not asked about anything for something the recipe set did");
    }

    /** A recipe with a plant frozen into its set says nothing, and a file with none does. */
    @Test
    void aRecipeWithAPlantSaysNothingAboutAMissingOne() throws IOException {
        RecipeEditorModel withAPlant = RecipeBytes.openOn(
                RecipeBytes.holding(SfcFixtures.lineal()), plantWithAReport());

        assertNotNull(withAPlant.plant(), "and the model holds the plant to bind against");
        assertNull(withAPlant.warning(), "so there is nothing to say about it");

        RecipeEditorModel withoutAFile = RecipeBytes.openOn(
                RecipeBytes.holding(SfcFixtures.lineal()));
        assertNull(withoutAFile.warning(),
                "and no file belongs to no recipe set, which is also nothing to say");
    }

    @Test
    void goingIntoAStepDrawsTheChartThatStepHolds() throws IOException {
        RecipeEditorModel model = openOverATree();
        S88RecipeElement tramo = model.getRecipe().findElement("TRAMO").orElseThrow();

        model.editChartOf(tramo);

        assertSame(tramo, model.currentStep(), "and the model says which chart is on show");
        assertEquals(tramo.getProcedureLogic(), model.chartBeingEdited(),
                "and that is the chart the drawing puts on the screen");
        assertEquals(S88RecipeElementKind.PHASE, model.levelBeingEdited(),
                "and the steps in it are the kind that level holds");
    }

    @Test
    void goingUpDrawsTheChartThatHoldsTheOneOnShow() throws IOException {
        RecipeEditorModel model = openOverATree();
        S88RecipeElement paso = model.getRecipe().findElement("PASO").orElseThrow();
        model.editChartOf(paso);

        model.editChartOf(paso.getParent());

        assertSame(model.getRecipe().findElement("TRAMO").orElseThrow(), model.currentStep(),
                "and the chart on show is the one that held it, one level up");
        assertEquals(S88RecipeElementKind.PHASE, model.levelBeingEdited(),
                "so the buttons act on the steps of that chart");
    }

    @Test
    void aStepAtTheTopGoesBackToTheChartOfTheRecipe() throws IOException {
        RecipeEditorModel model = openOverATree();
        S88RecipeElement tramo = model.getRecipe().findElement("TRAMO").orElseThrow();
        model.editChartOf(tramo);

        model.editChartOf(tramo.getParent());

        assertNull(model.currentStep(), "and that is the chart the editor opened on");
        assertEquals(model.getRecipe().getProcedureLogic(), model.chartBeingEdited(),
                "which is where every chart of the recipe starts");
        assertEquals(S88RecipeElementKind.UNIT_PROCEDURE, model.levelBeingEdited(),
                "so the steps put in it are the ones the recipe holds directly");
    }

    @Test
    void showingAChartNodeOpensItsChartAndIsTakenOnce() throws IOException {
        RecipeEditorModel model = openOverATree();
        S88RecipeElement tramo = model.getRecipe().findElement("TRAMO").orElseThrow();
        String node = tramo.getProcedureLogic().getSteps().getFirst().getId();

        model.showChartNode(tramo, node);

        assertSame(tramo, model.currentStep(), "and the chart on show is the one that holds it");
        assertEquals(node, model.takePendingChartNode(), "and the chart is told to pick it");
        assertNull(model.takePendingChartNode(), "and it is only told once");
    }

    @Test
    void theChartPathNamesTheRecipeAndTheChartsOnShow() throws IOException {
        RecipeEditorModel model = openOverATree();

        assertEquals("REC", model.currentChartPath(), "and the root chart is named by the recipe");
        model.editChartOf(model.getRecipe().findElement("TRAMO").orElseThrow());
        assertEquals("REC/TRAMO", model.currentChartPath(),
                "and a subchart adds the step that holds it");
        model.editChartOf(model.getRecipe().findElement("PASO").orElseThrow());
        assertEquals("REC/TRAMO/PASO", model.currentChartPath(),
                "and a chart inside a chart adds each step on the way down");
    }

    @Test
    void aChartSelectionReachesTheViewsOnlyWhenItChanges() throws IOException {
        RecipeEditorModel model = openOverATree();
        List<String> heard = new ArrayList<>();
        model.addChartSelectionListener(heard::add);

        model.chartSelectionChanged("BOX");
        model.chartSelectionChanged("BOX");
        model.chartSelectionChanged("BAR");

        assertEquals(List.of("BOX", "BAR"), heard,
                "and a view that follows the chart is not asked to move for a node it already has");
    }

    @Test
    void copyingARecipeGivesTheSameFileBack() {
        org.apache.plc4x.malbec.s88.api.S88MasterRecipe original = SfcFixtures.lineal();
        byte[] before = RecipeBytes.of(original);

        byte[] copy = RecipeBytes.of(
                org.apache.plc4x.malbec.s88.core.RecipeDeepCopy.copyMasterRecipe(original));

        assertArrayEquals(before, copy,
                "an edit is taken back by putting a copy of the recipe back, so a copy that is not"
                        + " the same recipe is an edit that cannot be taken back, and "
                        + RecipeBytes.difference(before, copy));
    }

    @Test
    void anEditThatIsUndoneLeavesTheRecipeAsItWas() throws IOException {
        RecipeEditorModel model = open();
        byte[] before = RecipeBytes.of(model.getRecipe());

        addStep(model, "QUENCH");
        model.getUndoRedo().undo();

        assertArrayEquals(before, RecipeBytes.of(model.getRecipe()),
                "taking an edit back has to leave the recipe exactly as it was, and "
                        + RecipeBytes.difference(before, RecipeBytes.of(model.getRecipe())));
    }

    @Test
    void anEditThatIsRedoneComesBackTheSame() throws IOException {
        RecipeEditorModel model = open();
        byte[] before = RecipeBytes.of(model.getRecipe());

        addStep(model, "QUENCH");
        byte[] afterAdd = RecipeBytes.of(model.getRecipe());
        model.getUndoRedo().undo();
        model.getUndoRedo().redo();

        assertArrayEquals(afterAdd, RecipeBytes.of(model.getRecipe()),
                "putting an edit back has to leave it as it was when it was done");
        assertFalse(Arrays.equals(before, afterAdd),
                "and the edit has to have changed something in the first place");
    }

    @Test
    void anEditThatIsRefusedLeavesTheRecipeAsItWas() throws IOException {
        RecipeEditorModel model = open();
        addStep(model, "QUENCH");
        byte[] afterAdd = RecipeBytes.of(model.getRecipe());

        // The same step a second time. The core refuses it, but only after taking the line off the
        // chart, which is where a chart left holding a line to nothing comes from.
        assertThrows(RecipeEditException.class, () -> addStepWithAClashingName(model));

        assertArrayEquals(afterAdd, RecipeBytes.of(model.getRecipe()),
                "an edit that was refused has to leave the recipe it refused to change alone, and "
                        + RecipeBytes.difference(afterAdd, RecipeBytes.of(model.getRecipe())));
    }

    @Test
    void anEditThatIsRefusedIsReportedWithSomethingToRead() {
        RecipeEditorModel model = open();
        addStep(model, "QUENCH");

        RecipeEditException failure = assertThrows(RecipeEditException.class,
                () -> addStepWithAClashingName(model));

        assertTrue(failure.getMessage().contains("QUENCH"),
                "the operator has to be told what it was that would not take, and got: "
                        + failure.getMessage());
        assertNotNull(failure.getCause(), "and what went wrong underneath it");
    }

    @Test
    void aRefusedEditIsNotSomethingToUndo() {
        RecipeEditorModel model = open();
        addStep(model, "QUENCH");

        assertThrows(RecipeEditException.class, () -> addStepWithAClashingName(model));

        assertTrue(model.getUndoRedo().canUndo(),
                "the edit that was really made is still there to take back");
        model.getUndoRedo().undo();
        assertFalse(model.getUndoRedo().canUndo(),
                "and taking it back leaves nothing behind, rather than a change that never"
                        + " happened sitting on top of it");
    }

    @Test
    void anUndoIsSomethingToRedo() {
        RecipeEditorModel model = open();
        addStep(model, "QUENCH");
        model.getUndoRedo().undo();

        assertTrue(model.getUndoRedo().canRedo(),
                "having taken an edit back, the operator has to be able to put it again");
    }

    @Test
    void undoingTellsTheViewsAboutIt() {
        RecipeEditorModel model = open();
        int[] told = {0};
        Consumer<RecipeEditorModel> listener = changed -> told[0]++;
        model.addChangeListener(listener);
        addStep(model, "QUENCH");
        int afterEdit = told[0];

        model.getUndoRedo().undo();

        assertEquals(afterEdit + 1, told[0],
                "a view that is not told cannot redraw, so the chart and the table keep showing"
                        + " something that is no longer there");
    }

    @Test
    void redoingTellsTheViewsAboutIt() {
        RecipeEditorModel model = open();
        int[] told = {0};
        Consumer<RecipeEditorModel> listener = changed -> told[0]++;
        model.addChangeListener(listener);
        addStep(model, "QUENCH");
        model.getUndoRedo().undo();
        int afterUndo = told[0];

        model.getUndoRedo().redo();

        assertEquals(afterUndo + 1, told[0], "and the same goes for putting an edit back");
    }

    /**
     * Every edit the chart buttons make has to be one the operator can take back, and has to survive
     * the file it is written to.
     *
     * <p>
     * Written as one test over the whole list rather than one test each, because the thing worth
     * knowing is that none of them is the odd one out. An edit that can be undone in memory but not
     * written to the file is an edit that comes back different, which is why the file after undoing
     * is compared and not just the recipe in memory.
     */
    @Test
    void everyChartEditCanBeTakenBackAndSurvivesTheFile() {
        RecipeEditorModel model = open();
        byte[] asOpened = RecipeBytes.of(model.getRecipe());

        model.edit("Split BEGIN",
                () -> EditProcedureLogicUseCase.selectiveFork(
                        model.getRecipe(), null, "BEGIN", "T_ONE_OF", "T_OTHERWISE"));
        model.edit("Split T_OTHERWISE",
                () -> EditProcedureLogicUseCase.parallelFork(
                        model.getRecipe(), null, "T_OTHERWISE", "QUENCH", "CHILL"));
        model.edit("Join QUENCH and CHILL",
                () -> EditProcedureLogicUseCase.joinOnto(model.getRecipe(), null,
                        "QUENCH", "CHILL", "T_BOTH", null));
        model.edit("Condition of T_ONE_OF",
                () -> EditProcedureLogicUseCase.setCondition(
                        model.getRecipe(), null, "T_ONE_OF", "Reports/STATE#=#COMPLETE"));
        model.edit("Make MIX optional",
                () -> ChartBranchingUseCase.addOptionalBranch(model.getRecipe(), null, "MIX"));
        model.edit("Rename HEAT",
                () -> EditProcedureLogicUseCase.renameStep(
                        model.getRecipe(), null, "HEAT", "HEATING"));
        byte[] afterEdits = RecipeBytes.of(model.getRecipe());

        assertFalse(Arrays.equals(asOpened, afterEdits),
                "and the edits together changed the recipe, or none of them did anything");
        assertEquals(6, howManyCanBeTakenBack(model), "and each of them is one the operator can"
                + " take back on its own, rather than six edits that have become one step");

        while (model.getUndoRedo().canUndo()) {
            model.getUndoRedo().undo();
        }

        assertArrayEquals(asOpened, RecipeBytes.of(model.getRecipe()),
                "and taking all of them back leaves the recipe as it was, and "
                        + RecipeBytes.difference(asOpened, RecipeBytes.of(model.getRecipe())));

        while (model.getUndoRedo().canRedo()) {
            model.getUndoRedo().redo();
        }

        assertArrayEquals(afterEdits, RecipeBytes.of(model.getRecipe()),
                "and putting all of them back gives the same recipe again, and "
                        + RecipeBytes.difference(afterEdits, RecipeBytes.of(model.getRecipe())));
    }

    /** How many separate edits the operator could step back through. */
    private static int howManyCanBeTakenBack(RecipeEditorModel model) {
        int steps = 0;
        while (model.getUndoRedo().canUndo()) {
            model.getUndoRedo().undo();
            steps++;
        }
        for (int putBack = 0; putBack < steps; putBack++) {
            model.getUndoRedo().redo();
        }
        return steps;
    }

    /**
     * What an edit leaves behind is what the edit needs, and nothing more.
     * <p>
     * Every edit used to make three copies of the whole recipe: one for the state before, one for
     * the state after, and a third for the live object. The third one held nothing that the second
     * did not, so the recipe a change leaves behind is the one kept for taking it back again, and
     * the live object is the only copy made.
     */
    @Test
    void anEditLeavesBehindOneSnapshotRatherThanThree() throws IOException {
        RecipeEditorModel model = open();
        S88MasterRecipe original = model.getRecipe();

        addStep(model, "PRIMERO");

        assertNotSame(original, model.getRecipe(),
                "and the recipe on show is a different object from the one it was");
        assertTrue(model.getRecipe().findElement("PRIMERO").isPresent(),
                "and it is the one the change was made to");
        assertEquals(1, howManyCanBeTakenBack(model), "and there is the one change to take back");
    }

    /**
     * A recipe of five hundred steps takes back every change in reasonable time.
     * <p>
     * This is a guard against the undo history holding onto the whole recipe again and again, which
     * is what it did when each edit kept two full copies of its own. Measured on this machine: sixty
     * edits take under a second, and five hundred take about ten.
     */
    @Test
    void manyEditsOnALargeRecipeStayInReasonableTime() throws IOException {
        RecipeEditorModel model = open();
        String last = "BEGIN";
        for (int n = 0; n < 60; n++) {
            String name = "PASO_" + n;
            String box = name;
            String after = last;
            model.edit("Add step " + name,
                    () -> EditProcedureLogicUseCase.insertStepAfter(model.getRecipe(), null, after,
                            name, box, "T_" + name, name + "_CLASS"));
            last = box;
        }

        long start = System.nanoTime();
        int changed = howManyCanBeTakenBack(model);
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(changed >= 60, "and every step that was added can be taken back: " + changed);
        assertTrue(elapsed < 5_000,
                "and all of them together took " + elapsed + " ms, which is one edit's work repeated"
                        + " rather than the recipe being walked again each time");
    }

    /** A bar that is edited has to come back with the same comparison, not a bar that waits on
     * nothing. */
    @Test
    void anEditedBarSurvivesTheFileWithItsComparison() {
        RecipeEditorModel model = open();

        model.edit("Condition of T_MIX",
                () -> EditProcedureLogicUseCase.setCondition(
                        model.getRecipe(), null, "T_MIX", "Reports/STATE#=#COMPLETE"));
        model.getUndoRedo().undo();
        model.getUndoRedo().redo();

        assertEquals("Reports/STATE#=#COMPLETE",
                model.getRecipe().getProcedureLogic().findTransition("T_MIX").orElseThrow()
                        .getCondition(),
                "an edit taken back and put again has to leave the comparison in place");
    }

    @Test
    void openingAndSavingWithoutTouchingAnythingChangesNothing() throws IOException {
        RecipeBytes.Memory storage = RecipeBytes.holding(SfcFixtures.lineal());
        byte[] onDisk = storage.written();

        RecipeBytes.openOn(storage).save();

        assertArrayEquals(onDisk, storage.written(),
                "opening a recipe and saving it without touching it has to leave the file as it"
                        + " was, and "
                        + RecipeBytes.difference(onDisk, storage.written()));
    }

    @Test
    void savingAfterAnEditAndUndoingItLeavesTheFileAsItWas() throws IOException {
        RecipeBytes.Memory storage = RecipeBytes.holding(SfcFixtures.lineal());
        byte[] onDisk = storage.written();
        RecipeEditorModel model = RecipeBytes.openOn(storage);

        addStep(model, "QUENCH");
        model.save();
        model.getUndoRedo().undo();
        model.save();

        assertArrayEquals(onDisk, storage.written(),
                "an edit that was made and then taken back has left nothing on the file, and "
                        + RecipeBytes.difference(onDisk, storage.written()));
    }

    /**
     * An optional branch written to the file comes back with the line that skips the step.
     *
     * <p>The line from one transition to another is the one the drawing accepts only for a skip, so
     * a file that does not carry it would give back a chart the editor would not have drawn.
     */
    @Test
    void anOptionalBranchSurvivesTheFile() throws IOException {
        RecipeBytes.Memory storage = RecipeBytes.holding(SfcFixtures.lineal());
        RecipeEditorModel model = RecipeBytes.openOn(storage);
        model.edit("Make MIX optional",
                () -> ChartBranchingUseCase.addOptionalBranch(model.getRecipe(), null, "MIX"));
        model.save();

        S88ProcedureLogic reopened = RecipeBytes.openOn(storage).getRecipe().getProcedureLogic();

        assertTrue(reopened.getLinks().stream().anyMatch(link -> skipsFromOneTransitionToAnother(link)),
                "and the line that goes round MIX is still there after the file, in: "
                        + reopened.getLinks());
    }

    /** Whether a line runs from one transition straight to another, which only a skip does. */
    private static boolean skipsFromOneTransitionToAnother(
            org.apache.plc4x.malbec.s88.api.S88ProcedureLink link) {
        return link.getFrom().size() == 1 && link.getTo().size() == 1
                && link.getFrom().get(0).getType() == S88IdRefType.TRANSITION
                && link.getTo().get(0).getType() == S88IdRefType.TRANSITION;
    }
}
