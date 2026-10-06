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

import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.data.FileObjectStorage;
import org.apache.plc4x.malbec.s88.recipes.services.S88RecipeProjectServices;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 * Creates a new recipe as a file in the recipe set.
 * <p>
 * Two things are asked, and the second one is the only real question here. A recipe is always a
 * master recipe: a control recipe is built at run time from a master and a plant, so there is
 * nothing of that kind to create here. What the engineer does choose is how the recipe names its
 * equipment, by the class of it or by one particular module, and that choice is made once and
 * applies to every step of the recipe, because a recipe that mixed the two would read but could not
 * run.
 * <p>
 * The file is written straight away, holding a recipe with its start and its stop and nothing else
 * between them. It has to exist before anything is edited in it: opening a file that is not there is
 * opening nothing, and a recipe is a document that is read, checked and saved as it is written.
 */
public class NewRecipeAction extends AbstractAction implements ContextAwareAction {

    /** What the recipe names its equipment by. */
    private static final String BY_CLASS = "By equipment class";
    private static final String BY_INSTANCE = "By specific equipment";

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

        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine("Recipe name:", "New Recipe");
        input.setInputText("");
        if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = input.getInputText() == null ? "" : input.getInputText().trim();
        if (name.isEmpty()) {
            // A recipe file named after nothing is a file nobody can find again, and saying so here
            // is friendlier than creating one and leaving it to be discovered later.
            DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message("A recipe needs a name.", NotifyDescriptor.WARNING_MESSAGE));
            return;
        }

        Object choice = DialogDisplayer.getDefault().notify(new NotifyDescriptor(
                "Should this recipe name the class of the equipment, or one particular module?",
                "New Recipe " + name,
                NotifyDescriptor.QUESTION_MESSAGE,
                NotifyDescriptor.DEFAULT_OPTION,
                new Object[]{BY_CLASS, BY_INSTANCE, NotifyDescriptor.CANCEL_OPTION},
                BY_CLASS));
        if (choice == null || choice == NotifyDescriptor.CANCEL_OPTION) {
            return;
        }
        S88RecipeKind kind = BY_INSTANCE.equals(choice) ? S88RecipeKind.INSTANCE : S88RecipeKind.CLASS;

        try {
            S88MasterRecipe recipe =
                    CreateMasterRecipeUseCase.execute(name, kind);
            FileObject recipeXml = project.getProjectDirectory().createData(name + ".xml");
            S88RecipeProjectServices.createRepository("xml", new FileObjectStorage(recipeXml))
                    .saveRecipe(recipe);
            project.getProjectDirectory().refresh();
        } catch (IOException | IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new NewRecipeAction(lkp);
    }
}
