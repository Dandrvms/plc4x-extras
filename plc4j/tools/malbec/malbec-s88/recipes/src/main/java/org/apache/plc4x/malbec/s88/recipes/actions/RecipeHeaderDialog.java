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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeHeader;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;

/** Shows the ISA-88 header of a recipe without letting anyone change it. */
final class RecipeHeaderDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    RecipeHeaderDialog(Window owner, S88MasterRecipe recipe, S88RecipeHeader header,
                       String currentFileName) {
        super(owner, "Recipe Header", ModalityType.APPLICATION_MODAL);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.gridx = 0;
        constraints.gridy = 0;

        add(form, constraints, "Recipe", recipe.getId());
        add(form, constraints, "Version", text(header.version()));
        add(form, constraints, "Creation date", date(header.creationDate()));
        add(form, constraints, "Last modified", date(header.lastModifiedDate()));
        add(form, constraints, "Author", text(header.author()));
        addDescription(form, constraints, text(header.description()));
        add(form, constraints, "Type", header.kind() == S88RecipeKind.CLASS ? "Class" : "Instance");
        add(form, constraints, "Area model", text(header.areaModelFile()));
        String fileName = header.fileName() == null || header.fileName().isBlank()
                ? currentFileName : header.fileName();
        add(form, constraints, "File name", text(fileName));

        JPanel south = new JPanel();
        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        south.add(close);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(new Dimension(600, 440));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(close);
    }

    private void add(JPanel form, GridBagConstraints constraints, String label, String value) {
        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setColumns(36);
        fields.put(label, field);
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        form.add(new JLabel(label + ":"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        form.add(field, constraints);
        constraints.gridy++;
    }

    private void addDescription(JPanel form, GridBagConstraints constraints, String value) {
        JTextArea description = new JTextArea(value, 5, 36);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        form.add(new JLabel("Description:"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        form.add(new JScrollPane(description), constraints);
        constraints.gridy++;
        constraints.weighty = 0;
    }

    /** The value shown for a label, for the tests. */
    String value(String label) {
        JTextField field = fields.get(label);
        return field == null ? null : field.getText();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String date(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        try {
            return DATE.format(Instant.parse(value));
        } catch (DateTimeParseException notAnInstant) {
            return value;
        }
    }
}
