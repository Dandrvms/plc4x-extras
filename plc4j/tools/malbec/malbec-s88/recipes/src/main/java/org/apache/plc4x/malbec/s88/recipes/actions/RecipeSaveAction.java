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
package org.apache.plc4x.malbec.s88.recipes.actions;

import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.recipes.panels.RecipeEditorModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;

/**
 * Writes the recipe on disk.
 * <p>
 * The platform has a save action, but it belongs to a document, and a recipe is not one: its file
 * has no registered file type and so the platform does not know there is anything here to save. This
 * action stands in for it, and it is on the toolbar of every view and bound to {@code Ctrl+S}.
 * <p>
 * A recipe that cannot be written is said out loud and stays unsaved, so the window still knows
 * there is something to save rather than quietly claiming otherwise.
 */
public final class RecipeSaveAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public RecipeSaveAction() {
        this(Lookup.EMPTY);
    }

    private RecipeSaveAction(Lookup context) {
        super("Save");
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        RecipeEditorModel model = context.lookup(RecipeEditorModel.class);
        if (model == null) {
            return;
        }
        save(model, null);
    }

    /**
     * Writes a recipe out, and says so out loud when it cannot be written.
     *
     * <p>
     * Shared with the window that asks about closing, because a recipe that could not be written on
     * purpose and one that could not be written by accident are the same problem for the operator and
     * have to be said about the same way.
     *
     * @param model  the recipe to write
     * @param parent the window the complaint belongs to, {@code null} for none
     */
    public static void save(RecipeEditorModel model, java.awt.Component parent) {
        try {
            model.save();
        } catch (IOException e) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "'" + model.getRecipeFile().getNameExt() + "' could not be saved.\n"
                            + e.getMessage(),
                    NotifyDescriptor.ERROR_MESSAGE));
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new RecipeSaveAction(lkp);
    }
}
