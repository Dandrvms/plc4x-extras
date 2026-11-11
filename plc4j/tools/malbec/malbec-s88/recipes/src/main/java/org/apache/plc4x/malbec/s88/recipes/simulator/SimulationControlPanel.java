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
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.simulator.SimulationControl;
import org.apache.plc4x.malbec.s88.batch.link.VariableCatalog;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;
import org.apache.plc4x.malbec.s88.recipes.runtime.RuntimeViewModel;

/** Controls the clock, variables, failures and link of the simulated plant. */
public final class SimulationControlPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] SPEEDS = {"0.5x", "1x", "5x", "10x", "60x"};

    private final SimulationControl control;
    private final RuntimeViewModel viewModel;
    private final Map<String, VariableCatalog.Definition> definitions;

    private final JCheckBox automatic = new JCheckBox("Automatic process");
    private final JButton clockButton = new JButton("Pause clock");
    private final JComboBox<String> speedBox = new JComboBox<>(SPEEDS);
    private final JLabel decisionLabel = new JLabel("No pending decision");
    private final JComboBox<String> decisionBox = new JComboBox<>();
    private final JButton decideButton = new JButton("Decide");
    private final JComboBox<String> failureModule = new JComboBox<>();
    private final JSpinner failureCode = new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));
    private final JCheckBox linkUp = new JCheckBox("Link up", true);
    private final JComboBox<String> activeStepBox = new JComboBox<>();
    private final List<String> activeModuleIds = new ArrayList<>();
    private final JButton completeButton = new JButton("Complete step");
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel variableRows = new DefaultTableModel(
            new Object[]{"Variable", "Value"}, 0) {
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
            if (SimulationControlPanel.this.applyVariable(variableNames.get(row), text)) {
                super.setValueAt(text, row, column);
            }
        }
    };
    private final JTable variableTable = new JTable(variableRows);
    private final List<String> variableNames = new ArrayList<>();
    private final Map<String, Integer> rowByVariable = new LinkedHashMap<>();
    private final List<String> currentPhaseLabels = new ArrayList<>();
    private final List<String> currentDecisionCandidates = new ArrayList<>();

    private boolean updating;

    SimulationControlPanel(SimulationControl control, BatchPayload payload,
                           RuntimeViewModel viewModel, BatchSnapshot initial) {
        this(control, VariableCatalog.of(payload), modulesOf(payload), viewModel, initial);
    }

    SimulationControlPanel(SimulationControl control,
                           Map<String, VariableCatalog.Definition> definitions,
                           List<String> modules, RuntimeViewModel viewModel,
                           BatchSnapshot initial) {
        super(new BorderLayout());
        this.control = control;
        this.viewModel = viewModel;
        this.definitions = definitions == null ? Map.of() : definitions;

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
        decideButton.addActionListener(event -> {
            Object selected = decisionBox.getSelectedItem();
            if (selected != null) {
                control.decide(selected.toString());
                status.setText("Decision sent: " + selected);
            }
        });
        completeButton.addActionListener(event -> {
            int at = activeStepBox.getSelectedIndex();
            if (at >= 0 && at < activeModuleIds.size()) {
                control.completeStep(activeModuleIds.get(at));
                if (!automatic.isSelected()) {
                    control.stepTicks(1);
                }
                status.setText("Step completed: " + activeStepBox.getSelectedItem());
            }
        });
        automatic.addActionListener(event -> {
            control.setAutomatic(automatic.isSelected());
            if (automatic.isSelected()) {
                control.play();
            } else {
                control.pauseClock();
            }
            updateClock();
            status.setText(automatic.isSelected()
                    ? "Automatic process started" : "Manual process; clock paused");
        });
        linkUp.addActionListener(event -> {
            control.setLinkUp(linkUp.isSelected());
            status.setText(linkUp.isSelected() ? "Link restored" : "Link down");
        });

        speedBox.setSelectedItem(labelOf(control.speed()));
        control.setAutomatic(automatic.isSelected());
        if (!automatic.isSelected()) {
            control.pauseClock();
        }
        updateClock();
        update(initial);
    }

    private JPanel controls(List<String> modules) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel clock = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        clock.setBorder(BorderFactory.createTitledBorder("Clock"));
        automatic.setToolTipText(
                "Applies follow rules and completes phases automatically. Starts the clock.");
        clock.add(clockButton);
        JButton step = new JButton("Step 1 tick");
        step.addActionListener(event -> {
            control.pauseClock();
            control.stepTicks(1);
            updateClock();
        });
        clock.add(step);
        JButton next = new JButton("Next event");
        next.setToolTipText("Advance until the next batch or process event.");
        next.addActionListener(event -> {
            control.pauseClock();
            boolean reached = control.stepToNextEvent();
            updateClock();
            status.setText(reached ? "Next event reached"
                    : "No event reached; use Step 1 tick or Complete step");
        });
        clock.add(next);
        clock.add(new JLabel("Speed:"));
        clock.add(speedBox);
        clock.add(automatic);
        panel.add(clock);

        JPanel decision = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        decision.setBorder(BorderFactory.createTitledBorder("Branch decision"));
        decision.add(new JLabel("When reached:"));
        JComboBox<String> decisionMode = new JComboBox<>(
                new String[]{"Continue automatically", "Wait for operator"});
        decisionMode.setToolTipText(
                "Select Wait for operator before reaching a branch with more than one way.");
        decisionMode.addActionListener(event -> {
            boolean manual = "Wait for operator".equals(decisionMode.getSelectedItem());
            control.setDecisionMode(manual ? DecisionMode.MANUAL : DecisionMode.AUTO);
            status.setText(manual ? "Branches wait for operator"
                    : "Branches continue automatically");
        });
        decideButton.setToolTipText("Enabled when a branch is waiting for a decision.");
        decision.add(decisionMode);
        decision.add(decisionLabel);
        decision.add(decisionBox);
        decision.add(decideButton);
        panel.add(decision);

        JPanel phase = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        phase.setBorder(BorderFactory.createTitledBorder("Active phase"));
        phase.add(new JLabel("Step:"));
        phase.add(activeStepBox);
        phase.add(completeButton);
        panel.add(phase);

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
            if (!activeStepBox.isPopupVisible()) {
                updatePhase(next);
            }
            if (!decisionBox.isPopupVisible()) {
                updateDecision(next);
            }
            if (!variableTable.isEditing()) {
                updateVariables(next);
            }
        } finally {
            updating = false;
        }
    }

    private void updateClock() {
        String text = control.isClockRunning() ? "Pause clock" : "Run clock";
        if (!text.equals(clockButton.getText())) {
            clockButton.setText(text);
        }
    }

    private void updatePhase(BatchSnapshot next) {
        List<String> labels = new ArrayList<>();
        List<String> modules = new ArrayList<>();
        for (StepStatus step : next.activeSteps()) {
            if (step.moduleId() == null || step.moduleId().isBlank()) {
                continue;
            }
            labels.add(step.stepId() + " [" + step.moduleId() + "]");
            modules.add(step.moduleId());
        }
        if (labels.equals(currentPhaseLabels)) {
            return;
        }
        Object picked = activeStepBox.getSelectedItem();
        currentPhaseLabels.clear();
        currentPhaseLabels.addAll(labels);
        activeModuleIds.clear();
        activeModuleIds.addAll(modules);
        activeStepBox.removeAllItems();
        for (String label : labels) {
            activeStepBox.addItem(label);
        }
        if (picked != null && labels.contains(picked.toString())) {
            activeStepBox.setSelectedItem(picked);
        }
        boolean active = !activeModuleIds.isEmpty();
        activeStepBox.setEnabled(active);
        completeButton.setEnabled(active);
    }

    private void updateDecision(BatchSnapshot next) {
        List<String> candidates = next.pendingDecision().candidates();
        if (candidates.equals(currentDecisionCandidates)) {
            return;
        }
        currentDecisionCandidates.clear();
        currentDecisionCandidates.addAll(candidates);
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
        Map<String, Integer> nextRows = new LinkedHashMap<>();
        for (RuntimeViewModel.VariableRow row : rowsOf(next)) {
            VariableCatalog.Definition definition = definitions.get(row.variable());
            if (definition == null) {
                continue;
            }
//            String unit = row.unit() == null ? "" : row.unit();
            Integer at = rowByVariable.get(row.variable());
            if (at == null || at < 0 || at >= variableRows.getRowCount()) {
                at = variableRows.getRowCount();
                variableRows.addRow(new Object[]{row.variable(), row.value()});
                variableNames.add(row.variable());
            } else {
//                setIfChanged(at, 0, row.module());
                setIfChanged(at, 0, row.value());
//                setIfChanged(at, 3, unit);
//                setIfChanged(at, 4, definition.type().name());
            }
            nextRows.put(row.variable(), at);
        }
        for (int at = variableRows.getRowCount() - 1; at >= 0; at--) {
            String variable = variableNames.get(at);
            if (!nextRows.containsKey(variable)) {
                variableRows.removeRow(at);
                variableNames.remove(at);
            }
        }
        rowByVariable.clear();
        for (int at = 0; at < variableNames.size(); at++) {
            rowByVariable.put(variableNames.get(at), at);
        }
    }

    private void setIfChanged(int row, int column, Object value) {
        Object current = variableRows.getValueAt(row, column);
        if (!java.util.Objects.equals(current, value)) {
            variableRows.setValueAt(value, row, column);
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
