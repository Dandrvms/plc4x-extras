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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.recipes.impl.Plc4xRecipesSubProjectProviderImpl;
import org.apache.plc4x.malbec.s88.recipes.services.RecipeSetPlantSnapshot;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 * Action to create a new Recipe set as a Sub-project.
 * <p>
 * <b>The plant is frozen into the new recipe set before it is finished.</b> A recipe step is a piece
 * of equipment with values given to its parameters, and nothing here can say which equipment without
 * the plant. Taking the plant at creation is what makes every recipe of the set answer to the plant
 * it was written against, rather than to whatever the plant file says by the time somebody opens the
 * recipe.
 * <p>
 * A recipe set with no plant is not created. An empty set with no plant would have to be opened
 * before anything could be done with it, and the recipe set would exist on disk as a folder whose
 * whole purpose it cannot carry out.
 */

public class CreateRecipesProjectAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public CreateRecipesProjectAction() {
        this(Lookup.EMPTY);
    }

    private CreateRecipesProjectAction(Lookup context) {
        super("New Recipe Set");
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) {
            return;
        }

        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine("Recipe set:", "Create a new Recipe sets");
        input.setInputText("");
        if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = input.getInputText();
        if (name == null || name.trim().isEmpty()) {
            say("A recipe set needs a name.");
            return;
        }

        S88PlantModel plant = RecipeSetPlantSnapshot.plantOf(project.getProjectDirectory());
        if (plant == null) {
            say("This project has no plant, so there is nothing for the recipes to be written"
                    + " against. Create the plant first.");
            return;
        }

        Project recipeSet = null;
        FileObject dir = null;
        try {
            dir = project.getProjectDirectory().createFolder(name.trim());
            dir.createData("recipes.cfg");
            project.getProjectDirectory().refresh();
            dir.refresh();

            recipeSet = ProjectManager.getDefault().findProject(dir);
            if (recipeSet == null) {
                say("The recipe set could not be opened as a project of its own.");
                deleteQuietly(dir);
                return;
            }
            RecipeSetPlantSnapshot.takeIfMissing(recipeSet, plant);

            Plc4xRecipesSubProjectProviderImpl provider =
                    project.getLookup().lookup(Plc4xRecipesSubProjectProviderImpl.class);
            if (provider != null) {
                EventQueue.invokeLater(provider::fireChange);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            if (recipeSet != null) {
                deleteQuietly(dir);
            } else {
                    try {
                        recipeSet.getProjectDirectory().delete();
                    } catch (IOException ioe) {
                        Exceptions.printStackTrace(ioe);
                    }
                }
            Exceptions.printStackTrace(ex);
            say(ex.getMessage());
        }
    }

    private static void deleteQuietly(FileObject folder) {
        if (folder == null) {
            return;
        }
        try {
            folder.delete();
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
        }
    }


    /**
     * Says something and waits for it to be read.
     *
     * @param what the message
     */
    private static void say(String what) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    what, NotifyDescriptor.WARNING_MESSAGE));
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new CreateRecipesProjectAction(lkp);
    }
}
