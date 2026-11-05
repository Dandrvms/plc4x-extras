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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.client.SimulationControl;
import org.apache.plc4x.malbec.s88.batch.link.VariableCatalog;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;

/** Controls the clock, variables, failures and link of the simulated plant. */
final class SimulationControlDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String TIMED = "Timed";
    private static final String STEP_BY_STEP = "Step by step";
    private static final String[] SPEEDS = {"0.5x", "1x", "5x", "10x", "60x"};

    private final SimulationControl control;
    private final RuntimeViewModel viewModel;
    private final Map<String, VariableCatalog.Definition> definitions;

    private final JButton clockButton = new JButton("Pause clock");
    private final JComboBox<String> speedBox = new JComboBox<>(SPEEDS);
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{TIMED, STEP_BY_STEP});
    private final JLabel decisionLabel = new JLabel("No pending decision");
    private final JComboBox<String> decisionBox = new JComboBox<>();
    private final JButton decideButton = new JButton("Decide");
    private final JComboBox<String> failureModule = new JComboBox<>();
    private final JSpinner failureCode = new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));
    private final JCheckBox linkUp = new JCheckBox("Link up", true);
    private final JCheckBox alwaysOnTop = new JCheckBox("Always on top");
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel variableRows = new DefaultTableModel(
            new Object[]{"Module", "Variable", "Value", "Unit", "Type"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 2;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (updating || column != 2 || row < 0 || row >= variableNames.size()) {
                return;
            }
            String text = value == null ? "" : value.toString();
            if (SimulationControlDialog.this.applyVariable(variableNames.get(row), text)) {
                super.setValueAt(text, row, column);
            }
        }
    };
    private final JTable variableTable = new JTable(variableRows);
    private final List<String> variableNames = new ArrayList<>();

    private boolean updating;

    SimulationControlDialog(Window owner, SimulationControl control, BatchPayload payload,
                            RuntimeViewModel viewModel, BatchSnapshot initial) {
        this(owner, control, VariableCatalog.of(payload), modulesOf(payload), viewModel, initial);
    }

    SimulationControlDialog(Window owner, SimulationControl control,
                            Map<String, VariableCatalog.Definition> definitions,
                            List<String> modules, RuntimeViewModel viewModel,
                            BatchSnapshot initial) {
        super(owner, "Simulation control", ModalityType.MODELESS);
        this.control = control;
        this.viewModel = viewModel;
        this.definitions = definitions == null ? Map.of() : definitions;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(controls(modules), BorderLayout.NORTH);
        body.add(new JScrollPane(variableTable), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        body.add(status, BorderLayout.SOUTH);
        setLayout(new BorderLayout());
        add(body, BorderLayout.CENTER);

        clockButton.addActionListener(event -> {
            if (control.isClockRunning()) {
                control.pauseClock();
            } else {
                control.play();
            }
            updateClock();
        });
        speedBox.addActionListener(event -> {
            if (!updating) {
                control.setSpeed(speedOf(String.valueOf(speedBox.getSelectedItem())));
                status.setText("Speed " + speedBox.getSelectedItem());
            }
        });
        modeBox.addActionListener(event -> {
            if (!updating) {
                if (STEP_BY_STEP.equals(modeBox.getSelectedItem())) {
                    control.pauseClock();
                } else {
                    control.play();
                }
                updateClock();
            }
        });
        decideButton.addActionListener(event -> {
            Object selected = decisionBox.getSelectedItem();
            if (selected != null) {
                control.decide(selected.toString());
                status.setText("Decision sent: " + selected);
            }
        });
        alwaysOnTop.addActionListener(event -> setAlwaysOnTop(alwaysOnTop.isSelected()));
        linkUp.addActionListener(event -> {
            control.setLinkUp(linkUp.isSelected());
            status.setText(linkUp.isSelected() ? "Link restored" : "Link down");
        });

        speedBox.setSelectedItem(labelOf(control.speed()));
        updateClock();
        update(initial);
        setSize(new Dimension(980, 620));
        setLocationRelativeTo(owner);
    }

    private JPanel controls(List<String> modules) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel clock = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        clock.setBorder(BorderFactory.createTitledBorder("Clock"));
        clock.add(clockButton);
        JButton step = new JButton("Step 1 tick");
        step.addActionListener(event -> {
            control.pauseClock();
            control.stepTicks(1);
            updateClock();
        });
        clock.add(step);
        JButton next = new JButton("Next event");
        next.addActionListener(event -> {
            control.pauseClock();
            control.stepToNextEvent();
            updateClock();
        });
        clock.add(next);
        clock.add(new JLabel("Speed:"));
        clock.add(speedBox);
        clock.add(new JLabel("Mode:"));
        clock.add(modeBox);
        clock.add(alwaysOnTop);
        panel.add(clock);

        JPanel decision = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        decision.setBorder(BorderFactory.createTitledBorder("Branch decision"));
        decision.add(new JLabel("Mode:"));
        JComboBox<String> decisionMode = new JComboBox<>(new String[]{"Automatic", "Manual"});
        decisionMode.addActionListener(event -> control.setDecisionMode(
                "Manual".equals(decisionMode.getSelectedItem())
                        ? DecisionMode.MANUAL : DecisionMode.AUTO));
        decision.add(decisionMode);
        decision.add(decisionLabel);
        decision.add(decisionBox);
        decision.add(decideButton);
        panel.add(decision);

        JPanel plant = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        plant.setBorder(BorderFactory.createTitledBorder("Plant"));
        plant.add(new JLabel("Failure module:"));
        for (String module : modules) {
            failureModule.addItem(module);
        }
        plant.add(failureModule);
        plant.add(new JLabel("Code:"));
        plant.add(failureCode);
        JButton inject = new JButton("Inject failure");
        inject.addActionListener(event -> {
            String module = failureModule.getSelectedItem() == null ? ""
                    : failureModule.getSelectedItem().toString();
            int code = ((Number) failureCode.getValue()).intValue();
            if (module.isBlank()) {
                JOptionPane.showMessageDialog(this, "Pick a module first.",
                        "Inject failure", JOptionPane.WARNING_MESSAGE);
                return;
            }
            control.injectFailure(module, code);
            status.setText("Failure " + code + " injected in " + module);
        });
        plant.add(inject);
        JButton clear = new JButton("Clear failure");
        clear.addActionListener(event -> {
            String module = failureModule.getSelectedItem() == null ? ""
                    : failureModule.getSelectedItem().toString();
            if (!module.isBlank()) {
                control.clearFailure(module);
                status.setText("Failure cleared in " + module);
            }
        });
        plant.add(clear);
        plant.add(linkUp);
        panel.add(plant);
        return panel;
    }

    void update(BatchSnapshot next) {
        if (next == null) {
            return;
        }
        updating = true;
        try {
            updateClock();
            updateDecision(next);
            if (!variableTable.isEditing()) {
                updateVariables(next);
            }
        } finally {
            updating = false;
        }
    }

    private void updateClock() {
        clockButton.setText(control.isClockRunning() ? "Pause clock" : "Run clock");
    }

    private void updateDecision(BatchSnapshot next) {
        List<String> candidates = next.pendingDecision().candidates();
        Object picked = decisionBox.getSelectedItem();
        decisionBox.removeAllItems();
        for (String candidate : candidates) {
            decisionBox.addItem(candidate);
        }
        if (picked != null && candidates.contains(picked.toString())) {
            decisionBox.setSelectedItem(picked);
        }
        boolean waiting = !candidates.isEmpty();
        decisionLabel.setText(waiting ? "Decision needed:" : "No pending decision");
        decisionBox.setEnabled(waiting);
        decideButton.setEnabled(waiting);
    }

    private void updateVariables(BatchSnapshot next) {
        variableRows.setRowCount(0);
        variableNames.clear();
        for (RuntimeViewModel.VariableRow row : rowsOf(next)) {
            VariableCatalog.Definition definition = definitions.get(row.variable());
            if (definition == null) {
                continue;
            }
            variableRows.addRow(new Object[]{row.module(), row.variable(), row.value(),
                    row.unit() == null ? "" : row.unit(), definition.type().name()});
            variableNames.add(row.variable());
        }
    }

    private List<RuntimeViewModel.VariableRow> rowsOf(BatchSnapshot next) {
        if (viewModel != null) {
            return viewModel.variables(next, "");
        }
        List<RuntimeViewModel.VariableRow> rows = new ArrayList<>();
        for (Map.Entry<String, VariableSample> entry : next.variables().entrySet()) {
            VariableSample sample = entry.getValue();
            rows.add(new RuntimeViewModel.VariableRow("", entry.getKey(),
                    sample.value() == null ? "" : sample.value().toString(), sample.unit(),
                    sample.quality(), sample.sourceTime()));
        }
        return rows;
    }

    boolean applyVariable(String publishedName, String text) {
        VariableCatalog.Definition definition = definitions.get(publishedName);
        if (definition == null) {
            status.setText("Unknown variable: " + publishedName);
            return false;
        }
        Object value;
        try {
            value = valueOf(definition, text);
        } catch (IllegalArgumentException refused) {
            status.setText(refused.getMessage());
            return false;
        }
        control.setVariable(publishedName, value);
        status.setText("Set " + publishedName + " = " + text);
        return true;
    }

    private static Object valueOf(VariableCatalog.Definition definition, String text) {
        String trimmed = text == null ? "" : text.trim();
        return switch (definition.type()) {
            case INTEGER -> {
                try {
                    yield Long.valueOf(trimmed);
                } catch (NumberFormatException notANumber) {
                    throw new IllegalArgumentException("'" + text + "' is not an integer.");
                }
            }
            case REAL -> {
                try {
                    yield Double.valueOf(trimmed);
                } catch (NumberFormatException notANumber) {
                    throw new IllegalArgumentException("'" + text + "' is not a number.");
                }
            }
            case ENUMERATION -> {
                if (!definition.values().isEmpty()
                        && definition.values().stream().noneMatch(v -> v.equalsIgnoreCase(trimmed))) {
                    throw new IllegalArgumentException("Value has to be one of "
                            + String.join(", ", definition.values()) + ".");
                }
                yield trimmed;
            }
            case STRING -> trimmed;
        };
    }

    private static List<String> modulesOf(BatchPayload payload) {
        Set<String> modules = new LinkedHashSet<>();
        if (payload != null) {
            modules.addAll(payload.moduleOfStep().values());
            modules.addAll(payload.unitOfStep().values());
        }
        return List.copyOf(modules);
    }

    private static String labelOf(double speed) {
        for (String label : SPEEDS) {
            if (Math.abs(speedOf(label) - speed) < 0.001) {
                return label;
            }
        }
        return "1x";
    }

    private static double speedOf(String label) {
        return switch (label == null ? "" : label) {
            case "0.5x" -> 0.5;
            case "5x" -> 5.0;
            case "10x" -> 10.0;
            case "60x" -> 60.0;
            default -> 1.0;
        };
    }
}
