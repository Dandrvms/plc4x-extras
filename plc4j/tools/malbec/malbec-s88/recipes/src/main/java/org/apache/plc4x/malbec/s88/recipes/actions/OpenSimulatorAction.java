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
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.recipes.simulator.SimulatorTopComponent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

/** Opens the simulator window kept by the host. */
@ActionID(category = "Window",
        id = "org.apache.plc4x.malbec.s88.recipes.actions.OpenSimulatorAction")
@ActionRegistration(displayName = "Simulator", menuText = "Simulator")
@ActionReference(path = "Menu/Window", position = 1500)
public final class OpenSimulatorAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    public OpenSimulatorAction() {
        super("Simulator");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        SimulatorTopComponent.openLast();
    }

    @Override
    public boolean isEnabled() {
        return SimulatorTopComponent.hasOpenable();
    }
}
