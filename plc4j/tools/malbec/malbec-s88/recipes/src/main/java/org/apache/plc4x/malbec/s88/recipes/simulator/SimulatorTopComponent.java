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
package org.apache.plc4x.malbec.s88.recipes.simulator;

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.simulator.SimulatedSession;
import org.apache.plc4x.malbec.s88.recipes.runtime.RuntimeViewModel;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** The floating simulator window. Closing it hides the window but keeps the session alive. */
@NbBundle.Messages("CTL_SimulatorTopComponent=Simulator")
@TopComponent.Description(
        preferredID = "SimulatorTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
public final class SimulatorTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    private static final Map<String, SimulatorTopComponent> OPEN = new LinkedHashMap<>();
    private static SimulatorTopComponent last;

    private final SimulatedSession session;
    private final SimulationControlPanel panel;
    private final String batchId;
    private final AtomicReference<BatchSnapshot> latest = new AtomicReference<>();
    private final Consumer<BatchSnapshot> listener = latest::set;
    private final Timer timer;
    private BatchSnapshot delivered;

    private SimulatorTopComponent(SimulatedSession session, BatchPayload payload,
                                  RuntimeViewModel viewModel) {
        this.session = session;
        this.batchId = payload.batchId();
        this.panel = new SimulationControlPanel(session.control(), payload, viewModel,
                session.snapshot());
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        JButton stop = new JButton("Stop simulator");
        stop.addActionListener(event -> stop());
        south.add(stop, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        setName("Simulator " + payload.batchId());
        setDisplayName("Simulator " + payload.batchId());
        latest.set(session.snapshot());
        delivered = latest.get();
        panel.update(delivered);
        session.addSnapshotListener(listener);
        timer = new Timer(150, event -> refresh());
        timer.setCoalesce(true);
        timer.start();
    }

    public static void open(SimulatedSession session, BatchPayload payload,
                            RuntimeViewModel viewModel) {
        SimulatorTopComponent existing = OPEN.get(payload.batchId());
        if (existing != null) {
            existing.open();
            WindowManager.getDefault().setTopComponentFloating(existing, true);
            existing.requestActive();
            return;
        }
        SimulatorTopComponent simulator = new SimulatorTopComponent(session, payload, viewModel);
        OPEN.put(payload.batchId(), simulator);
        last = simulator;
        if (WindowManager.getDefault().findMode("editor") != null) {
            WindowManager.getDefault().findMode("editor").dockInto(simulator);
        }
        simulator.open();
        WindowManager.getDefault().setTopComponentFloating(simulator, true);
        simulator.requestActive();
    }

    public static void openLast() {
        if (last == null && !OPEN.isEmpty()) {
            last = OPEN.values().iterator().next();
        }
        if (last == null) {
            return;
        }
        last.open();
        WindowManager.getDefault().setTopComponentFloating(last, true);
        last.requestActive();
    }

    public static boolean hasOpenable() {
        return !OPEN.isEmpty();
    }

    private void refresh() {
        BatchSnapshot next = latest.get();
        if (next == null || next == delivered) {
            return;
        }
        delivered = next;
        panel.update(next);
    }

    private void stop() {
        timer.stop();
        session.removeSnapshotListener(listener);
        session.close();
        OPEN.remove(batchId);
        if (last == this) {
            last = null;
        }
        close();
    }

    @Override
    public void componentOpened() {
        timer.start();
        refresh();
        super.componentOpened();
    }

    @Override
    public void componentClosed() {
        // The session belongs to the simulator host, not to this window.
        timer.stop();
        super.componentClosed();
    }
}
