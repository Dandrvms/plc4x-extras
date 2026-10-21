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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.CreateChartUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.core.RecipeDeepCopy;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;
import org.apache.plc4x.malbec.s88.data.FileObjectStorage;
import org.apache.plc4x.malbec.s88.recipes.services.RecipeSetPlantSnapshot;
import org.apache.plc4x.malbec.s88.recipes.services.S88RecipeProjectServices;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.awt.UndoRedo;
import org.openide.filesystems.FileObject;

/**
 * What an open editor is holding, and what it does about it.
 */
public final class RecipeEditorModel {

    private final FileObject recipeFile;
    private final S88RecipeRepository repository;
    private final S88PlantSnapshot plant;
    private final List<Consumer<RecipeEditorModel>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> chartSelectionListeners = new CopyOnWriteArrayList<>();
    private final RecipeUndoRedo undoRedo = new RecipeUndoRedo();
    private S88MasterRecipe recipe;
    private int savedPosition;

    /** A node the chart should pick as soon as it is drawn again. */
    private String pendingChartNode;

    /** The node the chart has picked now, {@code null} when it picked nothing. */
    private String chartSelection;

    /**
     * The name of the step whose chart is being edited, {@code null} for the chart of the recipe.
     * <p>
     * A name rather than the step itself, because the recipe is taken apart and put back together
     * by every edit.
     */
    private String currentStepId;

    private RecipeEditorModel(FileObject recipeFile, S88MasterRecipe recipe,
                              S88RecipeRepository repository, S88PlantSnapshot plant) {
        this.recipeFile = recipeFile;
        this.recipe = recipe;
        this.repository = repository;
        this.plant = plant;
        undoRedo.onApplied(this::fireChanged);
    }

    /**
     * The plant this recipe is written against, as it was when the recipe set was created.
     *
     * @return the frozen plant, {@code null} for a recipe that is not in a recipe set
     */
    public S88PlantSnapshot plant() {
        return plant;
    }

    /**
     * What to say about a recipe that can be drawn but not bound to anything.
     * <p>
     * The recipe set of this recipe has no plant frozen into it, so what its steps work on cannot be
     * said and none of the actions that need the plant are offered.
     *
     * @return one line to say, or {@code null} when the plant is there
     */
    public String warning() {
        return recipeFile == null || plant != null ? null
                : "'" + recipeFile.getNameExt() + "' belongs to a recipe set with no plant frozen"
                        + " into it, so what its steps work on cannot be said. Create the recipe"
                        + " set again with a plant to write against.";
    }

    /**
     * Reads a recipe file so that it can be edited.
     * <p>
     * A recipe whose recipe set has no plant frozen into it still opens, and the view says what that
     * means rather than the recipe being closed unread.
     *
     * @param recipeFile the recipe to open
     * @return the editor state for it
     * @throws IOException when the file does not hold a recipe that can be read
     */
    public static RecipeEditorModel open(FileObject recipeFile) throws IOException {
        return open(recipeFile, new FileObjectStorage(recipeFile), plantUnder(recipeFile));
    }

    /**
     * The frozen plant of the recipe set a recipe file belongs to.
     * <p>
     * A recipe set with no plant frozen into it still holds recipes that can be read and drawn. What
     * cannot be said is what their steps are attached to, so the plant comes back empty and the view
     * says so rather than the recipe being closed unread.
     *
     * @param recipeFile the recipe being opened
     * @return the plant, or {@code null} when the recipe set has none
     */
    private static S88PlantSnapshot plantUnder(FileObject recipeFile) {
        if (recipeFile == null) {
            return null;
        }
        FileObject folder = recipeFile.getParent();
        Project recipeSet;
        try {
            recipeSet = folder == null ? null : ProjectManager.getDefault().findProject(folder);
        } catch (IOException notAProject) {
            return null;
        }
        if (recipeSet == null || !RecipeSetPlantSnapshot.isTaken(recipeSet)) {
            return null;
        }
        return RecipeSetPlantSnapshot.required(recipeSet);
    }

    /**
     * Reads a recipe so that it can be edited, from wherever the recipe is kept.
     *
     * @param recipeFile the file being edited, {@code null} when there is no file
     * @param storage    where the recipe is read from and written to
     * @return the editor state for it
     * @throws IOException when the storage does not hold a recipe that can be read
     */
    static RecipeEditorModel open(FileObject recipeFile, S88Storage storage) throws IOException {
        return open(recipeFile, storage, null);
    }

    /**
     * Reads a recipe and the plant it is written against.
     *
     * @param recipeFile the file being edited, {@code null} when there is no file
     * @param storage    where the recipe is read from and written to
     * @param plant      the plant frozen into the recipe set, {@code null} when there is none
     * @return the editor state for it
     * @throws IOException when the storage does not hold a recipe that can be read
     */
    static RecipeEditorModel open(FileObject recipeFile, S88Storage storage,
                                  S88PlantSnapshot plant) throws IOException {
        S88RecipeRepository repository =
                S88RecipeProjectServices.createRepository("xml", storage);
        S88MasterRecipe recipe = repository.loadRecipe();
        if (recipe == null) {
            throw new IOException("'" + name(recipeFile) + "' does not hold a recipe, or is empty.");
        }
        return new RecipeEditorModel(recipeFile, recipe, repository, plant);
    }

    private static String name(FileObject recipeFile) {
        return recipeFile == null ? "The storage" : recipeFile.getNameExt();
    }

    /** The file being edited. */
    public FileObject getRecipeFile() {
        return recipeFile;
    }

    /**
     * The recipe being edited, shared by every view of it.
     *
     * @return the recipe, never {@code null} while the window is open
     */
    public S88MasterRecipe getRecipe() {
        return recipe;
    }

    /**
     * The step whose chart the editor is drawing.
     * <p>
     * The step is held by its name and found again in the recipe each time, because every edit
     * replaces the recipe with another object. Holding the step itself would leave the editor
     * drawing and changing a tree that is no longer the one on show.
     *
     * <p>A step an undo has taken off the recipe is left behind, and the chart of the recipe is
     * drawn in its place.
     *
     * @return the step, or {@code null} for the chart of the recipe
     */
    public S88RecipeElement currentStep() {
        return currentStepId == null ? null
                : recipe.findElement(currentStepId).orElse(null);
    }

    /**
     * The chart the editor is drawing.
     * <p>
     * A step whose chart has not been made yet has nothing to draw, so this is {@code null}. To
     * give it one, use {@link #createChartForCurrentStep()}.
     *
     * @return the chart of the recipe when no step is being edited, or {@code null} when the step
     *         being edited has no chart of its own
     */
    public S88ProcedureLogic chartBeingEdited() {
        return EditProcedureLogicUseCase.existingChart(recipe, currentStep());
    }

    /**
     * Gives the step being edited the chart its level calls for, when it does not have one yet.
     *
     * <p>A chart made here can be taken back, unlike one that a read makes on its own, because the
     * operator asked for it and it goes through an edit.
     *
     * @throws RecipeEditException when the change could not be made, after the recipe was put back
     */
    public void createChartForCurrentStep() {
        edit("Create chart",
                () -> CreateChartUseCase.forStep(getRecipe(), currentStep()));
    }

    /**
     * Draws the chart of the given step, or the chart of the recipe again when it is {@code null}.
     *
     * @param step step whose chart to draw, {@code null} for the chart of the recipe
     */
    public void editChartOf(S88RecipeElement step) {
        this.currentStepId = step == null ? null : step.getId();
        fireChanged();
    }

    /**
     * Draws the chart of a step and picks a node in it.
     *
     * @param step   step whose chart to draw, {@code null} for the chart of the recipe
     * @param nodeId node to pick once the chart and its subchart are drawn
     */
    void showChartNode(S88RecipeElement step, String nodeId) {
        this.currentStepId = step == null ? null : step.getId();
        this.pendingChartNode = nodeId;
        fireChanged();
    }

    /**
     * Takes the node the chart was asked to pick, if there is one.
     *
     * @return the node to pick, or {@code null} when the chart was not asked for one
     */
    String takePendingChartNode() {
        String node = pendingChartNode;
        pendingChartNode = null;
        return node;
    }

    /** The node the chart has picked now, {@code null} when it picked nothing. */
    String chartSelection() {
        return chartSelection;
    }

    /**
     * The path the route gives the chart on show, so that two charts with a node of the same name
     * can be told apart.
     *
     * @return the recipe id followed by the id of each step whose chart holds the one on show
     */
    String currentChartPath() {
        String root = recipe.getId() == null || recipe.getId().isBlank()
                ? "recipe" : recipe.getId();
        StringBuilder path = new StringBuilder(root);
        appendOwners(path, currentStep());
        return path.toString();
    }

    private static void appendOwners(StringBuilder path, S88RecipeElement step) {
        if (step == null) {
            return;
        }
        appendOwners(path, step.getParent());
        path.append('/').append(step.getId());
    }

    /**
     * Says the chart picked another node, so a view can follow it.
     *
     * @param nodeId the picked node, {@code null} when nothing is picked
     */
    void chartSelectionChanged(String nodeId) {
        if (Objects.equals(chartSelection, nodeId)) {
            return;
        }
        chartSelection = nodeId;
        for (Consumer<String> listener : chartSelectionListeners) {
            listener.accept(nodeId);
        }
    }

    void addChartSelectionListener(Consumer<String> listener) {
        chartSelectionListeners.add(listener);
    }

    void removeChartSelectionListener(Consumer<String> listener) {
        chartSelectionListeners.remove(listener);
    }

    /**
     * The level of the chart being drawn, which is what the steps in it are.
     *
     * @return the kind its steps are
     */
    public S88RecipeElementKind levelBeingEdited() {
        return RecipeLevels.levelOf(currentStep());
    }

    /**
     * What is wrong with the recipe right now, empty when it holds together.
     */
    public RecipeConformance conformance() {
        return RecipeConformance.of(recipe, plant);
    }

    /** Whether there is anything to save. */
    public boolean isModified() {
        return undoRedo.position() != savedPosition;
    }

    /** What can be undone and redone, which the multiview reads to draw its own buttons. */
    public UndoRedo getUndoRedo() {
        return undoRedo;
    }

    /**
     * Makes a change that can be taken back.
     *
     * <p>The change works on the recipe as it stands. The recipe is then copied, and the copy the
     * change left behind is the one kept for taking it back again, so this holds no more than one
     * live recipe and one snapshot per edit rather than three of each.
     *
     * @param name  how the undo button names this change, such as "Add step"
     * @param apply what the change does
     * @throws RecipeEditException when the change could not be made, after the recipe was put back
     */
    public void edit(String name, Runnable apply) {
        S88MasterRecipe before = RecipeDeepCopy.copyMasterRecipe(recipe);
        try {
            apply.run();
        } catch (RuntimeException failure) {
            replaceRecipe(before);
            fireChanged();
            throw new RecipeEditException(name, failure);
        }
        undoRedo.add(new SnapshotEdit(before, recipe), name);
        replaceRecipe(RecipeDeepCopy.copyMasterRecipe(recipe));
        fireChanged();
    }

    /** What an edit is taken back with: the recipe as it was, and the recipe as it is. */
    private final class SnapshotEdit extends AbstractUndoableEdit {

        private final S88MasterRecipe before;
        private final S88MasterRecipe after;

        SnapshotEdit(S88MasterRecipe before, S88MasterRecipe after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public void undo() {
            replaceRecipe(RecipeDeepCopy.copyMasterRecipe(before));
        }

        @Override
        public void redo() {
            replaceRecipe(RecipeDeepCopy.copyMasterRecipe(after));
        }

        @Override
        public String getPresentationName() {
            return "Change";
        }
    }


    public boolean undo() {
        if (!undoRedo.canUndo()) {
            return false;
        }
        try {
            undoRedo.undo();
            return true;
        } catch (CannotUndoException nothingToUndo) {
            return false;
        }
    }


    public boolean redo() {
        if (!undoRedo.canRedo()) {
            return false;
        }
        try {
            undoRedo.redo();
            return true;
        } catch (CannotRedoException nothingToRedo) {
            return false;
        }
    }


    public void save() throws IOException {
        repository.saveRecipe(recipe);
        savedPosition = undoRedo.position();
        fireChanged();
    }


    public void revert() throws IOException {
        S88MasterRecipe reloaded = repository.loadRecipe();
        if (reloaded == null) {
            throw new IOException("'" + name(recipeFile) + "' no longer holds a recipe.");
        }
        recipe = reloaded;
        undoRedo.discardAll();
        savedPosition = undoRedo.position();
        fireChanged();
    }


    private void replaceRecipe(S88MasterRecipe other) {
        this.recipe = other;
    }


    void addChangeListener(Consumer<RecipeEditorModel> listener) {
        listeners.add(listener);
    }

    void removeChangeListener(Consumer<RecipeEditorModel> listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        for (Consumer<RecipeEditorModel> listener : listeners) {
            listener.accept(this);
        }
    }
}