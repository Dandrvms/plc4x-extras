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
import org.apache.plc4x.malbec.s88.recipes.panels.RecipeEditorTopComponent;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;

/**
 * Opens a recipe in an editor.
 * <p>
 * There is no open action from the platform for this file, because a recipe has no registered file
 * type and so is not a data object that the platform knows how to open. This is that action instead.
 * <p>
 * <b>A recipe that cannot be read is said out loud and nothing opens.</b> An editor over a recipe
 * that was not read would show an empty recipe, and the first save would write that empty recipe over
 * the one on disk, which is a worse outcome than not opening at all.
 */
public final class RecipeOpenAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public RecipeOpenAction() {
        this(Lookup.EMPTY);
    }

    private RecipeOpenAction(Lookup context) {
        super("Open");
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        FileObject recipeFile = context.lookup(FileObject.class);
        if (recipeFile == null) {
            return;
        }
        try {
            RecipeEditorTopComponent.showFor(recipeFile);
        } catch (IOException e) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "'" + recipeFile.getNameExt() + "' could not be opened.\n" + e.getMessage(),
                    NotifyDescriptor.ERROR_MESSAGE));
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new RecipeOpenAction(lkp);
    }
}
