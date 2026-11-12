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
package org.apache.plc4x.malbec.s88.recipes.editor;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88ChangeListener;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantModelProvider;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeHeader;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.CreateChartUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.EquipmentUidMigration;
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
import org.openide.util.WeakListeners;

/**
 * What an open editor is holding, and what it does about it.
 */
public final class RecipeEditorModel {

    private final FileObject recipeFile;
    private final S88RecipeRepository repository;
    private final S88PlantModelProvider plantProvider;
    private final S88ChangeListener plantListener;
    private final S88ChangeListener plantWeakListener;
    private S88PlantModel plant;
    private final List<Consumer<RecipeEditorModel>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> chartSelectionListeners = new CopyOnWriteArrayList<>();
    private final RecipeUndoRedo undoRedo = new RecipeUndoRedo();
    private S88MasterRecipe recipe;
    private S88RecipeHeader header;
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
                              S88RecipeRepository repository, S88PlantModelProvider plantProvider,
                              S88PlantModel plant) {
        this.recipeFile = recipeFile;
        this.recipe = recipe;
        this.repository = repository;
        this.plantProvider = plantProvider;
        this.plant = plant;
        this.plantListener = event -> plantChanged();
        this.plantWeakListener = plantProvider == null ? null
                : WeakListeners.create(S88ChangeListener.class, plantListener, plantProvider);
        if (plantProvider != null) {
            plantProvider.addChangeListener(plantWeakListener);
        }
        this.header = S88RecipeHeader.of(recipe);
        undoRedo.onApplied(this::fireChanged);
    }

    /**
     * The plant this recipe is written against.
     *
     * @return the plant, {@code null} for a recipe that is not under a readable plant
     */
    public S88PlantModel plant() {
        return plant;
    }

    /**
     * What to say about a recipe that can be drawn but not bound to anything.
     *
     * @return one line to say, or {@code null} when the plant is there
     */
    public String warning() {
        if (recipeFile == null || plant != null) {
            return null;
        }
        return plantProvider != null
                ? "'" + recipeFile.getNameExt() + "' belongs to a plant folder with no readable"
                        + " plant.xml, so what its steps work on cannot be said."
                : "'" + recipeFile.getNameExt() + "' is not under a plant with a readable plant.xml,"
                        + " so what its steps work on cannot be said.";
    }

    private void plantChanged() {
        if (plantProvider == null) {
            return;
        }
        plant = plantProvider.getPlantModel();
        fireChanged();
    }

    /**
     * Reads a recipe file so that it can be edited.
     *
     * @param recipeFile the recipe to open
     * @return the editor state for it
     * @throws IOException when the file does not hold a recipe that can be read
     */
    public static RecipeEditorModel open(FileObject recipeFile) throws IOException {
        return open(recipeFile, new FileObjectStorage(recipeFile), plantUnder(recipeFile));
    }

    private static PlantSource plantUnder(FileObject recipeFile) {
        if (recipeFile == null) {
            return PlantSource.none();
        }
        FileObject recipeSetFolder = recipeFile.getParent();
        Project recipeSet = projectAt(recipeSetFolder);
        if (recipeSet == null) {
            return PlantSource.none();
        }
        S88PlantModelProvider provider = providerAt(recipeSetFolder == null
                ? null : recipeSetFolder.getParent());
        if (provider != null) {
            return PlantSource.live(provider);
        }
        S88PlantModel legacy = RecipeSetPlantSnapshot.openModel(recipeSet);
        return legacy == null ? PlantSource.none() : PlantSource.legacy(legacy);
    }

    private static Project projectAt(FileObject folder) {
        if (folder == null) {
            return null;
        }
        try {
            return ProjectManager.getDefault().findProject(folder);
        } catch (IOException | IllegalArgumentException notAProject) {
            return null;
        }
    }

    private static S88PlantModelProvider providerAt(FileObject folder) {
        Project project = projectAt(folder);
        return project == null ? null : project.getLookup().lookup(S88PlantModelProvider.class);
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
        return openStatic(recipeFile, storage, PlantSource.none());
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
        return openStatic(recipeFile, storage,
                plant == null ? PlantSource.none() : PlantSource.legacy(plant.getPlant()));
    }

    static RecipeEditorModel open(FileObject recipeFile, S88Storage storage,
                                  S88PlantModelProvider provider) throws IOException {
        return openStatic(recipeFile, storage,
                provider == null ? PlantSource.none() : PlantSource.live(provider));
    }

    private static RecipeEditorModel open(FileObject recipeFile, S88Storage storage,
                                          PlantSource source) throws IOException {
        return openStatic(recipeFile, storage, source);
    }

    private static RecipeEditorModel openStatic(FileObject recipeFile, S88Storage storage,
                                                PlantSource source) throws IOException {
        S88RecipeRepository repository =
                S88RecipeProjectServices.createRepository("xml", storage);
        S88MasterRecipe recipe = repository.loadRecipe();
        if (recipe == null) {
            throw new IOException("'" + name(recipeFile) + "' does not hold a recipe, or is empty.");
        }
        EquipmentUidMigration.repair(recipe, source.plant());
        return new RecipeEditorModel(recipeFile, recipe, repository, source.provider(),
                source.plant());
    }

    private record PlantSource(S88PlantModelProvider provider, S88PlantModel plant) {

        static PlantSource none() {
            return new PlantSource(null, null);
        }

        static PlantSource live(S88PlantModelProvider provider) {
            return new PlantSource(provider, provider.getPlantModel());
        }

        static PlantSource legacy(S88PlantModel plant) {
            return new PlantSource(null, plant);
        }
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

    /** The header of the recipe, as it was read or last saved. */
    public S88RecipeHeader header() {
        return header;
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
        if (!isModified()) {
            return;
        }
        S88RecipeHeader previous = header;
        String fileName = recipeFile == null ? null : recipeFile.getNameExt();
        Instant now = Instant.now();
        S88RecipeHeader saved = header.withDefaults(now,
                System.getProperty("user.name", S88RecipeHeader.UNKNOWN_AUTHOR))
                .saved(now, fileName);
        saved.applyTo(recipe);
        try {
            repository.saveRecipe(recipe);
        } catch (RuntimeException failure) {
            previous.applyTo(recipe);
            header = previous;
            throw failure;
        }
        header = saved;
        savedPosition = undoRedo.position();
        fireChanged();
    }


    public void revert() throws IOException {
        S88MasterRecipe reloaded = repository.loadRecipe();
        if (reloaded == null) {
            throw new IOException("'" + name(recipeFile) + "' no longer holds a recipe.");
        }
        recipe = reloaded;
        header = S88RecipeHeader.of(reloaded);
        undoRedo.discardAll();
        savedPosition = undoRedo.position();
        fireChanged();
    }


    private void replaceRecipe(S88MasterRecipe other) {
        this.recipe = other;
        header.applyTo(other);
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