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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;
import org.openide.awt.UndoRedo;

/**
 * What can be undone and redone in one open recipe.
 * <p>
 * <b>Written here rather than taken from the platform.</b> The platform's own undo manager is
 * {@code org.openide.text.UndoRedoManager}, and it takes a {@code CloneableEditorSupport}, which is
 * a wrapper around a document. A recipe is not a document and has no reason to be one: it is a model
 * that a repository reads and writes whole. Implementing the small interface the multiview asks for
 * over a stack of edits is the whole of what undo is here, and it costs no document at all.
 * <p>
 * <b>The stack forgets what is redoable as soon as something new is done.</b> That is what undo means
 * everywhere else, and the alternative leaves a redo that would take the recipe somewhere it has
 * never been.
 */
final class RecipeUndoRedo implements UndoRedo {

    private static final Logger LOGGER = Logger.getLogger(RecipeUndoRedo.class.getName());

    /** Enough to be useful on a chart without eating memory on a big one. */
    private static final int LIMIT = 200;

    private final Deque<NamedEdit> done = new ArrayDeque<>();
    private final Deque<NamedEdit> undone = new ArrayDeque<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    private Runnable applied = () -> {
    };
    private int position;

    /**
     * Says what to call when an edit is taken back or put again.
     * <p>
     * The change listeners of this class only reach the window's own buttons. What an edit did to
     * the recipe has to be said separately, because a view that is not told keeps drawing something
     * that is no longer there.
     *
     * @param what to call once the recipe has changed, never {@code null}
     */
    void onApplied(Runnable what) {
        this.applied = what == null ? () -> {
        } : what;
    }

    /**
     * Records an edit that has already been applied.
     *
     * @param edit what was done
     * @param name how it is called in the button, such as "Add step"
     */
    void add(UndoableEdit edit, String name) {
        done.push(new NamedEdit(edit, name));
        while (done.size() > LIMIT) {
            done.removeLast();
        }
        undone.clear();
        position++;
        fireChanged();
    }

    /** Whether there is anything to go back to, which is also whether there is anything to save. */
    boolean hasHistory() {
        return !done.isEmpty();
    }

    /**
     * How many edits are applied right now.
     * <p>
     * This is what says whether the recipe on screen is the one on disk: the position where it was
     * saved is remembered by the editor, and being anywhere else means there is something to save.
     * Counting rather than carrying a separate dirty flag is what keeps the flag and the history from
     * telling different stories, which they would as soon as something was undone.
     */
    int position() {
        return position;
    }

    /** Throws the whole history away, for a recipe that was reloaded from the file. */
    void discardAll() {
        done.clear();
        undone.clear();
        position = 0;
        fireChanged();
    }

    @Override
    public boolean canUndo() {
        return !done.isEmpty();
    }

    @Override
    public boolean canRedo() {
        return !undone.isEmpty();
    }

    @Override
    public void undo() throws CannotUndoException {
        if (done.isEmpty()) {
            throw new CannotUndoException();
        }
        NamedEdit edit = done.pop();
        try {
            edit.edit.undo();
        } catch (RuntimeException failure) {
            // Put it back rather than losing it: an edit that cannot be taken back after it has
            // already been applied is a bug in the edit, and dropping it makes the button stop
            // working with nothing to say why.
            done.push(edit);
            throw failure;
        }
        undone.push(edit);
        position--;
        tell();
    }

    @Override
    public void redo() throws CannotRedoException {
        if (undone.isEmpty()) {
            throw new CannotRedoException();
        }
        NamedEdit edit = undone.pop();
        try {
            edit.edit.redo();
        } catch (RuntimeException failure) {
            undone.push(edit);
            throw failure;
        }
        done.push(edit);
        position++;
        tell();
    }

    /**
     * Tells the buttons, then tells the views.
     * <p>
     * The edit is put back on the other stack either way, so a view that throws while redrawing
     * does not cost the operator the edit they just took back.
     */
    private void tell() {
        fireChanged();
        try {
            applied.run();
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "A view did not redraw after an edit.", failure);
        }
    }

    @Override
    public String getUndoPresentationName() {
        return done.isEmpty() ? "Undo" : done.peek().name;
    }

    @Override
    public String getRedoPresentationName() {
        return undone.isEmpty() ? "Redo" : undone.peek().name;
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(listeners)) {
            listener.stateChanged(event);
        }
    }

    /** An edit and the name its button carries. */
    private static final class NamedEdit {

        private final UndoableEdit edit;
        private final String name;

        NamedEdit(UndoableEdit edit, String name) {
            this.edit = edit;
            this.name = name == null ? "Change" : name;
        }
    }
}