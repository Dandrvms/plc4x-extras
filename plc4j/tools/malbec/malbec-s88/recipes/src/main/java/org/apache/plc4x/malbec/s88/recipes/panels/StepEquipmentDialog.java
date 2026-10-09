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
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.core.UpdateRecipeParameterUseCase;

/**
 * What one step of a recipe is for: the equipment behind it, the values it gives that equipment, and
 * the values it will read back off it.
 *
 * <p>
 * <b>A step is a piece of equipment with values on it.</b> The parameters are what the step sets,
 * and they are the part an author changes from recipe to recipe. The reports are what the step reads
 * back, and they are here to be looked at rather than edited, because a report is what the equipment
 * publishes and a recipe does not get to decide what that equipment says.
 *
 * <p>
 * <b>Whether the names are the equipment's own or the class's base names is not decided here.</b> A
 * recipe written for particular equipment names the variables that equipment publishes, and a recipe
 * written by class names the base names its class declares, which are resolved to a piece of equipment later.
 * Either way the names come from the plant and the author does not type them.
 */
final class StepEquipmentDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Where a plant keeps the values a recipe gives a piece of equipment. */
    private static final String PARAMETERS = "Parameters";

    /** Where a plant keeps the values a piece of equipment publishes. */
    private static final String REPORTS = "Reports";

    /**
     * The engineering unit of a variable, or the name of its enumeration when the type says it is
     * one. The same property holds both.
     */
    private static final String UNITS_OR_ENUM = "Eng_Units/Enum";

    private final transient RecipeEditorModel model;
    private final transient S88RecipeElement step;
    private final transient S88PlantSnapshot plant;
    private final boolean byClass;
    private final DefaultTableModel parameters = new DefaultTableModel(
            new Object[]{"Parameter", "Value", "Type", "Unit / Enum", "Allowed"}, 0) {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            // Only the value is the recipe's to change. The name, the type and the units are what
            // the plant says, and the allowed values are what it will accept.
            return column == 1;
        }
    };
    private final DefaultTableModel reports = new DefaultTableModel(
            new Object[]{"Report", "Type", "Unit / Enum"}, 0) {

        private static final long serialVersionUID = 1L;
    };

    StepEquipmentDialog(java.awt.Window owner, RecipeEditorModel model, S88RecipeElement step) {
        super(owner, "Equipment of " + step.getId(), ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.step = step;
        this.plant = model.plant();
        this.byClass = model.getRecipe().addressesByClass();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Parameters", table(parameters));
        tabs.addTab("Reports", table(reports));
        add(tabs, BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);
        read();
        setSize(new Dimension(620, 420));
        setLocationRelativeTo(owner);
    }

    private static JScrollPane table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        return new JScrollPane(table);
    }

    private JPanel buttons() {
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        south.add(new JLabel(describes()), BorderLayout.CENTER);
        JPanel right = new JPanel();
        JButton cancel = new JButton("Close");
        cancel.addActionListener(event -> dispose());
        right.add(cancel);
        JButton save = new JButton("Save values");
        save.addActionListener(event -> saveValues());
        right.add(save);
        south.add(right, BorderLayout.EAST);
        return south;
    }

    /**
     * What this step stands for, said in one line under the tables.
     */
    private String describes() {
        if (byClass) {
            return "Class " + step.getEquipmentClassId()
                    + ": the names below are the ones the class declares.";
        }
        List<String> bound = step.getActualEquipmentIds();
        return "Equipment " + (bound.isEmpty() ? "(none)" : bound.get(0))
                + ": the names below are the ones this equipment publishes.";
    }

    /**
     * Fills the tables from the step and from the plant.
     */
    private void read() {
        parameters.setRowCount(0);
        reports.setRowCount(0);
        Map<String, Object> published = publishedBy();
        Map<String, Map<String, Object>> plantParameters = container(published, PARAMETERS);
        Map<String, Map<String, Object>> plantReports = container(published, REPORTS);

        for (S88RecipeParameter parameter : step.getParameters()) {
            String name = parameter.getId();
            if (PlatformVariable.isWrittenByTheBatch(name)) {
                // The batch writes this one while the recipe runs. Offering it here would ask the
                // author for something they do not decide.
                continue;
            }
            Map<String, Object> definition = plantParameters.get(name);
            S88ParameterValue carried = parameter.getFirstValue();
            DataType dataType = dataTypeOf(definition, carried);
            parameters.addRow(new Object[]{
                name,
                carried == null ? "" : carried.getFirstValueString(),
                dataType == null ? "" : dataType.name(),
                dataType == DataType.ENUMERATION
                        ? enumOf(definition, carried)
                        : unitOf(definition, carried, dataType),
                allowedOf(definition)
            });
        }
        for (Map.Entry<String, Map<String, Object>> report : plantReports.entrySet()) {
            reports.addRow(new Object[]{
                report.getKey(),
                text(report.getValue(), "Type"),
                text(report.getValue(), UNITS_OR_ENUM)
            });
        }
    }

    /**
     * The parameters and reports of whatever this step stands for.
     * <p>
     * A step bound to a class reads the class, because a class is what it names and what the batch
     * will resolve it against. A step bound to one piece of equipment reads that equipment, because that is the only
     * place the values it will send and read are written down.
     */
    private Map<String, Object> publishedBy() {
        if (byClass) {
            S88ElementClass equipmentClass = plant == null || step.getEquipmentClassId() == null
                    ? null
                    : plant.findClass(step.getEquipmentClassId());
            return equipmentClass == null ? Map.of() : equipmentClass.getProperties();
        }
        if (plant != null && step.getEquipmentUid() != null) {
            S88Element equipment = plant.findByUid(step.getEquipmentUid()).orElse(null);
            if (equipment != null) {
                return equipment.getProperties();
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> container(Map<String, Object> properties,
                                                               String key) {
        Object declared = properties.get(key);
        if (!(declared instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) raw).entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> definition) {
                byName.put(entry.getKey(), (Map<String, Object>) definition);
            }
        }
        return byName;
    }

/**
 * What the plant says a parameter may be given, which is why a value outside it is refused.
 */
    private static String allowedOf(Map<String, Object> definition) {
        if (definition == null) {
            return "";
        }
        String lowest = text(definition, "Min");
        String highest = text(definition, "Max");
        if (lowest.isEmpty() && highest.isEmpty()) {
            return "";
        }
        return (lowest.isEmpty() ? "" : lowest) + " .. " + (highest.isEmpty() ? "" : highest);
    }

    private static String text(Map<String, Object> definition, String key) {
        if (definition == null) {
            return "";
        }
        Object value = definition.get(key);
        return value == null ? "" : value.toString();
    }

    /**
     * Writes the values the author changed back into the recipe.
     * <p>
     * Each one on its own edit, so that undo takes back one value rather than the whole table.
     */
    private void saveValues() {
        Map<String, Map<String, Object>> declared =
                container(publishedBy(), PARAMETERS);
        for (int row = 0; row < parameters.getRowCount(); row++) {
            String name = (String) parameters.getValueAt(row, 0);
            Object typed = parameters.getValueAt(row, 1);
            String wanted = typed == null ? "" : typed.toString();
            S88RecipeParameter parameter = step.findParameter(name).orElse(null);
            String already = parameter == null || parameter.getFirstValue() == null
                    ? null
                    : parameter.getFirstValue().getFirstValueString();
            if (java.util.Objects.equals(wanted, already)) {
                continue;
            }
            S88ParameterValue carried = parameter == null ? null : parameter.getFirstValue();
            Map<String, Object> definition = declared.get(name);
            DataType dataType = dataTypeOf(definition, carried);
            String unit = unitOf(definition, carried, dataType);
            String saving = name;
            model.edit("Value of " + name, () -> UpdateRecipeParameterUseCase.execute(
                    model.getRecipe(), step, saving, wanted, dataType, unit));
        }
        dispose();
    }

    /**
     * The type the value keeps, which is the one the plant declares for it.
     *
     * <p>Writing a value writes the whole value afresh, so a type left out of that write is a type
     * thrown away. What the parameter already carries is the fallback for a plant that says nothing,
     * because a type nobody declared is better than none at all.
     */
    private static DataType dataTypeOf(Map<String, Object> definition, S88ParameterValue carried) {
        String declared = text(definition, "Type");
        DataType type = declared == null || declared.isEmpty() ? null : DataType.fromString(declared);
        if (type != null) {
            return type;
        }
        return carried == null ? null : carried.getDataType();
    }

    /**
     * The unit the value keeps, which is the engineering unit the plant declares for it.
     *
     * <p>An enumeration has no unit, so what the plant keeps in the same place is the name of the
     * enumeration and is not written as a unit. That name is what
     * {@link #enumOf} reads instead.
     */
    private static String unitOf(Map<String, Object> definition, S88ParameterValue carried,
                                 DataType dataType) {
        if (dataType == DataType.ENUMERATION) {
            return carried == null ? null : carried.getUnitOfMeasure();
        }
        String declared = text(definition, UNITS_OR_ENUM);
        return declared == null || declared.isEmpty()
                ? (carried == null ? null : carried.getUnitOfMeasure())
                : declared;
    }

    private static String enumOf(Map<String, Object> definition, S88ParameterValue carried) {
        if (carried != null && !carried.getEnumerationSetIds().isEmpty()) {
            return String.join(", ", carried.getEnumerationSetIds());
        }
        return text(definition, UNITS_OR_ENUM);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            read();
        }
        super.setVisible(visible);
    }

    /** The parameter values as they now stand, for a test that reads them. */
    java.util.List<String> valuesShown() {
        java.util.List<String> shown = new java.util.ArrayList<>();
        for (int row = 0; row < parameters.getRowCount(); row++) {
            shown.add(parameters.getValueAt(row, 0) + "=" + parameters.getValueAt(row, 1));
        }
        return shown;
    }

    /** What the first parameter says it may be given, for a test that reads it. */
    String allowedShown() {
        return parameters.getRowCount() == 0
                ? ""
                : String.valueOf(parameters.getValueAt(0, 4));
    }

    /** The type and unit columns as they now stand, for a test that reads them. */
    java.util.List<String> typeAndUnitShown() {
        java.util.List<String> shown = new java.util.ArrayList<>();
        for (int row = 0; row < parameters.getRowCount(); row++) {
            shown.add(parameters.getValueAt(row, 2) + " / " + parameters.getValueAt(row, 3));
        }
        return shown;
    }

    /** Puts a value in the first column of a row, as the author typing in the table would. */
    void putValue(String name, String value) {
        for (int row = 0; row < parameters.getRowCount(); row++) {
            if (name.equals(parameters.getValueAt(row, 0))) {
                parameters.setValueAt(value, row, 1);
                return;
            }
        }
        throw new IllegalArgumentException("There is no parameter called '" + name + "' here.");
    }

    /** Writes the table back the way the Save values button does, without opening anything. */
    void saveForTest() {
        saveValues();
    }
}