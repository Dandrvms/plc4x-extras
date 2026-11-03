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
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.client.SessionOptions;
import org.apache.plc4x.malbec.s88.batch.client.sim.SimulatedSession;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.runtime.BatchRuntimeService;
import org.apache.plc4x.malbec.s88.recipes.editor.RecipeChartScene;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
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
    private final JLabel batchLabel = label("Batch: —");
    private final JLabel recipeLabel = label("Recipe: —");
    private final JLabel unitsLabel = label("Units: —");
    private final JLabel stateLabel = label("State: —");
    private final JLabel connectionLabel = label("Connection: OFFLINE");
    private final JLabel simulated = new JLabel("SIMULATED", SwingConstants.CENTER);
    private final JLabel statusLabel = label(" ");
    private final JLabel breadcrumb = label("Procedure");
    private final JPanel activeList = new JPanel();
    private final JTextArea footer = area(4);
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
    private final Deque<String> recentEvents = new ArrayDeque<>();

    private BatchPayload payload;
    private S88MasterRecipe recipe;
    private SimulatedSession session;
    private BatchRuntimeService service;
    private RuntimeController controller;
    private RuntimeViewModel viewModel;
    private S88RecipeElement owner;
    private BatchSnapshot lastSnapshot;

    public BatchRuntimeTopComponent() {
        initUi();
        setName("Runtime");
        setDisplayName("Runtime");
        setToolTipText("Simulated batch runtime");
    }

    private BatchRuntimeTopComponent(BatchPayload payload, Path outputRoot, double speed)
            throws Exception {
        initUi();
        this.payload = payload;
        this.recipe = payload.control();
        this.viewModel = new RuntimeViewModel(recipe, payload.plant());
        setName("Runtime " + payload.batchId());
        setDisplayName("Runtime " + payload.batchId());
        setToolTipText("Simulated batch " + payload.batchId());
        try {
            session = SimulatedSession.create(payload,
                    SessionOptions.defaults().withSpeed(speed));
            service = new BatchRuntimeService(session.client(), outputRoot, payload);
            service.start();
            controller = new RuntimeController(session.client(), this::applySnapshot);
            showChart(null);
            applySnapshot(service.lastSnapshot());
        } catch (Exception failure) {
            cleanup();
            throw failure;
        }
    }

    /** Opens the only runtime window for this batch, or brings the existing one forward. */
    public static void open(BatchPayload payload, Path outputRoot, double speed) {
        for (TopComponent opened : WindowManager.getDefault().getRegistry().getOpened()) {
            if (opened instanceof BatchRuntimeTopComponent runtime
                    && payload.batchId().equals(runtime.batchId())) {
                runtime.requestActive();
                return;
            }
        }
        BatchRuntimeTopComponent runtime = null;
        try {
            runtime = new BatchRuntimeTopComponent(payload, outputRoot, speed);
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
        header.add(simulated);
        add(header, BorderLayout.NORTH);

        JPanel left = panel("Execution");
        activeList.setLayout(new BoxLayout(activeList, BoxLayout.Y_AXIS));
        activeList.setOpaque(false);
        left.add(new JScrollPane(activeList), BorderLayout.CENTER);

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
        centre.add(new JScrollPane(sceneView), BorderLayout.CENTER);
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
        sceneView.addMouseWheelListener(event -> {
            if (!event.isControlDown()) {
                return;
            }
            double zoom = scene.getZoomFactor();
            zoom = event.getWheelRotation() < 0 ? zoom * 1.1 : zoom / 1.1;
            scene.setZoomFactor(Math.clamp(zoom, 0.2, 4.0));
            event.consume();
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

        JSplitPane leftCentre = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, centre);
        leftCentre.setResizeWeight(0.17);
        leftCentre.setDividerLocation(230);
        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCentre, right);
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

        footer.setEditable(false);
        footer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        footer.setBackground(PANEL);
        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.setOpaque(false);
        south.add(commands, BorderLayout.NORTH);
        south.add(new JScrollPane(footer), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
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

    private static JTextArea area(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private String batchId() {
        return payload == null ? null : payload.batchId();
    }

    private void showChart(S88RecipeElement next) {
        owner = next;
        if (recipe == null) {
            return;
        }
        scene.draw(recipe, owner);
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
        recipeLabel.setText("Recipe: " + (recipe == null ? "—" : recipe.getId()));
        unitsLabel.setText("Units: " + units(snapshot));
        stateLabel.setText("State: " + snapshot.batchState());
        stateLabel.setForeground(snapshot.batchState() == ModuleState.COMPLETE ? NORMAL
                : snapshot.batchState() == ModuleState.ABORTED
                        || snapshot.batchState() == ModuleState.STOPPED ? ALARM : INK);
        boolean stale = controller != null && controller.stale();
        connectionLabel.setText("Connection: " + (snapshot.connected() ? "ONLINE" : "OFFLINE")
                + (stale ? " / SIN DATOS" : ""));
        connectionLabel.setForeground(!snapshot.connected() ? ALARM : stale ? WARNING : NORMAL);

        updateActive(snapshot);
        if (followActive.isSelected()) {
            follow(snapshot);
        }
        updateChart(snapshot);
        updateVariables(snapshot);
        updateCommands(snapshot);
        appendEvents(snapshot.newEvents());
        repaint();
    }

    private void follow(BatchSnapshot snapshot) {
        StepStatus first = snapshot.activeSteps().stream().findFirst().orElse(null);
        String path = first == null ? "" : first.chartPath();
        S88RecipeElement wanted = path == null || path.isBlank() ? null
                : recipe.findElement(path).orElse(null);
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
            return "—";
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

    private void appendEvents(List<BatchEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (BatchEvent event : events) {
            recentEvents.addLast(String.format("%7.2f  %-20s %s  %s", event.simTime(),
                    event.type(), event.elementId() == null ? "" : event.elementId(),
                    event.details() == null ? "" : event.details()));
        }
        while (recentEvents.size() > 5) {
            recentEvents.removeFirst();
        }
        footer.setText(String.join("\n", recentEvents));
    }

    private String units(BatchSnapshot snapshot) {
        return payload == null || payload.unitOfStep().isEmpty() ? "—"
                : String.join(", ", new java.util.LinkedHashSet<>(payload.unitOfStep().values()));
    }

    private void updateActive(BatchSnapshot snapshot) {
        activeList.removeAll();
        if (snapshot.activeSteps().isEmpty()) {
            activeList.add(new JLabel("No active phase."));
        } else {
            for (StepStatus step : snapshot.activeSteps()) {
                JPanel row = new JPanel(new BorderLayout(4, 2));
                row.setOpaque(false);
                JLabel name = new JLabel(step.stepId() + "  " + step.state());
                row.add(name, BorderLayout.NORTH);
                JProgressBar progress = new JProgressBar(0, 100);
                progress.setValue((int) Math.round(step.progress() * 100));
                progress.setStringPainted(true);
                row.add(progress, BorderLayout.CENTER);
                activeList.add(row);
            }
        }
        activeList.revalidate();
        activeList.repaint();
    }

    @Override
    public void componentClosed() {
        cleanup();
        super.componentClosed();
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
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
