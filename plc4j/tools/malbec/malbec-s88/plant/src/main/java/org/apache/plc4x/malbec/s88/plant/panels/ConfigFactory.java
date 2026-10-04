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

import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.core.AddStructEntryUseCase;
import org.apache.plc4x.malbec.s88.core.BaseNameSupport;
import org.apache.plc4x.malbec.s88.core.ClassConformance;
import org.apache.plc4x.malbec.s88.core.UpdateStructEntryUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigFactory {
    private ConfigFactory() {
        /* This utility class should not be instantiated */
    }


    public static JPanel createConfigPanel(Plc4xPlantModel model, S88Element element) {
        return switch(element.getLevel()){
            case PROCESSCELL -> new JPanel();
            case UNIT -> buildUnitPanel(model, element);
            case EQUIPMENTMODULE -> buildEMPanel(model, element);
            default -> new JPanel();
        };
    }

    private static JPanel buildUnitPanel(Plc4xPlantModel model, S88Element element) {
        String[] columns = {"Name", "Base Name", "Type", "Eng_Units/Enum", "Reference", "StaticValue"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);
        JTable table = createStandardConfigTable(tableModel);

        updateUnitTableData(currentElement(model, element), tableModel);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = table.rowAtPoint(e.getPoint());
                    if (row < 0) {
                        return;
                    }
                    String name = String.valueOf(table.getValueAt(row, 0));
                    S88Element live = currentElement(model, element);
                    Map<String, Object> prop = live.getStructuredProperty(name);
                    if (prop == null) {

                        updateUnitTableData(live, tableModel);
                        showError("'" + name + "' is no longer an attribute of " + live.getId() + ".");
                        return;
                    }

                    new AttributeDialogBuilder("Edit Attribute")
                            .withEnumerations(enumerations(model))
                            .withEditable(false)
                            .withInitialData(name, prop)
                            .onSave((updatedName, updatedProps) -> {
                                try {
                                    S88Element current = currentElement(model, element);
                                    UpdateStructEntryUseCase.execute(model.getModel(), current, null, name, updatedName, updatedProps);
                                    model.save();
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    showError(ex);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            })
                            .onUpdate(() -> updateUnitTableData(currentElement(model, element), tableModel))
                            .show();
                }
            }
        });

        JButton btnAdd = new JButton("Add unit attribute");
        btnAdd.addActionListener(e -> new AttributeDialogBuilder("Create Unit Attribute")
                .withEnumerations(enumerations(model))
                .withEditable(true)
                .withVariableKeyPreview(currentElement(model, element), model)
                .onSave((updatedName, updatedProps) -> {
                    try {
                        S88Element current = currentElement(model, element);
                        AddStructEntryUseCase.execute(model.getModel(), current, null, updatedName, updatedProps);
                        model.save();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        showError(ex);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                })
                .onUpdate(() -> updateUnitTableData(currentElement(model, element), tableModel))
                .show());


        JPanel[] built = new JPanel[1];
        Runnable refresh = () -> {
            S88Element current = currentElement(model, element);
            updateUnitTableData(current, tableModel);
            ConfigPanelBuilder.setConformanceSummary(built[0], ClassConformance.of(current));
        };
        built[0] = refreshOnModelChange(model, conformancePanel(model, element)
                .withInfoPanel()
                .withCenterComponent("Unit attributes", new JScrollPane(table))
                .addBottomButton(btnAdd)
                .build(), refresh);
        return built[0];
    }

    /**
     * Starts the panel with the two ways of closing a gap with the class already in place, so the
     * builder only has to describe them.
     */
    private static ConfigPanelBuilder conformancePanel(Plc4xPlantModel model, S88Element element) {
        JButton btnAddToClass = new JButton("Add to class");
        btnAddToClass.addActionListener(e -> addToClass(model, element));

        JButton btnAlign = new JButton("Align with class");
        btnAlign.addActionListener(e -> alignWithClass(model, element));

        return new ConfigPanelBuilder(element)
                .withConformancePanel(btnAddToClass, btnAlign);
    }

    /**
     * Pushes the element's own base names into the class. The class is shared, so the siblings are
     * left short of what it now declares; the user is told how many before it happens.
     */
    private static void addToClass(Plc4xPlantModel model, S88Element element) {
        S88Element current = currentElement(model, element);
        S88ElementClass elementClass = current != null ? current.getElementClass() : null;
        if (elementClass == null) {
            showError("This element has no equipment type, so there is nothing to add to.");
            return;
        }
        ClassConformance conformance = ClassConformance.of(current);
        if (conformance.excess().isEmpty()) {
            return;
        }
        int siblings = deficientSiblings(model, current, elementClass, conformance.excess());
        String message = "Add " + conformance.excess().size() + " base name(s) to class '"
                + elementClass.getName() + "':\n\n  " + String.join("\n  ", conformance.excess())
                + "\n\nThe class states what every instance of this type is expected to publish."
                + (siblings > 0
                ? "\n\n" + siblings + " other element(s) of this class will be left without them."
                        + "\nThey can be aligned one by one with 'Align with class'."
                : "\n\nNo other element of this class is affected.");
        if (DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(
                message, NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.QUESTION_MESSAGE))
                != NotifyDescriptor.YES_OPTION) {
            return;
        }
        try {
            ClassConformance.addToClass(current, elementClass);
            announce(model, current);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Publishes on the element what the class already declares. The class is untouched, so nothing
     * outside this element changes.
     */
    private static void alignWithClass(Plc4xPlantModel model, S88Element element) {
        S88Element current = currentElement(model, element);
        S88ElementClass elementClass = current != null ? current.getElementClass() : null;
        if (elementClass == null) {
            showError("This element has no equipment type, so there is nothing to align with.");
            return;
        }
        ClassConformance conformance = ClassConformance.of(current);
        if (conformance.deficit().isEmpty()) {
            return;
        }
        try {
            ClassConformance.alignWithClass(current, elementClass);
            announce(model, current);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Writes the plant and tells the panels that are listening.
     */
    private static void announce(Plc4xPlantModel model, S88Element element) throws IOException {
        if (model == null) {
            return;
        }
        model.save();
        if (model.getModel() != null) {
            model.getModel().fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.UPDATED, element));
        }
    }

    /**
     * How many other elements of the same class would be left short of the base names just added,
     * so the impact of a change to the shared contract can be stated.
     */
    private static int deficientSiblings(Plc4xPlantModel model, S88Element element,
                                         S88ElementClass elementClass, List<String> excess) {
        if (model == null || model.getModel() == null) {
            return 0;
        }
        int count = 0;
        for (S88Element candidate : allElements(model.getModel().getRoot())) {
            if (candidate == null || candidate == element) {
                continue;
            }
            if (candidate.getElementClass() != elementClass) {
                continue;
            }

            if (!ClassConformance.publishesAll(candidate, excess)) {
                count++;
            }
        }
        return count;
    }

    private static List<S88Element> allElements(S88Element element) {
        List<S88Element> all = new java.util.ArrayList<>();
        if (element == null) {
            return all;
        }
        all.add(element);
        for (S88Element child : element.getChildren()) {
            all.addAll(allElements(child));
        }
        return all;
    }

    private static List<S88Enumeration> enumerations(Plc4xPlantModel model) {
        if (model != null && model.getModel() != null) {
            return model.getModel().getEnumerations();
        }
        return Collections.emptyList();
    }


    private static S88Element currentElement(Plc4xPlantModel model, S88Element element) {
        if (model != null && element != null && element.getId() != null) {
            S88Element current = model.getElementByID(element.getId());
            if (current != null) {
                return current;
            }
        }
        return element;
    }

    private static void showError(Exception ex) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
    }

    private static void showError(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE));
    }

    private static JPanel refreshOnModelChange(Plc4xPlantModel model, JPanel content, Runnable refresh) {
        return new RefreshOnChangePanel(model, content, refresh);
    }

    private static final class RefreshOnChangePanel extends JPanel {
        private final Plc4xPlantModel model;
        private final Runnable refresh;
        private final ChangeListener listener;

        RefreshOnChangePanel(Plc4xPlantModel model, JPanel content, Runnable refresh) {
            super(new BorderLayout());
            this.model = model;
            this.refresh = refresh;
            this.listener = event -> refresh.run();
            add(content, BorderLayout.CENTER);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (model != null) {
                model.addChangeListener(listener);
                refresh.run();
            }
        }

        @Override
        public void removeNotify() {
            if (model != null) {
                model.removeChangeListener(listener);
            }
            super.removeNotify();
        }
    }

    private static JPanel buildEMPanel(Plc4xPlantModel model, S88Element element) {

        String[] paramColumns = {"Name", "Base Name", "Eng_Units/Enum", "Type", "Max", "Min", "Default", "Reference"};
        String[] reportColumns = {"Name", "Base Name", "Eng_Units/Enum", "Type", "Reference"};

        DefaultTableModel paramsTableModel = createReadOnlyTableModel(paramColumns);
        DefaultTableModel reportsTableModel = createReadOnlyTableModel(reportColumns);

        JTable paramsTable = createStandardConfigTable(paramsTableModel);
        JTable reportsTable = createStandardConfigTable(reportsTableModel);

        updateEMTableData(currentElement(model, element), paramsTableModel, "Parameters");
        updateEMTableData(currentElement(model, element), reportsTableModel, "Reports");

        paramsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = paramsTable.rowAtPoint(e.getPoint());
                    if (row < 0) {
                        return;
                    }
                    if (isPlatformRow(paramsTable, row)) {
                        return;
                    }
                    String name = String.valueOf(paramsTable.getValueAt(row, 0));
                    S88Element live = currentElement(model, element);
                    Map<String, Object> params = live.getStructuredProperty("Parameters");
                    Map<String, Object> bag = params != null ? (Map<String, Object>) params.get(name) : null;
                    if (bag == null) {
                        updateEMTableData(live, paramsTableModel, "Parameters");
                        showError("'" + name + "' is no longer a parameter of " + live.getId() + ".");
                        return;
                    }

                    new ParameterDialogBuilder("Edit Parameter")
                            .withEnumerations(enumerations(model))
                            .withInitialData(name, bag)
                            .withEditableFields(false)
                            .withVariableKeyPreview(currentElement(model, element), model)
                            .onSave((updatedName, updatedProps) -> {
                                S88Element current = currentElement(model, element);
                                try {
                                    UpdateStructEntryUseCase.execute(model.getModel(), current, "Parameters", name, updatedName, updatedProps);
                                    model.save();
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    showError(ex);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                                updateEMTableData(currentElement(model, element), paramsTableModel, "Parameters");
                            })
                            .show();
                }
            }
        });

        reportsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = reportsTable.rowAtPoint(e.getPoint());
                    if (row < 0) {
                        return;
                    }
                    if (isPlatformRow(reportsTable, row)) {
                        return;
                    }
                    String name = String.valueOf(reportsTable.getValueAt(row, 0));
                    S88Element live = currentElement(model, element);
                    Map<String, Object> reports = live.getStructuredProperty("Reports");
                    Map<String, Object> bag = reports != null ? (Map<String, Object>) reports.get(name) : null;
                    if (bag == null) {
                        updateEMTableData(live, reportsTableModel, "Reports");
                        showError("'" + name + "' is no longer a report of " + live.getId() + ".");
                        return;
                    }

                    new ParameterDialogBuilder("Edit Report")
                            .withEnumerations(enumerations(model))
                            .reportsMode()
                            .withInitialData(name, bag)
                            .withEditableFields(false)
                            .withVariableKeyPreview(currentElement(model, element), model)
                            .onSave((updatedName, updatedProps) -> {
                                S88Element current = currentElement(model, element);
                                try {
                                    UpdateStructEntryUseCase.execute(model.getModel(), current, "Reports", name, updatedName, updatedProps);
                                    model.save();
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    showError(ex);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                                updateEMTableData(currentElement(model, element), reportsTableModel, "Reports");
                            })
                            .show();
                }
            }
        });

        JButton btnAddParameter = new JButton("Add parameter");
        btnAddParameter.addActionListener(e -> new ParameterDialogBuilder("Add Parameter")
                .withEnumerations(enumerations(model))
                .withVariableKeyPreview(currentElement(model, element), model)
                .onSave((updatedName, updatedProps) -> {
                    S88Element current = currentElement(model, element);
                    try {
                        AddStructEntryUseCase.execute(model.getModel(), current, "Parameters", updatedName, updatedProps);
                        model.save();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        showError(ex);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    updateEMTableData(currentElement(model, element), paramsTableModel, "Parameters");
                })
                .show());


        JButton btnAddReport = new JButton("Add Report");
        btnAddReport.addActionListener(e -> new ParameterDialogBuilder("Add Report")
                .withEnumerations(enumerations(model))
                .reportsMode()
                .withVariableKeyPreview(currentElement(model, element), model)
                .onSave((updatedName, updatedProps) -> {
                    S88Element current = currentElement(model, element);
                    try {
                        AddStructEntryUseCase.execute(model.getModel(), current, "Reports", updatedName, updatedProps);
                        model.save();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        showError(ex);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    updateEMTableData(currentElement(model, element), reportsTableModel, "Reports");
                })
                .show());


        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Parameters", new JScrollPane(paramsTable));
        tabbedPane.addTab("Reports", new JScrollPane(reportsTable));


        JPanel[] built = new JPanel[1];
        Runnable refresh = () -> {
            S88Element current = currentElement(model, element);
            updateEMTableData(current, paramsTableModel, "Parameters");
            updateEMTableData(current, reportsTableModel, "Reports");
            ConfigPanelBuilder.setConformanceSummary(built[0], ClassConformance.of(current));
        };
        built[0] = refreshOnModelChange(model, conformancePanel(model, element)
                .withInfoPanel()
                .withCenterComponent(null, tabbedPane)
                .addBottomButton(btnAddParameter)
                .addBottomButton(btnAddReport)
                .build(), refresh);
        return built[0];
    }


    private static DefaultTableModel createReadOnlyTableModel(String[] columns) {
        return new DefaultTableModel(null, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable createStandardConfigTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setFillsViewportHeight(true);
        return table;
    }



    private static void updateUnitTableData(S88Element element, DefaultTableModel tableModel) {
        tableModel.setRowCount(0);
        int columnCount = tableModel.getColumnCount();
        iterate(element, tableModel, columnCount, null, null);
    }

    /**
     * Whether a row is one of the ISA-88 variables every module publishes.
     * <p>
     * Those rows are listed so the plant file can be read, but they belong to the model not to
     * the engineer, so the editor opens nothing for them.
     *
     * @param table table the row is in
     * @param row   row to inspect
     * @return {@code true} when the row is a platform variable
     */
    private static boolean isPlatformRow(JTable table, int row) {
        Object baseName = table.getValueAt(row, 1);
        return baseName != null && PlatformVariable.isPlatformName(String.valueOf(baseName));
    }

    private static void updateEMTableData(S88Element element, DefaultTableModel tableModel, String propertyName) {
        tableModel.setRowCount(0);
        int columnCount = tableModel.getColumnCount();

        Map<String, Object> params = element.getStructuredProperty(propertyName);
        if (params == null) params = Collections.emptyMap();

        iterate(element, tableModel, columnCount, params, propertyName);
    }

    private static void iterate(S88Element element, DefaultTableModel tableModel, int columnCount,
                                Map<String, Object> property, String containerKey) {
        for (var entry : element.getStructuredProperties(property).entrySet()) {
            String name = entry.getKey();
            Map<String, Object> propertyValues = entry.getValue();

            Object[] rowData = new Object[columnCount];
            if (columnCount > 0) {
                rowData[0] = name;
            }
            for (int i = 1; i < columnCount; i++) {
                String columnName = tableModel.getColumnName(i);

                Object value = propertyValues.get(columnName);
                if (value != null) {
                    rowData[i] = value;
                } else if ("Base Name".equals(columnName)) {
                    rowData[i] = BaseNameSupport.resolveBaseName(element, containerKey, name);
                } else {
                    rowData[i] = "";
                }
            }
            tableModel.addRow(rowData);
        }
    }
}
