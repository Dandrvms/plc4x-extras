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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.data.FileObjectStorage;
import org.apache.plc4x.malbec.s88.recipes.services.S88RecipeProjectServices;
import org.openide.filesystems.FileObject;

/**
 * What an open editor is holding, and what it does about it.
 * <p>
 * One of these per open recipe, and both views are handed the same one. That is the whole reason
 * editing the table and looking at the graph can be the same window: there is one recipe, and it has
 * one unsaved state.
 * <p>
 * <b>It owns the unsaved state because nothing else does.</b> A recipe file has no type registered for
 * it and so is not a {@code DataObject}, which in the platform is what carries a document and its
 * dirty flag and supplies Save, Undo and Redo for nothing. Here the editor is the document, so it has
 * to keep track of whether there is anything to save itself.
 * <p>
 * <b>The recipe is read again rather than edited in place after a revert.</b> A revert replaces the
 * recipe object instead of emptying it and refilling it, because the deep copy hands back a new
 * object and there is no copy into an existing one. A view therefore asks
 * {@link #getRecipe()} every time it redraws rather than holding on to the recipe it was given.
 */
public final class RecipeEditorModel {

    private final FileObject recipeFile;
    private final S88RecipeRepository repository;
    private final List<Consumer<RecipeEditorModel>> listeners = new CopyOnWriteArrayList<>();
    private S88MasterRecipe recipe;
    private boolean modified;

    private RecipeEditorModel(FileObject recipeFile, S88MasterRecipe recipe,
                              S88RecipeRepository repository) {
        this.recipeFile = recipeFile;
        this.recipe = recipe;
        this.repository = repository;
    }

    /**
     * Reads a recipe file so that it can be edited.
     * <p>
     * A file that cannot be read is a failure here rather than a recipe with something missing. An
     * editor that opened on a recipe it had not managed to read would show an empty recipe, and the
     * next save would write that empty recipe over the one on disk.
     *
     * @param recipeFile the recipe to open
     * @return the editor state for it
     * @throws IOException when the file does not hold a recipe that can be read
     */
    public static RecipeEditorModel open(FileObject recipeFile) throws IOException {
        S88RecipeRepository repository =
                S88RecipeProjectServices.createRepository("xml", new FileObjectStorage(recipeFile));
        S88MasterRecipe recipe = repository.loadRecipe();
        if (recipe == null) {
            throw new IOException("'" + recipeFile.getNameExt()
                    + "' does not hold a recipe, or is empty.");
        }
        return new RecipeEditorModel(recipeFile, recipe, repository);
    }

    /** The file being edited. */
    public FileObject getRecipeFile() {
        return recipeFile;
    }

    /**
     * The recipe being edited, shared by every view of it.
     * <p>
     * Asked for on each redraw rather than held on to, because a revert brings a different object.
     *
     * @return the recipe, never {@code null} while the window is open
     */
    public S88MasterRecipe getRecipe() {
        return recipe;
    }

    /** What is wrong with the recipe right now, empty when it holds together. */
    public RecipeConformance conformance() {
        return RecipeConformance.of(recipe);
    }

    /** Whether there is anything to save. */
    public boolean isModified() {
        return modified;
    }

    /**
     * Says a view changed something.
     * <p>
     * Every edit goes through here, and that is what keeps the two views in step: each one hears
     * about the other's changes and redraws.
     */
    public void markModified() {
        modified = true;
        fireChanged();
    }

    /**
     * Writes the recipe out.
     *
     * @throws IOException when the file cannot be written
     */
    public void save() throws IOException {
        repository.saveRecipe(recipe);
        modified = false;
        fireChanged();
    }

    /**
     * Throws away everything changed since the recipe was read and reads it again.
     * <p>
     * This is what a window does when it is closed with unsaved changes and the operator says no.
     * Reloading rather than undoing edit by edit is the only honest option: this model has no undo
     * stack, so there is nothing to step back through.
     *
     * @throws IOException when the file cannot be read again
     */
    public void revert() throws IOException {
        S88MasterRecipe reloaded = repository.loadRecipe();
        if (reloaded == null) {
            throw new IOException("'" + recipeFile.getNameExt()
                    + "' no longer holds a recipe.");
        }
        recipe = reloaded;
        modified = false;
        fireChanged();
    }

    /**
     * Registers something to be told when the recipe changed.
     * <p>
     * Held weakly in spirit and copied on each call, so that a view which closes without
     * unregistering does not keep itself alive.
     */
    void addChangeListener(Consumer<RecipeEditorModel> listener) {
        listeners.add(listener);
    }

    /** Stops telling a listener, so that a closed view is not called back into. */
    void removeChangeListener(Consumer<RecipeEditorModel> listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        for (Consumer<RecipeEditorModel> listener : listeners) {
            listener.accept(this);
        }
    }
}
