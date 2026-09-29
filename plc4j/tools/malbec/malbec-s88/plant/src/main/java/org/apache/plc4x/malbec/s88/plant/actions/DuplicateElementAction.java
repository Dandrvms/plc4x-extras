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
package org.apache.plc4x.malbec.s88.plant.actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.DuplicateElementDialog;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 * Action to turn an ISA-88 element into an equipment type and create copies of it.
 * <p>
 * Copying is the only way into a type: the element gets a class (a new one derived from its
 * variables when it has none yet) and {@code n} instances of it are created with unique ids and
 * base-linked variable names.
 */
public class DuplicateElementAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public DuplicateElementAction() {
        this(Lookup.EMPTY);
    }

    private DuplicateElementAction(Lookup context) {
        super(Bundle.BTN_duplicate_element());
        this.context = context;
    }

    @Messages("BTN_duplicate_element=Duplicate...")
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) {
            Node node = context.lookup(Node.class);
            if (node != null) {
                project = node.getLookup().lookup(Project.class);
            }
        }
        if (project == null) {
            return;
        }

        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null) {
            return;
        }

        S88Element element = context.lookup(S88Element.class);
        if (element == null && plantModel.getModel() != null) {
            element = plantModel.getModel().getRoot();
        }
        if (element == null) {
            return;
        }

        // The node holds the element it was built with, and the plant model replaces its elements
        // on every reload. Duplicating the stale one would act on a branch that is no longer in the
        // plant, so the element is looked up again by the id the action was invoked on.
        if (element.getId() != null && plantModel.getModel() != null) {
            S88Element live = plantModel.getModel().findById(element.getId()).orElse(null);
            if (live == null) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        "'" + element.getId() + "' is no longer part of the plant.", NotifyDescriptor.ERROR_MESSAGE));
                return;
            }
            element = live;
        }

        try {
            DuplicateElementDialog.duplicate(null, plantModel, element);
        } catch (RuntimeException ex) {
            // A level that cannot be duplicated, or a name the use case rejects, used to vanish
            // into the log: the menu item simply appeared to do nothing.
            Exceptions.printStackTrace(ex);
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    ex.getMessage() != null ? ex.getMessage() : ex.toString(), NotifyDescriptor.ERROR_MESSAGE));
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new DuplicateElementAction(lkp);
    }
}