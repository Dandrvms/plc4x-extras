/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.recipes.actions;

import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.recipes.data.RecipeDataObject;
import org.apache.plc4x.malbec.s88.recipes.editor.RecipeEditorModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** Opens the read-only ISA-88 header of the recipe in focus. */
@ActionID(category = "Recipe",
        id = "org.apache.plc4x.malbec.s88.recipes.actions.ShowRecipeHeaderAction")
@ActionRegistration(displayName = "Recipe Header", menuText = "Recipe Header")
@ActionReference(path = "Menu/Recipe", position = 100)
public final class ShowRecipeHeaderAction extends AbstractAction implements ContextAwareAction {

    private static final long serialVersionUID = 1L;

    private final Lookup context;

    public ShowRecipeHeaderAction() {
        this(Lookup.EMPTY);
    }

    private ShowRecipeHeaderAction(Lookup context) {
        super("Recipe Header");
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        RecipeEditorModel model = modelOf(context);
        if (model == null) {
            return;
        }
        Window owner = WindowManager.getDefault().getMainWindow();
        String fileName = model.getRecipeFile() == null ? null
                : model.getRecipeFile().getNameExt();
        new RecipeHeaderDialog(owner, model.getRecipe(), model.header(), fileName).setVisible(true);
    }

    @Override
    public boolean isEnabled() {
        return modelOf(context) != null;
    }

    @Override
    public Action createContextAwareInstance(Lookup otherContext) {
        return new ShowRecipeHeaderAction(otherContext);
    }

    private static RecipeEditorModel modelOf(Lookup lookup) {
        RecipeEditorModel model = modelOfLookup(lookup);
        if (model != null) {
            return model;
        }
        TopComponent active = TopComponent.getRegistry().getActivated();
        if (active != null && active.getLookup() != lookup) {
            return modelOfLookup(active.getLookup());
        }
        return null;
    }

    private static RecipeEditorModel modelOfLookup(Lookup lookup) {
        RecipeEditorModel model = lookup == null ? null : lookup.lookup(RecipeEditorModel.class);
        if (model != null) {
            return model;
        }
        RecipeDataObject dataObject = lookup == null ? null : lookup.lookup(RecipeDataObject.class);
        if (dataObject == null) {
            return null;
        }
        try {
            return dataObject.model();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
