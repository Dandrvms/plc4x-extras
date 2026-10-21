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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88TransitionCondition;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;
import org.apache.plc4x.malbec.s88.core.SetBarConditionUseCase;

/**
 * What one bar of a chart waits on: clauses joined with and/or, or a wait.
 * <p>
 * A recipe by class names a base name instead of a piece of equipment, so there is nothing to pick
 * there, and the value box offers the members of an enumeration when the report has one. The dialog
 * only gathers and checks what is chosen; writing the bar is the business of whoever opened it.
 */
final class TransitionConditionDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String AND = "AND";
    private static final String OR = "OR";

    private final transient S88MasterRecipe recipe;
    private final transient S88RecipeElement owner;
    private final transient String barId;
    private final transient S88PlantSnapshot plant;
    private final boolean byClass;

    private final JComboBox<String> equipmentBox = new JComboBox<>();
    private final JComboBox<String> reportBox = new JComboBox<>();
    private final JComboBox<String> operatorBox = new JComboBox<>();
    private final JComboBox<String> valueBox = new JComboBox<>();
    private final JComboBox<String> joiningBox = new JComboBox<>(new String[]{AND, OR});
    private final JSpinner waitSpinner =
            new JSpinner(new SpinnerNumberModel(5, 0, 100000, 1));
    private final DefaultListModel<String> clauseLabels = new DefaultListModel<>();
    private final JList<String> clauseList = new JList<>(clauseLabels);
    private final List<S88TransitionCondition> clauses = new ArrayList<>();
    private final JLabel status = new JLabel(" ");

    private boolean accepted;
    private S88TransitionCondition chosen;
    private boolean choseNothing;
    private boolean reloading;

    TransitionConditionDialog(Window owner, S88MasterRecipe recipe, S88RecipeElement ownerStep,
                              String barId, S88PlantSnapshot plant, String currentCondition) {
        super(owner, "Condition of " + barId, ModalityType.APPLICATION_MODAL);
        this.recipe = recipe;
        this.owner = ownerStep;
        this.barId = barId;
        this.plant = plant;
        this.byClass = recipe.addressesByClass();

        add(one(), BorderLayout.NORTH);
        add(middle(), BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);

        valueBox.setEditable(true);
        for (S88ConditionOperator one : S88ConditionOperator.values()) {
            if (one != S88ConditionOperator.OTHER) {
                operatorBox.addItem(one.getSymbol());
            }
        }
        operatorBox.setSelectedItem("=");
        equipmentBox.addActionListener(event -> {
            if (!reloading) {
                reload();
            }
        });
        reportBox.addActionListener(event -> {
            if (!reloading) {
                reloadValues();
            }
        });
        prefill(currentCondition);
        reload();

        setSize(new Dimension(560, 420));
        setLocationRelativeTo(owner);
    }

    private JPanel one() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        if (!byClass) {
            panel.add(new JLabel("Equipment to read from", SwingConstants.RIGHT));
            panel.add(equipmentBox);
        }
        panel.add(new JLabel("Report to wait on", SwingConstants.RIGHT));
        panel.add(reportBox);
        panel.add(new JLabel("How it compares", SwingConstants.RIGHT));
        panel.add(operatorBox);
        panel.add(new JLabel("Value to wait for", SwingConstants.RIGHT));
        panel.add(valueBox);
        JButton addIt = new JButton("Add this condition");
        addIt.addActionListener(event -> addCondition());
        panel.add(new JLabel("", SwingConstants.RIGHT));
        panel.add(addIt);
        return panel;
    }

    /** The clauses gathered so far, how they are joined, and the wait. */
    private JPanel middle() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        panel.add(new JScrollPane(clauseList), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(6, 4));
        JPanel joining = new JPanel();
        joining.add(new JLabel("Join the ones above with"));
        joining.add(joiningBox);
        JButton remove = new JButton("Remove the picked one");
        remove.addActionListener(event -> removePicked());
        joining.add(remove);
        south.add(joining, BorderLayout.NORTH);

        JPanel waiting = new JPanel();
        waiting.add(new JLabel("or wait"));
        waiting.add(waitSpinner);
        waiting.add(new JLabel("seconds"));
        JButton addWait = new JButton("Add this wait");
        addWait.addActionListener(event -> addWait());
        waiting.add(addWait);
        south.add(waiting, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buttons() {
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        south.add(status, BorderLayout.CENTER);
        JPanel right = new JPanel();
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        right.add(cancel);
        JButton clear = new JButton("Wait on nothing");
        clear.addActionListener(event -> clear());
        right.add(clear);
        JButton save = new JButton("Save condition");
        save.addActionListener(event -> accept());
        right.add(save);
        south.add(right, BorderLayout.EAST);
        return south;
    }

    /**
     * The clauses the bar already waits on, so that opening the dialog changes nothing by itself.
     */
    private void prefill(String currentCondition) {
        S88TransitionCondition existing = S88TransitionCondition.parse(currentCondition);
        if (existing == null) {
            return;
        }
        if (existing.getKind() == S88TransitionCondition.Kind.COMPARISON
                || existing.getKind() == S88TransitionCondition.Kind.WAIT) {
            clauses.add(existing);
            clauseLabels.addElement(existing.toDisplay());
            return;
        }
        for (S88TransitionCondition operand : existing.getOperands()) {
            clauses.add(operand);
            clauseLabels.addElement(operand.toDisplay());
        }
        joiningBox.setSelectedItem(existing.getKind() == S88TransitionCondition.Kind.OR ? OR : AND);
    }

    // ========== Keeping the boxes in step ==========

    /** Offers what a bar of this chart can read from, then the reports of whatever is picked. */
    private void reload() {
        reloading = true;
        try {
            if (!byClass) {
                String picked = (String) equipmentBox.getSelectedItem();
                equipmentBox.removeAllItems();
                for (String equipment : SetBarConditionUseCase.equipmentWithReports(plant, owner)) {
                    equipmentBox.addItem(equipment);
                }
                if (picked != null) {
                    equipmentBox.setSelectedItem(picked);
                }
            }
            reloadReports();
        } finally {
            reloading = false;
        }
    }

    private void reloadReports() {
        reloading = true;
        try {
            String picked = (String) reportBox.getSelectedItem();
            reportBox.removeAllItems();
            List<String> reports = reportsToOffer();
            for (String report : reports) {
                reportBox.addItem(report);
            }
            if (picked != null && reports.contains(picked)) {
                reportBox.setSelectedItem(picked);
            }
            reloadValues();
        } finally {
            reloading = false;
        }
    }

    private List<String> reportsToOffer() {
        if (byClass) {
            return SetBarConditionUseCase.reportsToWaitOn(recipe, owner, barId, plant);
        }
        String equipment = (String) equipmentBox.getSelectedItem();
        return equipment == null
                ? List.of()
                : SetBarConditionUseCase.reportsOf(plant, equipment);
    }

    /** Offers the members of the report's enumeration, or lets the value be typed. */
    private void reloadValues() {
        reloading = true;
        try {
            Object typed = valueBox.isEditable() ? valueBox.getEditor().getItem() : null;
            valueBox.removeAllItems();
            List<String> offered = SetBarConditionUseCase.allowedValues(recipe, owner, barId, plant,
                    (String) equipmentBox.getSelectedItem(), (String) reportBox.getSelectedItem());
            for (String value : offered) {
                valueBox.addItem(value);
            }
            if (typed != null) {
                valueBox.setSelectedItem(typed);
            } else if (!offered.isEmpty()) {
                valueBox.setSelectedItem(offered.get(0));
            }
        } finally {
            reloading = false;
        }
    }

    // ========== Gathering the clauses ==========

    /** Checks the boxes as one comparison and adds it to the list. */
    void addCondition() {
        String equipment = byClass ? null : (String) equipmentBox.getSelectedItem();
        String report = (String) reportBox.getSelectedItem();
        String operator = (String) operatorBox.getSelectedItem();
        String value = typedValue();
        if (report == null || operator == null || value == null || value.isBlank()) {
            status.setText("A condition needs a report, a comparison and a value.");
            return;
        }
        try {
            SetBarConditionUseCase.checkValue(recipe, owner, barId, plant, equipment, report, value);
        } catch (IllegalArgumentException refused) {
            status.setText(refused.getMessage());
            return;
        }
        S88ConditionOperator how = S88ConditionOperator.fromSymbol(operator);
        S88VariableAddress address = byClass
                ? S88VariableAddress.parse("Reports/" + report)
                : S88VariableAddress.of(null, report);
        clauses.add(S88TransitionCondition.comparison(
                S88ConditionExpression.on(equipment, address, how, value)));
        clauseLabels.addElement(clauses.get(clauses.size() - 1).toDisplay());
        status.setText(" ");
    }

    /** Adds a wait for the seconds shown as a clause of its own. */
    void addWait() {
        int seconds = ((Number) waitSpinner.getValue()).intValue();
        S88TransitionCondition wait = S88TransitionCondition.wait(seconds);
        clauses.add(wait);
        clauseLabels.addElement(wait.toDisplay());
        status.setText(" ");
    }

    private void removePicked() {
        int at = clauseList.getSelectedIndex();
        if (at < 0) {
            return;
        }
        clauses.remove(at);
        clauseLabels.remove(at);
    }

    /** Says the bar waits on nothing. */
    void clear() {
        clauses.clear();
        clauseLabels.clear();
        choseNothing = true;
        accepted = true;
        dispose();
    }

    /**
     * Builds the condition and, when it holds together, accepts it.
     */
    void accept() {
        try {
            chosen = build();
        } catch (IllegalArgumentException refused) {
            status.setText(refused.getMessage());
            return;
        }
        accepted = true;
        dispose();
    }

    private S88TransitionCondition build() {
        if (clauses.isEmpty()) {
            return null;
        }
        if (clauses.size() == 1) {
            return clauses.get(0);
        }
        return AND.equals(joiningBox.getSelectedItem())
                ? S88TransitionCondition.and(clauses)
                : S88TransitionCondition.or(clauses);
    }

    private String typedValue() {
        Object editor = valueBox.isEditable() ? valueBox.getEditor().getItem() : valueBox.getSelectedItem();
        return editor == null ? null : editor.toString().trim();
    }

    /** Whether the operator accepted a condition or asked the bar to wait on nothing. */
    boolean wasAccepted() {
        return accepted;
    }

    /**
     * The condition as the text a recipe carries, or {@code null} when the bar waits on nothing.
     */
    String conditionText() {
        return choseNothing || chosen == null ? null : chosen.toText();
    }

    /** The clauses as they are shown, for a test that reads them. */
    List<String> clausesShown() {
        List<String> shown = new ArrayList<>();
        for (int at = 0; at < clauseLabels.size(); at++) {
            shown.add(clauseLabels.get(at));
        }
        return shown;
    }

    /** Picks a report, for a test that drives the boxes without a window. */
    void useReport(String report) {
        reportBox.setSelectedItem(report);
    }

    /** Picks how the report compares, for a test that drives the boxes without a window. */
    void useOperator(String operator) {
        operatorBox.setSelectedItem(operator);
    }

    /** Types a value, for a test that drives the boxes without a window. */
    void useValue(String value) {
        valueBox.setSelectedItem(value);
    }

    /** Picks the equipment a bar reads from, for a test that drives the boxes without a window. */
    void useEquipment(String equipment) {
        equipmentBox.setSelectedItem(equipment);
    }

    /** Says how the clauses are joined, for a test that drives the boxes without a window. */
    void joinWith(String joining) {
        joiningBox.setSelectedItem(joining);
    }

    /** Sets the wait, for a test that drives the boxes without a window. */
    void useWaitSeconds(int seconds) {
        waitSpinner.setValue(seconds);
    }

    /** What the boxes offer as a value, for a test that reads them. */
    List<String> valuesOffered() {
        List<String> offered = new ArrayList<>();
        for (int at = 0; at < valueBox.getItemCount(); at++) {
            offered.add(valueBox.getItemAt(at));
        }
        return offered;
    }

    /** What the dialog says under the tables, for a test that reads it. */
    String statusShown() {
        return status.getText();
    }
}
