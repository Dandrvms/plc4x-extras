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

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.time.Instant;
import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeHeader;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.data.FileObjectStorage;
import org.apache.plc4x.malbec.s88.recipes.services.S88RecipeProjectServices;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.windows.WindowManager;

/**
 * Creates a new recipe as a file in the recipe set.
 */
public class NewRecipeAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public NewRecipeAction() {
        this(Lookup.EMPTY);
    }

    private NewRecipeAction(Lookup context) {
        super("New Recipe");
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) {
            return;
        }

        Window owner = WindowManager.getDefault().getMainWindow();
        NewRecipeDialog.RecipeDraft draft = new NewRecipeDialog(owner, "").showDialog();
        if (draft == null) {
            return;
        }
        String name = draft.name();
        String fileName = draft.fileName();
        FileObject existing = project.getProjectDirectory().getFileObject(fileName);
        if (existing != null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "'" + fileName + "' already exists in this recipe set.",
                    NotifyDescriptor.WARNING_MESSAGE));
            return;
        }

        FileObject recipeXml = null;
        try {
            S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute(name, draft.kind());
            recipeXml = project.getProjectDirectory().createData(fileName);
            S88RecipeHeader header = S88RecipeHeader.created(
                    System.getProperty("user.name", S88RecipeHeader.UNKNOWN_AUTHOR),
                    draft.description(), draft.kind(), S88RecipeHeader.PLANT_FILE, fileName,
                    Instant.now());
            header.applyTo(recipe);
            S88RecipeProjectServices.createRepository("xml", new FileObjectStorage(recipeXml))
                    .saveRecipe(recipe);
            project.getProjectDirectory().refresh();
            OpenCookie open = DataObject.find(recipeXml).getCookie(OpenCookie.class);
            if (open != null) {
                open.open();
            }
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Recipe " + name + " created.", NotifyDescriptor.INFORMATION_MESSAGE));
        } catch (IOException | IllegalArgumentException ex) {
            if (recipeXml != null) {
                try {
                    recipeXml.delete();
                } catch (IOException ignored) {
                }
            }
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.WARNING_MESSAGE));
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new NewRecipeAction(lkp);
    }
}
