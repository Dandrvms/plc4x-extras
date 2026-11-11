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
package org.apache.plc4x.malbec.s88.recipes.runtime;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.client.BatchClient;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.runtime.BatchRuntimeService;
import org.apache.plc4x.malbec.s88.batch.runtime.report.RunFolder;
import org.apache.plc4x.malbec.s88.recipes.editor.RecipeChartScene;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.Workspace;
import org.openide.windows.WindowManager;

/** A read-only runtime view of one simulated batch. */
@NbBundle.Messages("CTL_BatchRuntimeTopComponent=Runtime")
@TopComponent.Description(
        preferredID = "BatchRuntimeTopComponent",
        iconBase = "org/apache/plc4x/malbec/s88/recipes/nodes/recipe.png",
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
public final class BatchRuntimeTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;

    private static final Color PAGE = new Color(0xEC, 0xEF, 0xEF);
    private static final Color PANEL = new Color(0xF7, 0xF8, 0xF8);
    private static final Color INK = new Color(0x25, 0x2B, 0x2B);
    private static final Color MUTED = new Color(0x5B, 0x63, 0x63);
    private static final Color NORMAL = new Color(0x2F, 0x6F, 0x8F);
    private static final Color WARNING = new Color(0x9A, 0x62, 0x00);
    private static final Color ALARM = new Color(0xA8, 0x2A, 0x2A);

    private final RecipeChartScene scene = new RecipeChartScene();
    private final JComponent sceneView = scene.createView();
    private final JLabel batchLabel = label("Batch: ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â");
    private final JLabel recipeLabel = label("Recipe: ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â");
    private final JLabel unitsLabel = label("Units: ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â");
    private final JLabel stateLabel = label("State: ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â");
    private final JLabel connectionLabel = label("Connection: OFFLINE");
    private final JLabel activeStepLabel = label("Active step: ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â");
    private final JLabel simulated = new JLabel("SIMULATED", SwingConstants.CENTER);
    private final JLabel statusLabel = label(" ");
    private final JLabel breadcrumb = label("Procedure");
    private final JButton outputButton = new JButton("Output");
    private final JTextField moduleFilter = new JTextField(16);
    private final JCheckBox followActive = new JCheckBox("Follow active phase");
    private final DefaultTableModel variables = new DefaultTableModel(
            new Object[]{"Module", "Variable", "Value", "Unit", "Quality", "Age"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable variableTable = new JTable(variables);
    private final Map<BatchCommand, JButton> commandButtons = new LinkedHashMap<>();
    private final List<BatchEvent> eventHistory = new ArrayList<>();
    private final Set<String> seenEvents = new java.util.LinkedHashSet<>();

    private BatchPayload payload;
    private S88MasterRecipe recipe;
    private BatchRuntimeService service;
    private RuntimeController controller;
    private RuntimeViewModel viewModel;
    private S88RecipeElement owner;
    private BatchSnapshot lastSnapshot;
    private int finishedRun = -1;

    public BatchRuntimeTopComponent() {
        initUi();
        setName("Runtime");
        setDisplayName("Runtime");
        setToolTipText("Simulated batch runtime");
    }

    private BatchRuntimeTopComponent(BatchPayload payload, BatchClient client, Path outputRoot)
            throws Exception {
        initUi();
        this.payload = payload;
        this.recipe = payload.control();
        this.viewModel = new RuntimeViewModel(recipe, payload.plant());
        setName("Runtime " + payload.batchId());
        setDisplayName("Runtime " + payload.batchId());
        setToolTipText("Simulated batch " + payload.batchId());
        try {
            outputButton.setEnabled(true);
            service = new BatchRuntimeService(client, outputRoot, payload);
            service.start();
            controller = new RuntimeController(client, this::applySnapshot);
            showChart(null);
            applySnapshot(service.lastSnapshot());
        } catch (Exception failure) {
            cleanup();
            throw failure;
        }
    }

    /** Opens the only runtime window for this batch, or brings the existing one forward. */
    public static void open(BatchPayload payload, BatchClient client, Path outputRoot) {
        for (TopComponent opened : WindowManager.getDefault().getRegistry().getOpened()) {
            if (opened instanceof BatchRuntimeTopComponent runtime
                    && payload.batchId().equals(runtime.batchId())) {
                runtime.requestActive();
                return;
            }
        }
        BatchRuntimeTopComponent runtime = null;
        try {
            runtime = new BatchRuntimeTopComponent(payload, client, outputRoot);
            WindowManager.getDefault().findMode("editor").dockInto(runtime);
            runtime.open();
            runtime.requestActive();
        } catch (Exception failure) {
            if (runtime != null) {
                runtime.cleanup();
            }
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    failure.getMessage(), "Runtime could not start", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initUi() {
        setLayout(new BorderLayout(8, 8));
        setBackground(PAGE);
        scene.setReadOnly(true);
        simulated.setOpaque(true);
        simulated.setBackground(new Color(0xE1, 0xEC, 0xF3));
        simulated.setForeground(new Color(0x20, 0x4A, 0x63));
        simulated.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        header.setOpaque(false);
        header.add(batchLabel);
        header.add(recipeLabel);
        header.add(unitsLabel);
        header.add(stateLabel);
        header.add(connectionLabel);
        header.add(activeStepLabel);
        header.add(simulated);
        header.add(outputButton);
        outputButton.setEnabled(false);
        outputButton.addActionListener(event -> BatchOutputTopComponent.showFor(this));
        add(header, BorderLayout.NORTH);

        JPanel centre = new JPanel(new BorderLayout(6, 6));
        centre.setOpaque(false);
        JPanel trail = new JPanel(new BorderLayout(6, 0));
        trail.setOpaque(false);
        JButton up = new JButton("Up");
        up.addActionListener(event -> showChart(owner == null ? null : owner.getParent()));
        trail.add(up, BorderLayout.WEST);
        trail.add(breadcrumb, BorderLayout.CENTER);
        trail.add(followActive, BorderLayout.EAST);
        centre.add(trail, BorderLayout.NORTH);
        JScrollPane sceneScroll = new JScrollPane(sceneView);
        installWheel(sceneView, scene, sceneScroll);
        centre.add(sceneScroll, BorderLayout.CENTER);
        followActive.addActionListener(event -> {
            if (followActive.isSelected() && lastSnapshot != null) {
                applySnapshot(lastSnapshot);
            }
        });
        sceneView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || recipe == null) {
                    return;
                }
                String node = scene.nodeAtInView(sceneView, event.getPoint());
                S88RecipeElement element = node == null ? null
                        : recipe.findElement(node).orElse(null);
                if (element != null && element.getProcedureLogic() != null) {
                    showChart(element);
                }
            }
        });

        JPanel right = panel("Equipment / variables");
        moduleFilter.setToolTipText("Filter by module");
        moduleFilter.addActionListener(event -> applySnapshot(lastSnapshot));
        JPanel filter = new JPanel(new BorderLayout(4, 0));
        filter.setOpaque(false);
        filter.add(new JLabel("Module:"), BorderLayout.WEST);
        filter.add(moduleFilter, BorderLayout.CENTER);
        right.add(filter, BorderLayout.NORTH);
        variableTable.setFillsViewportHeight(true);
        variableTable.setAutoCreateRowSorter(true);
        variableTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(table, value,
                        selected, focused, row, column);
                if (!selected) {
                    String quality = String.valueOf(table.getValueAt(row, 4));
                    component.setForeground("BAD".equals(quality) ? ALARM
                            : "STALE".equals(quality) ? WARNING : INK);
                }
                return component;
            }
        });
        right.add(new JScrollPane(variableTable), BorderLayout.CENTER);

        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centre, right);
        all.setResizeWeight(0.75);
        all.setDividerLocation(760);
        add(all, BorderLayout.CENTER);

        JPanel commands = new JPanel(new BorderLayout(8, 0));
        commands.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        buttons.setOpaque(false);
        for (BatchCommand command : BatchCommand.values()) {
            JButton button = new JButton(command.name());
            button.addActionListener(event -> command(command));
            if (command == BatchCommand.ABORT) {
                button.setForeground(ALARM);
            } else if (command == BatchCommand.STOP) {
                button.setForeground(WARNING);
            }
            commandButtons.put(command, button);
            buttons.add(button);
        }
        statusLabel.setForeground(MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        commands.add(buttons, BorderLayout.WEST);
        commands.add(statusLabel, BorderLayout.CENTER);
        add(commands, BorderLayout.SOUTH);
    }

    private static JPanel panel(String title) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(true);
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC7, 0xCE, 0xCE)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JLabel heading = new JLabel(title);
        heading.setForeground(MUTED);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(INK);
        return label;
    }

    private String batchId() {
        return payload == null ? null : payload.batchId();
    }

    private void showChart(S88RecipeElement next) {
        owner = next;
        if (recipe == null) {
            return;
        }
        scene.draw(recipe, owner, payload == null ? null : payload.plant());
        scene.setReadOnly(true);
        breadcrumb.setText(crumb());
        if (lastSnapshot != null) {
            updateChart(lastSnapshot);
        }
        sceneView.revalidate();
        sceneView.repaint();
    }

    private String crumb() {
        StringBuilder text = new StringBuilder(recipe == null ? "Procedure" : recipe.getId());
        Deque<String> names = new ArrayDeque<>();
        for (S88RecipeElement step = owner; step != null; step = step.getParent()) {
            names.addFirst(step.getId());
        }
        for (String name : names) {
            text.append(" > ").append(name);
        }
        return text.toString();
    }

    private void applySnapshot(BatchSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        lastSnapshot = snapshot;
        batchLabel.setText("Batch: " + snapshot.batchId());
        recipeLabel.setText("Recipe: " + (recipe == null ? "ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â" : recipe.getId()));
        unitsLabel.setText("Units: " + units(snapshot));
        activeStepLabel.setText("Active step: " + (viewModel == null
                ? "ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â" : viewModel.activeStepLabel(snapshot)));
        stateLabel.setText("State: " + snapshot.batchState());
        stateLabel.setForeground(snapshot.batchState() == ModuleState.COMPLETE ? NORMAL
                : snapshot.batchState() == ModuleState.ABORTED
                        || snapshot.batchState() == ModuleState.STOPPED ? ALARM : INK);
        boolean stale = controller != null && controller.stale();
        connectionLabel.setText("Connection: " + (snapshot.connected() ? "ONLINE" : "OFFLINE")
                + (stale ? " / SIN DATOS" : ""));
        connectionLabel.setForeground(!snapshot.connected() ? ALARM : stale ? WARNING : NORMAL);

        if (followActive.isSelected()) {
            follow(snapshot);
        }
        updateChart(snapshot);
        updateVariables(snapshot);
        updateCommands(snapshot);
        BatchOutputTopComponent.publish(this, appendEvents(snapshot.newEvents()));
        if (snapshot.batchState().kind() == ModuleState.Kind.FINAL) {
            finished(snapshot);
        }
        repaint();
    }

    private void follow(BatchSnapshot snapshot) {
        String path = viewModel == null ? "" : viewModel.activeChartPath(snapshot);
        S88RecipeElement wanted = path.isBlank() ? null : recipe.findElement(path).orElse(null);
        if (wanted != owner) {
            showChart(wanted);
        }
    }

    private void updateChart(BatchSnapshot snapshot) {
        if (viewModel == null) {
            return;
        }
        RuntimeViewModel.ChartState state = viewModel.chartState(owner, snapshot);
        scene.setExecution(state.states(), state.firedTransitions(),
                controller != null && controller.stale());
        scene.setBadges(state.badges());
        sceneView.repaint();
    }

    private void updateVariables(BatchSnapshot snapshot) {
        variables.setRowCount(0);
        if (viewModel == null) {
            return;
        }
        String filter = moduleFilter.getText() == null ? "" : moduleFilter.getText().trim();
        Instant now = Instant.now();
        for (RuntimeViewModel.VariableRow row : viewModel.variables(snapshot, filter)) {
            variables.addRow(new Object[]{row.module(), row.variable(), row.value(),
                    row.unit() == null ? "" : row.unit(), row.quality().name(), age(row, now)});
        }
    }

    private static String age(RuntimeViewModel.VariableRow row, Instant now) {
        if (row.sourceTime() == null) {
            return "ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â";
        }
        long seconds = Math.max(0, Duration.between(row.sourceTime(), now).toSeconds());
        return seconds + " s";
    }

    private void updateCommands(BatchSnapshot snapshot) {
        Set<BatchCommand> allowed = viewModel == null ? Set.of()
                : viewModel.commands(snapshot);
        for (Map.Entry<BatchCommand, JButton> entry : commandButtons.entrySet()) {
            entry.getValue().setEnabled(snapshot.connected() && allowed.contains(entry.getKey()));
        }
    }

    private void command(BatchCommand command) {
        if (command == BatchCommand.ABORT && JOptionPane.showConfirmDialog(this,
                "Abort the batch?", "Abort", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        CommandResult result = service.sendCommand(command);
        statusLabel.setText(result.accepted() ? "Accepted: " + command
                : "Rejected: " + result.reason());
        statusLabel.setForeground(result.accepted() ? NORMAL : ALARM);
    }

    private List<BatchEvent> appendEvents(List<BatchEvent> events) {
        List<BatchEvent> added = new ArrayList<>();
        if (events == null || events.isEmpty()) {
            return added;
        }
        for (BatchEvent event : events) {
            if (!seenEvents.add(event.run() + ":" + event.seq())) {
                continue;
            }
            added.add(event);
            eventHistory.add(event);
        }
        return added;
    }

    private static void installWheel(JComponent view, RecipeChartScene chart,
                                     JScrollPane scroll) {
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        view.addMouseWheelListener(event -> {
            if (event.isControlDown()) {
                double zoom = chart.getZoomFactor();
                zoom = event.getWheelRotation() < 0 ? zoom * 1.1 : zoom / 1.1;
                chart.setZoomFactor(Math.clamp(zoom, 0.2, 4.0));
                event.consume();
                return;
            }
            JScrollBar bar = event.isShiftDown()
                    ? scroll.getHorizontalScrollBar() : scroll.getVerticalScrollBar();
            bar.setValue(bar.getValue() + event.getUnitsToScroll() * bar.getUnitIncrement());
            event.consume();
        });
    }

    private String units(BatchSnapshot snapshot) {
        return payload == null || payload.unitOfStep().isEmpty() ? "ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â"
                : String.join(", ", new java.util.LinkedHashSet<>(payload.unitOfStep().values()));
    }

    @Override
    public boolean canClose() {
        ModuleState state = lastSnapshot == null ? null : lastSnapshot.batchState();
        if (!needsCloseConfirmation(state)) {
            return true;
        }
        return JOptionPane.showConfirmDialog(this,
                "The simulated batch is still active. Closing this window will stop it and mark the run as INTERRUPTED. Close anyway?",
                "Close runtime", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override
    public boolean canClose(Workspace workspace, boolean last) {
        return canClose();
    }

    static boolean needsCloseConfirmation(ModuleState state) {
        return state != null && state.isActive();
    }

    @Override
    public void componentClosed() {
        cleanup();
        super.componentClosed();
    }

    private void finished(BatchSnapshot snapshot) {
        if (finishedRun == snapshot.run()) {
            return;
        }
        finishedRun = snapshot.run();
        RunFolder folder = service == null ? null : service.currentRunFolder();
        new BatchFinishedDialog(SwingUtilities.getWindowAncestor(this), snapshot,
                folder == null ? null : folder.reportFile(),
                folder == null ? null : folder.directory(),
                eventHistory).setVisible(true);
    }

    String outputBatchId() {
        return batchId();
    }

    List<BatchEvent> outputEvents() {
        return List.copyOf(eventHistory);
    }

    Path reportFile() {
        RunFolder folder = service == null ? null : service.currentRunFolder();
        return folder == null ? null : folder.reportFile();
    }

    Path runFolder() {
        RunFolder folder = service == null ? null : service.currentRunFolder();
        return folder == null ? null : folder.directory();
    }

    private void cleanup() {
        if (controller != null) {
            controller.close();
            controller = null;
        }
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
