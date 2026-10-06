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

import java.awt.event.ActionEvent;
import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;

//import org.apache.plc4x.malbec.s88.api.*;
import org.apache.plc4x.malbec.s88.recipes.impl.Plc4xRecipesSubProjectProviderImpl;
import org.netbeans.api.project.Project;
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

        try {
            FileObject dir = project.getProjectDirectory().createFolder(name);
            FileObject recipesConfig = dir.createData("recipes.cfg");

            project.getProjectDirectory().refresh();
            dir.refresh();

            org.netbeans.api.project.ProjectManager.getDefault().findProject(dir);

            Plc4xRecipesSubProjectProviderImpl provider = project.getLookup().lookup(Plc4xRecipesSubProjectProviderImpl.class);
            if (provider != null) {
                java.awt.EventQueue.invokeLater(provider::fireChange);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new CreateRecipesProjectAction(lkp);
    }
}
