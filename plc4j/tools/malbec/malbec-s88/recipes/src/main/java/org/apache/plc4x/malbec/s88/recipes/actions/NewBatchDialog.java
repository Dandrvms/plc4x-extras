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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;

/** Collects the identity, output folder and equipment of a new simulated batch. */
final class NewBatchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextField batchId = new JTextField(28);
    private final JTextField output = new JTextField(36);
    private final JComboBox<String> speed = new JComboBox<>(new String[]{"1x", "5x", "10x", "30x", "60x"});
    private final JLabel status = new JLabel(" ");
    private final List<EquipmentRow> equipment = new ArrayList<>();

    private BatchDetails result;

    NewBatchDialog(Window owner, S88MasterRecipe recipe, S88PlantSnapshot plant,
                   String suggestedBatchId, Path defaultOutput) {
        super(owner, "Run Simulation", ModalityType.APPLICATION_MODAL);
        batchId.setText(suggestedBatchId);
        output.setText(defaultOutput == null ? "" : defaultOutput.toString());
        speed.setSelectedItem("10x");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.gridx = 0;
        constraints.gridy = 0;
        addLine(form, constraints, "Batch id:", batchId);
        addOutput(form, constraints);
        addLine(form, constraints, "Speed:", speed);
        addLine(form, constraints, "Profile:", new JLabel("defaults"));

        if (plant != null) {
            for (S88RecipeElement element : recipe.getAllElements()) {
                if (element.getEquipmentClassId() == null
                        || element.getEquipmentClassId().isBlank()) {
                    continue;
                }
                List<String> choices = choices(plant, element);
                JComboBox<String> combo = new JComboBox<>(choices.toArray(new String[0]));
                if (!choices.isEmpty()) {
                    combo.setSelectedIndex(0);
                }
                equipment.add(new EquipmentRow(element.getId(), element.getEquipmentClassId(),
                        combo));
                addLine(form, constraints, element.getId() + " [" + element.getEquipmentClassId() + "]:",
                        combo);
            }
        }

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        south.add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        buttons.add(cancel);
        JButton run = new JButton("Run");
        run.addActionListener(event -> accept());
        buttons.add(run);
        south.add(buttons, BorderLayout.EAST);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(new Dimension(700, 520));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(run);
    }

    BatchDetails showDialog() {
        setVisible(true);
        return result;
    }

    private void addLine(JPanel form, GridBagConstraints constraints, String label,
                         java.awt.Component field) {
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        form.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        form.add(field, constraints);
        constraints.gridy++;
    }

    private void addOutput(JPanel form, GridBagConstraints constraints) {
        JPanel line = new JPanel(new BorderLayout(4, 0));
        line.add(output, BorderLayout.CENTER);
        JButton browse = new JButton("...");
        browse.addActionListener(event -> chooseOutput());
        line.add(browse, BorderLayout.EAST);
        addLine(form, constraints, "Output folder:", line);
    }

    private void chooseOutput() {
        JFileChooser chooser = new JFileChooser(output.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            output.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void accept() {
        String id = batchId.getText() == null ? "" : batchId.getText().trim();
        if (id.isEmpty()) {
            status.setText("A batch needs an id.");
            return;
        }
        String folder = output.getText() == null ? "" : output.getText().trim();
        if (folder.isEmpty()) {
            status.setText("Choose an output folder.");
            return;
        }
        Path folderPath;
        try {
            folderPath = Path.of(folder);
        } catch (InvalidPathException invalid) {
            status.setText("That output folder is not a valid path.");
            return;
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        for (EquipmentRow row : equipment) {
            Object selected = row.choice().getSelectedItem();
            if (selected != null && !selected.toString().isBlank()) {
                overrides.put(row.elementId(), selected.toString());
            }
        }
        double factor = switch (String.valueOf(speed.getSelectedItem())) {
            case "1x" -> 1.0;
            case "5x" -> 5.0;
            case "30x" -> 30.0;
            case "60x" -> 60.0;
            default -> 10.0;
        };
        result = new BatchDetails(id, folderPath, overrides, factor);
        dispose();
    }

    private static List<String> choices(S88PlantSnapshot plant, S88RecipeElement element) {
        S88Level level = RecipeLevels.boundTo(element.getKind());
        if (level == null) {
            return List.of();
        }
        String classId = element.getEquipmentClassId();
        List<String> accepted = new ArrayList<>();
        for (String id : BindRecipeStepUseCase.choices(plant, false, element.getKind())) {
            S88Element candidate = plant.findById(id).orElse(null);
            if (candidate != null && isOfClass(candidate, classId)) {
                accepted.add(id);
            }
        }
        return accepted;
    }

    private static boolean isOfClass(S88Element element, String classId) {
        S88ElementClass direct = element.getElementClass();
        if (direct != null && classId.equals(direct.getName())) {
            return true;
        }
        for (S88ElementClass candidate : element.getElementClasses()) {
            if (candidate != null && classId.equals(candidate.getName())) {
                return true;
            }
        }
        return false;
    }

    private record EquipmentRow(String elementId, String classId, JComboBox<String> choice) {
    }

    /** What the dialog gathers for the run. */
    record BatchDetails(String batchId, Path outputRoot, Map<String, String> equipmentOverrides,
                        double speed) {

        BatchDetails {
            equipmentOverrides = Map.copyOf(equipmentOverrides);
        }
    }
}
