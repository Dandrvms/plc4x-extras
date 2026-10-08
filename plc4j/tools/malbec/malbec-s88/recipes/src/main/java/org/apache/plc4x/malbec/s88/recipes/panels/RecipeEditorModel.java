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
import javax.swing.undo.AbstractUndoableEdit;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.core.RecipeDeepCopy;
import org.apache.plc4x.malbec.s88.data.FileObjectStorage;
import org.apache.plc4x.malbec.s88.recipes.services.S88RecipeProjectServices;
import org.openide.awt.UndoRedo;
import org.openide.filesystems.FileObject;

/**
 * What an open editor is holding, and what it does about it.
 * <p>
 * One of these per open recipe, and both views are handed the same one. That is the whole reason
 * editing the table and looking at the graph can be the same window: there is one recipe, and it has
 * one unsaved state and one history.
 * <p>
 * <b>It owns the unsaved state because nothing else does.</b> A recipe file has no type registered for
 * it and so is not a {@code DataObject}, which in the platform is what carries a document and its
 * dirty flag and supplies Save, Undo and Redo for nothing. Here the editor is the document, so it has
 * to keep track of whether there is anything to save itself.
 * <p>
 * <b>Every change goes through {@link #edit}</b>, so that every change can be undone and every change
 * marks the recipe as unsaved. An edit that goes around that is a change nobody can take back.
 * <p>
 * <b>The recipe is read again rather than edited in place after a revert.</b> A revert replaces the
 * recipe object instead of emptying it and refilling it, because there is no copy into an existing
 * recipe. A view therefore asks {@link #getRecipe()} every time it redraws rather than holding on to
 * the recipe it was given.
 */
public final class RecipeEditorModel {

    private final FileObject recipeFile;
    private final S88RecipeRepository repository;
    private final List<Consumer<RecipeEditorModel>> listeners = new CopyOnWriteArrayList<>();
    private final RecipeUndoRedo undoRedo = new RecipeUndoRedo();
    private S88MasterRecipe recipe;
    private int savedPosition;

    private RecipeEditorModel(FileObject recipeFile, S88MasterRecipe recipe,
                              S88RecipeRepository repository) {
        this.recipeFile = recipeFile;
        this.recipe = recipe;
        this.repository = repository;
        undoRedo.onApplied(this::fireChanged);
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
        return open(recipeFile, new FileObjectStorage(recipeFile));
    }

    /**
     * Reads a recipe so that it can be edited, from wherever the recipe is kept.
     * <p>
     * The storage is what the recipe is read from and written to, and in the editor it is one file
     * in a project. Taking it as an argument rather than assuming one is what lets the editor be
     * opened on a recipe that is not in a project at all, which is what a test needs and what a
     * runtime reading recipes off a machine would need.
     *
     * @param recipeFile the file being edited, {@code null} when there is no file
     * @param storage    where the recipe is read from and written to
     * @return the editor state for it
     * @throws IOException when the storage does not hold a recipe that can be read
     */
    static RecipeEditorModel open(FileObject recipeFile, S88Storage storage) throws IOException {
        S88RecipeRepository repository =
                S88RecipeProjectServices.createRepository("xml", storage);
        S88MasterRecipe recipe = repository.loadRecipe();
        if (recipe == null) {
            throw new IOException("'" + name(recipeFile) + "' does not hold a recipe, or is empty.");
        }
        return new RecipeEditorModel(recipeFile, recipe, repository);
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
        return undoRedo.position() != savedPosition;
    }

    /** What can be undone and redone, which the multiview reads to draw its own buttons. */
    public UndoRedo getUndoRedo() {
        return undoRedo;
    }

    /**
     * Makes a change that can be taken back.
     * <p>
     * <b>How it is taken back is not up to the caller.</b> A copy of the whole recipe is kept on both
     * sides of the change, and undo and redo put those copies back. Asking the caller for a way back
     * means every button has to know exactly what the change did and what undoes it, and one that
     * does not is an edit that cannot be taken back, or one that undoes half of itself.
     * <p>
     * <b>A change that fails leaves nothing behind.</b> The copy from before is put back before the
     * failure is reported, so a recipe that cannot take the change is a recipe that did not take it.
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
            replaceRecipe(RecipeDeepCopy.copyMasterRecipe(before));
            fireChanged();
            throw new RecipeEditException(name, failure);
        }
        S88MasterRecipe after = RecipeDeepCopy.copyMasterRecipe(recipe);
        // The recipe on screen is the one the change made, but it is a copy of it: undo and redo
        // both put their own copy back rather than running the change again, and the copy that is
        // kept must be the one that was really the state of things.
        replaceRecipe(RecipeDeepCopy.copyMasterRecipe(after));
        undoRedo.add(new SnapshotEdit(before, after), name);
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

    /**
     * Writes the recipe out.
     *
     * @throws IOException when the file cannot be written
     */
    public void save() throws IOException {
        repository.saveRecipe(recipe);
        savedPosition = undoRedo.position();
        fireChanged();
    }

    /**
     * Throws away everything changed since the recipe was read and reads it again.
     * <p>
     * The history goes with it. Leaving a stack of edits that undo to a recipe that is no longer on
     * screen is how undo starts producing states nobody chose.
     *
     * @throws IOException when the file cannot be read again
     */
    public void revert() throws IOException {
        S88MasterRecipe reloaded = repository.loadRecipe();
        if (reloaded == null) {
            throw new IOException("'" + recipeFile.getNameExt() + "' no longer holds a recipe.");
        }
        recipe = reloaded;
        undoRedo.discardAll();
        savedPosition = undoRedo.position();
        fireChanged();
    }

    /**
     * Throws the recipe away and puts another one in its place.
     * <p>
     * Used when an edit is taken back or put again: there is no copy of a recipe to fill in, so the
     * whole thing is swapped for one that was kept.
     *
     * @param other the recipe to hold from now on
     */
    private void replaceRecipe(S88MasterRecipe other) {
        this.recipe = other;
    }

    /**
     * Registers something to be told when the recipe changed.
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