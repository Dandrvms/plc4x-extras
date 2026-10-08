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
import java.util.Arrays;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the editor does when an edit is taken back, put again, or refused halfway.
 * <p>
 * Everything here compares the recipe as a file would hold it. An edit that cannot be taken all the
 * way back is an edit the operator cannot undo, and nothing in the editor's own state would say so.
 */
class RecipeEditorModelTest {

    /** Adds a step after the start, which is what the Add step button does. */
    private static void addStep(RecipeEditorModel model, String name) {
        model.edit("Add step " + name,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        model.getRecipe(), null, "BOX_BEGIN", name, "BOX_" + name,
                        "T_" + name, name + "_CLASS"));
    }

    /** Adds a step whose name is already in the recipe, which the core refuses. */
    private static void addStepWithAClashingName(RecipeEditorModel model) {
        model.edit("Add step QUENCH",
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        model.getRecipe(), null, "BOX_BEGIN", "QUENCH", "BOX_QUENCH", "T_QUENCH",
                        "QUENCH_CLASS"));
    }

    private static RecipeEditorModel open() {
        try {
            return RecipeBytes.openOn(RecipeBytes.holding(SfcFixtures.lineal()));
        } catch (IOException failure) {
            throw new AssertionError("a recipe that was just written could not be read", failure);
        }
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

        model.edit("Split BOX_BEGIN",
                () -> EditProcedureLogicUseCase.selectiveFork(
                        model.getRecipe(), null, "BOX_BEGIN", "T_ONE_OF", "T_OTHERWISE"));
        model.edit("Split T_OTHERWISE",
                () -> EditProcedureLogicUseCase.parallelFork(
                        model.getRecipe(), null, "T_OTHERWISE", "QUENCH", "CHILL"));
        model.edit("Join BOX_QUENCH and BOX_CHILL",
                () -> EditProcedureLogicUseCase.joinOnto(model.getRecipe(), null,
                        "BOX_QUENCH", "BOX_CHILL", "T_BOTH", null));
        model.edit("Condition of T_ONE_OF",
                () -> EditProcedureLogicUseCase.setCondition(
                        model.getRecipe(), null, "T_ONE_OF", "Reports/STATE#=#COMPLETE"));
        model.edit("Rename BOX_HEAT",
                () -> EditProcedureLogicUseCase.renameStep(
                        model.getRecipe(), null, "BOX_HEAT", "HEATING"));
        byte[] afterEdits = RecipeBytes.of(model.getRecipe());

        assertFalse(Arrays.equals(asOpened, afterEdits),
                "and the edits together changed the recipe, or none of them did anything");
        assertEquals(5, howManyCanBeTakenBack(model), "and each of them is one the operator can"
                + " take back on its own, rather than five edits that have become one step");

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
}
