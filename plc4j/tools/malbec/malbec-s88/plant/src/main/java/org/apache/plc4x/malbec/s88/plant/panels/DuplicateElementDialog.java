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
package org.apache.plc4x.malbec.s88.plant.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.core.BaseNameSupport;
import org.apache.plc4x.malbec.s88.core.DuplicateElementUseCase;
import org.apache.plc4x.malbec.s88.core.VariableKeySupport;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.util.Exceptions;


/**
 * Makes an equipment type out of an element and creates the copies of it.
 * <p>
 * The dialog previews the names the copies will get and the base name each variable is derived
 * from, both of which can be adjusted before the copies are created.
 */
public final class DuplicateElementDialog extends JDialog {

    private static final Color ERROR_COLOUR = new Color(0xb00000);

    private final Plc4xPlantModel model;
    private final S88Element source;
    private final boolean reusingClass;
private final JTextField txtType;
    private final JSpinner spinCopies;
    private final JLabel idsLabel;
    private final JLabel errorLabel;
    private JTable table;

    private boolean applied;

private DuplicateElementDialog(Window owner, Plc4xPlantModel model, S88Element source) {
        super(owner, "Make Equipment Type", ModalityType.APPLICATION_MODAL);
        if (source == null) {
            throw new IllegalArgumentException("No element selected to make a type of.");
        }
        guardDuplicableLevel(source);
        this.model = model;
        this.source = source;

        S88ElementClass existingClass = source.getElementClass();
        this.reusingClass = existingClass != null;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.EAST;

gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("From ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        String levelName = source.getLevel() != null ? source.getLevel().getDisplayName() : "";
        form.add(new JLabel("<html><b>" + source.getId() + "</b> (<i>" + levelName + "</i>)</html>"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("Type ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        if (reusingClass) {
            // The element already belongs to a class, and that class is what the copies will share,
            // so the name is shown rather than offered: a name typed here would be ignored by the
            // use case. Making it read-only says so instead of accepting the entry and dropping it.
            txtType = new JTextField(existingClass.getName(), 20);
            txtType.setEditable(false);
            txtType.setToolTipText("'" + source.getId() + "' already belongs to class '"
                    + existingClass.getName() + "', so the copies join that class rather than a new"
                    + " one. Base names this element publishes that the class does not declare yet"
                    + " are added to it.");
        } else {
            String typeDefault = BaseNameSupport.baseIdOf(source.getId());
            txtType = new JTextField(typeDefault != null ? typeDefault : "", 20);
            txtType.setToolTipText("The name of the type contract the copies will share.");
        }
        form.add(txtType, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.weightx = 0.0;
        form.add(new JLabel("Copies ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        spinCopies = new JSpinner(new SpinnerNumberModel(1, 0, 20, 1));
        form.add(spinCopies, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("New ids ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        idsLabel = new JLabel();
        form.add(idsLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(buildVariablesTable(), gbc);

        errorLabel = new JLabel(" ");
        errorLabel.setForeground(ERROR_COLOUR);
        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.PLAIN, 11f));
        gbc.gridy = 5;
        gbc.weighty = 0.0;
        form.add(errorLabel, gbc);

        JButton btnOk = new JButton("Duplicate");
        btnOk.setPreferredSize(new Dimension(110, 26));
        JButton btnCancel = new JButton("Cancel");
        btnCancel.setPreferredSize(new Dimension(100, 26));
        btnCancel.addActionListener(e -> dispose());
        btnOk.addActionListener(e -> attemptDuplicate());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttons.add(btnOk);
        buttons.add(btnCancel);

        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(btnOk);

        spinCopies.addChangeListener(e -> refreshIds());
        refreshIds();
        pack();
        setSize(new Dimension(Math.max(getWidth(), 560), Math.max(getHeight(), 380)));
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and creates the copies when the user confirms.
     *
     * @param owner  window to centre on, may be {@code null}
     * @param model  plant holding the element
     * @param source element to turn into a type
     * @return {@code true} when the copies were actually created
     */
public static boolean duplicate(Window owner, Plc4xPlantModel model, S88Element source) {
        DuplicateElementDialog dialog = new DuplicateElementDialog(owner, model, source);
        dialog.setVisible(true);
        return dialog.applied;
    }

    /**
     * The levels that hold the composition of the plant rather than the equipment the copies stand
     * for are never turned into a type: only the units and the modules fitted into them get copies.
     */
    private static void guardDuplicableLevel(S88Element source) {
        if (source.getParent() == null) {
            throw new IllegalArgumentException("The root of a plant cannot be turned into an equipment type.");
        }
        if (source.getLevel() == S88Level.AREA || source.getLevel() == S88Level.PROCESSCELL) {
            String levelName = source.getLevel().getDisplayName() != null
                    ? source.getLevel().getDisplayName() : String.valueOf(source.getLevel());
            throw new IllegalArgumentException("Only a UNIT or an EQUIPMENT MODULE can be duplicated, not a "
                    + levelName + ".");
        }
    }

    private JComponent buildVariablesTable() {
        DefaultTableModel rows = new DefaultTableModel(
                new String[]{"Container", "Variable", "Base name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2;
            }
        };
for (VariableRow variable : collectVariables(source)) {
            rows.addRow(new Object[]{variable.container(), variable.variable(),
                    BaseNameSupport.suggestBaseName(variable.variable(), variable.ownerId())});
        }
        table = new JTable(rows);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(460, 130));
        return scroll;
    }

    private void refreshIds() {
        int copies = copies();
        List<String> ids = model.getModel() != null
                ? BaseNameSupport.nextIds(model.getModel(), source.getId(), copies)
                : new ArrayList<>();
        idsLabel.setText(ids.isEmpty() ? "-" : String.join(", ", ids));
    }

/**
     * Every variable published under the element and its descendants, so the user can see which
     * base names the copies will be tied to and adjust them before confirming. Each variable is
     * paired with the element that owns it, because a base name is derived by stripping the id of
     * that very element.
     */
    private List<VariableRow> collectVariables(S88Element element) {
        List<VariableRow> result = new ArrayList<>();
        if (element == null) {
            return result;
        }
        for (String container : List.of(VariableKeySupport.PARAMETERS, VariableKeySupport.REPORTS)) {
            Object raw = element.getProperties().get(container);
            if (raw instanceof Map<?, ?> containerMap) {
                for (Object childKey : containerMap.keySet()) {
                    result.add(new VariableRow(container, String.valueOf(childKey), element.getId()));
                }
            }
        }
        for (S88Element child : element.getChildren()) {
            result.addAll(collectVariables(child));
        }
        return result;
    }

    private record VariableRow(String container, String variable, String ownerId) {
    }

    private int copies() {
        Object value = spinCopies.getValue();
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void attemptDuplicate() {
        String typeName = reusingClass ? null : txtType.getText();
        Map<String, String> overrides = new LinkedHashMap<>();
        DefaultTableModel rows = (DefaultTableModel) table.getModel();
        for (int i = 0; i < rows.getRowCount(); i++) {
            String container = String.valueOf(rows.getValueAt(i, 0));
            String variable = String.valueOf(rows.getValueAt(i, 1));
            String base = String.valueOf(rows.getValueAt(i, 2)).trim();
            if (!base.isEmpty()) {
                overrides.put(container + "/" + variable, base);
            }
        }

        try {
            DuplicateElementUseCase.execute(model.getModel(), liveSource(), copies(), typeName, overrides);
            model.save();
            applied = true;
            dispose();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            showError("The element could not be duplicated: " + ex.getMessage());
        }
    }

    /**
     * The element as the plant holds it now. The dialog is filled from the element it was opened
     * with, and the plant can be reloaded while it is open, replacing every element of the branch
     * being previewed; duplicating the copy shown would then act on a branch that is gone.
     */
    private S88Element liveSource() {
        if (model == null || model.getModel() == null || source.getId() == null) {
            return source;
        }
        return model.getModel().findById(source.getId()).orElse(source);
    }

    private void showError(String message) {
        errorLabel.setText(message != null ? message : " ");
        errorLabel.revalidate();
        errorLabel.repaint();
    }
}

