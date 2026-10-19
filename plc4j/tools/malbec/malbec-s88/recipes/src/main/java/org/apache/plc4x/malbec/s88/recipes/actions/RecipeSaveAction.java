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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.recipes.panels.RecipeEditorModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;

/**
 * Writes the recipe on disk.
 */
public final class RecipeSaveAction extends AbstractAction implements ContextAwareAction {

    private static final Logger LOG = Logger.getLogger(RecipeSaveAction.class.getName());

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
        if (!save(model)) {
            reportCouldNotSave(model);
        }
    }

    /**
     * Writes a recipe out.
     *
     * @param model the recipe to write
     * @return true when it got onto disk, false when it did not
     */
    public static boolean save(RecipeEditorModel model) {
        try {
            model.save();
            return true;
        } catch (IOException | RuntimeException couldNotWrite) {
            LOG.log(Level.WARNING, "The recipe could not be saved.", couldNotWrite);
            return false;
        }
    }

    /**
     * Says out loud that the recipe could not be written.
     *
     * @param model the recipe that did not get onto disk
     */
    public static void reportCouldNotSave(RecipeEditorModel model) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                "'" + model.getRecipeFile().getNameExt() + "' could not be saved.",
                NotifyDescriptor.ERROR_MESSAGE));
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new RecipeSaveAction(lkp);
    }
}
