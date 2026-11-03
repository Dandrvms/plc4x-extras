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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;

/** Collects the name, description and kind of a new recipe. */
final class NewRecipeDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextField nameField = new JTextField(32);
    private final JTextArea descriptionField = new JTextArea(5, 32);
    private final JRadioButton classButton = new JRadioButton("Class");
    private final JRadioButton instanceButton = new JRadioButton("Instance", true);
    private final JLabel status = new JLabel(" ");

    private RecipeDraft result;

    NewRecipeDialog(Window owner, String suggestedName) {
        super(owner, "New Recipe", ModalityType.APPLICATION_MODAL);
        nameField.setText(suggestedName == null ? "" : suggestedName);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        ButtonGroup kinds = new ButtonGroup();
        kinds.add(classButton);
        kinds.add(instanceButton);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel("Name:"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        form.add(nameField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        form.add(new JLabel("Description:"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        form.add(new JScrollPane(descriptionField), constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.weighty = 0;
        form.add(new JLabel("Type:"), constraints);
        JPanel kindsPanel = new JPanel();
        kindsPanel.add(classButton);
        kindsPanel.add(instanceButton);
        constraints.gridx = 1;
        form.add(kindsPanel, constraints);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        south.add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        buttons.add(cancel);
        JButton create = new JButton("Create");
        create.addActionListener(event -> create());
        buttons.add(create);
        south.add(buttons, BorderLayout.EAST);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(new Dimension(560, 380));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(create);
    }

    /** Shows the dialog and returns what the user entered, or {@code null}. */
    RecipeDraft showDialog() {
        setVisible(true);
        return result;
    }

    JTextField nameField() {
        return nameField;
    }

    JTextArea descriptionField() {
        return descriptionField;
    }

    JRadioButton classButton() {
        return classButton;
    }

    JRadioButton instanceButton() {
        return instanceButton;
    }

    private void create() {
        String name = normalizedName(nameField.getText());
        if (name.isEmpty()) {
            status.setText("A recipe needs a name.");
            nameField.requestFocusInWindow();
            return;
        }
        if (name.startsWith(".") || name.contains("/") || name.contains("\\")) {
            status.setText("That name cannot be used for a recipe file.");
            nameField.requestFocusInWindow();
            return;
        }
        S88RecipeKind kind = classButton.isSelected()
                ? S88RecipeKind.CLASS : S88RecipeKind.INSTANCE;
        result = new RecipeDraft(name, descriptionField.getText(), kind);
        dispose();
    }

    static String normalizedName(String text) {
        String name = text == null ? "" : text.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            name = name.substring(0, name.length() - 4).trim();
        }
        return name;
    }

    static String fileName(String name) {
        return normalizedName(name) + ".xml";
    }

    /** What the creation dialog returns. */
    static record RecipeDraft(String name, String description, S88RecipeKind kind) {

        String fileName() {
            return NewRecipeDialog.fileName(name);
        }
    }
}
