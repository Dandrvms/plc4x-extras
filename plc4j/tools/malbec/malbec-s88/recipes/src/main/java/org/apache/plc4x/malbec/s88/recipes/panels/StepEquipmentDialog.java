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

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import org.apache.plc4x.malbec.s88.core.SetBarConditionUseCase;
import org.apache.plc4x.malbec.s88.core.UpdateRecipeParameterUseCase;

/**
 * What one step of a recipe is for: the equipment behind it, the values it gives that equipment, and
 * the values it will read back off it.
 *
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
            // parameter value column
            return column == 1;
        }
    };
    private final DefaultTableModel reports = new DefaultTableModel(
            new Object[]{"Report", "Type", "Unit / Enum"}, 0) {

        private static final long serialVersionUID = 1L;
    };

    /** What each parameter row offers to choose from, empty when the value is typed. */
    private final Map<Integer, List<String>> valueChoices = new LinkedHashMap<>();

    private JTable parametersTable;

    StepEquipmentDialog(Window owner, RecipeEditorModel model, S88RecipeElement step) {
        super(owner, "Equipment of " + step.getId(), ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.step = step;
        this.plant = model.plant();
        this.byClass = model.getRecipe().addressesByClass();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(PARAMETERS, new JScrollPane(parametersTable()));
        tabs.addTab(REPORTS, new JScrollPane(plainTable(reports)));
        add(tabs, BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);
        read();
        setSize(new Dimension(620, 420));
        setLocationRelativeTo(owner);
    }

    /**
     * The parameters table, whose value column offers the members of an enumeration rather than
     * letting a person write something the equipment can never hold.
     */
    private JTable parametersTable() {
        parametersTable = new JTable(parameters) {

            private static final long serialVersionUID = 1L;

            @Override
            public javax.swing.table.TableCellEditor getCellEditor(int row, int column) {
                if (column == 1) {
                    List<String> choices = valueChoices.get(row);
                    if (choices != null && !choices.isEmpty()) {
                        return new javax.swing.DefaultCellEditor(
                                new javax.swing.JComboBox<>(choices.toArray(new String[0])));
                    }
                }
                return super.getCellEditor(row, column);
            }
        };
        parametersTable.setFillsViewportHeight(true);
        return parametersTable;
    }

    private static JTable plainTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        return table;
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
        valueChoices.clear();
        Map<String, Object> published = publishedBy();
        Map<String, Map<String, Object>> plantParameters = container(published, PARAMETERS);
        Map<String, Map<String, Object>> plantReports = container(published, REPORTS);

        for (S88RecipeParameter parameter : step.getParameters()) {
            String name = parameter.getId();
            if (PlatformVariable.isWrittenByTheBatch(name)) {
                continue;
            }
            Map<String, Object> definition = plantParameters.get(name);
            S88ParameterValue carried = parameter.getFirstValue();
            DataType dataType = dataTypeOf(definition, carried);
            int row = parameters.getRowCount();
            parameters.addRow(new Object[]{
                name,
                carried == null ? "" : carried.getFirstValueString(),
                dataType == null ? "" : dataType.name(),
                dataType == DataType.ENUMERATION
                        ? enumOf(definition, carried)
                        : unitOf(definition, carried, dataType),
                allowedOf(definition)
            });
            if (dataType == DataType.ENUMERATION) {
                List<String> members =
                        SetBarConditionUseCase.membersOf(plant, enumOf(definition, carried));
                if (!members.isEmpty()) {
                    valueChoices.put(row, members);
                }
            }
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
     * A value the plant would never accept is refused where it was typed rather than written, and a
     * value left empty is the same as one that was never there.
     */
    private void saveValues() {
        String problem = checkValues();
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, "Not a value it can hold",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        writeTheValues();
        dispose();
    }

    private void writeTheValues() {
        List<ValuesToWrite> writes = valuesToWrite();
        if (writes.isEmpty()) {
            return;
        }
        model.edit("Value of " + step.getId(), () -> {
            for (ValuesToWrite write : writes) {
                S88RecipeElement current = model.getRecipe().findElement(step.getId()).orElse(step);
                UpdateRecipeParameterUseCase.execute(model.getRecipe(), current, write.name(),
                        write.value(), write.type(), write.unit());
            }
        });
    }

    /**
     * The first value that is not one the plant would accept, or {@code null} when every one is.
     *
     * @return what is wrong, in a sentence the author can read
     */
    String checkValues() {
        stopEditing();
        Map<String, Map<String, Object>> declared = container(publishedBy(), PARAMETERS);
        for (int row = 0; row < parameters.getRowCount(); row++) {
            String name = (String) parameters.getValueAt(row, 0);
            Object typed = parameters.getValueAt(row, 1);
            String problem = problemWith(declared.get(name), name, typed == null ? "" : typed.toString());
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private List<ValuesToWrite> valuesToWrite() {
        stopEditing();
        Map<String, Map<String, Object>> declared = container(publishedBy(), PARAMETERS);
        List<ValuesToWrite> writes = new ArrayList<>();
        for (int row = 0; row < parameters.getRowCount(); row++) {
            String name = (String) parameters.getValueAt(row, 0);
            Object typed = parameters.getValueAt(row, 1);
            String wanted = typed == null ? "" : typed.toString();
            S88RecipeParameter parameter = step.findParameter(name).orElse(null);
            String already = parameter == null || parameter.getFirstValue() == null
                ? null
                : parameter.getFirstValue().getFirstValueString();
            if (sameValue(wanted, already)) {
                continue;
            }
            Map<String, Object> definition = declared.get(name);
            DataType dataType = dataTypeOf(definition,
                    parameter == null ? null : parameter.getFirstValue());
            writes.add(new ValuesToWrite(name, wanted, dataType,
                    unitOf(definition, parameter == null ? null : parameter.getFirstValue(), dataType)));
        }
        return writes;
    }

    /** A value left empty is the same as one that was never there. */
    private static boolean sameValue(String wanted, String already) {
        return normalize(wanted).equals(normalize(already));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * What is wrong with one value: its type, and whether it sits inside what the plant allows.
     */
    private String problemWith(Map<String, Object> definition, String name, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return null;
        }
        String trimmed = wanted.trim();
        DataType dataType = dataTypeOf(definition, null);
        switch (dataType == null ? DataType.STRING : dataType) {
            case INTEGER -> {
                if (asDouble(trimmed) == null || trimmed.contains(".")) {
                    return name + " has to be a whole number, and '" + wanted + "' is not one.";
                }
            }
            case REAL -> {
                if (asDouble(trimmed) == null) {
                    return name + " has to be a number, and '" + wanted + "' is not one.";
                }
            }
            case ENUMERATION -> {
                List<String> members = membersOfTheEnumeration(definition);
                if (!members.isEmpty() && !members.contains(trimmed)) {
                    return name + " has to be one of " + String.join(", ", members)
                            + ", and '" + wanted + "' is not one of them.";
                }
            }
            default -> {
                /* A string can hold what a person writes. */
            }
        }
        return outOfRange(definition, name, trimmed, dataType);
    }

    /**
     * Whether the value sits between the lowest and the highest the plant allows, when it says.
     */
    private static String outOfRange(Map<String, Object> definition, String name, String trimmed,
                                     DataType dataType) {
        if (dataType != DataType.INTEGER && dataType != DataType.REAL) {
            return null;
        }
        Double value = asDouble(trimmed);
        if (value == null) {
            return null;
        }
        Double lowest = asDouble(text(definition, "Min"));
        Double highest = asDouble(text(definition, "Max"));
        if (lowest != null && value < lowest) {
            return name + " has to be at least " + text(definition, "Min") + ".";
        }
        if (highest != null && value > highest) {
            return name + " has to be at most " + text(definition, "Max") + ".";
        }
        return null;
    }

    private static Double asDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private List<String> membersOfTheEnumeration(Map<String, Object> definition) {
        return SetBarConditionUseCase.membersOf(plant, text(definition, UNITS_OR_ENUM));
    }

    /** Stops an edit still open in the table, so what is read is what was typed. */
    private void stopEditing() {
        if (parametersTable != null && parametersTable.isEditing()) {
            parametersTable.getCellEditor().stopCellEditing();
        }
    }

    private record ValuesToWrite(String name, String value, DataType type, String unit){}

    /**
     * The type the value keeps, which is the one the plant declares for it.
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
    List<String> valuesShown() {
        List<String> shown = new ArrayList<>();
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

    /** What a parameter offers to choose from, for a test that reads it. */
    List<String> choicesFor(String name) {
        for (int row = 0; row < parameters.getRowCount(); row++) {
            if (name.equals(parameters.getValueAt(row, 0))) {
                List<String> choices = valueChoices.get(row);
                return choices == null ? List.of() : choices;
            }
        }
        return List.of();
    }

    /** The type and unit columns as they now stand, for a test that reads them. */
    List<String> typeAndUnitShown() {
        List<String> shown = new ArrayList<>();
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