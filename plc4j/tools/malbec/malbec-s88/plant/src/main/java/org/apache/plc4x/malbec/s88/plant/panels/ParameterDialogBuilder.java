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

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.EngineeringUnits;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.PlainDocument;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ParameterDialogBuilder {

    private static final Color HINT_COLOUR = new Color(0x777777);
    private static final Color REJECTED_COLOUR = new Color(0xb00000);

    private final String title;
    private boolean isEditMode = false;
    private String initialName = "";
    private Map<String, Object> initialProps = null;
    private List<S88Enumeration> enumerations = new ArrayList<>();
    private boolean reportsMode = false;
    private Window owner;
    private boolean isEditable = true;
    private BiConsumer<String, Map<String, Object>> onSaveAction;
    private S88Element previewElement;
    private Plc4xPlantModel previewModel;
    private VariableKeyPreviewPanel variableKeyPreview;
    private JLabel nameHintLabel;
    protected JTextField txtName;
    protected JComboBox<String> comboType;
    protected JComboBox<String> comboEnumeration;
    protected JComboBox<EngineeringUnits> comboEngineeringUnits;
    protected JTextField txtMax;
    protected JTextField txtMin;
    protected JTextField txtDefault;
    protected JComboBox<String> comboDefault;
    protected JPanel defaultField;
    protected JTextField txtReference;
    protected boolean hasReference = true;
    protected JButton btnOk;
    protected JButton btnCancel;

    private final RestrictedDocumentFilter valueFilter = RestrictedDocumentFilter.forValue(null);

    public ParameterDialogBuilder(String title) {
        this.title = title;
    }

    public ParameterDialogBuilder reportsMode() {
        this.reportsMode = true;
        return this;
    }

    public ParameterDialogBuilder withOwner(Window owner) {
        this.owner = owner;
        return this;
    }

    public ParameterDialogBuilder withEnumerations(List<S88Enumeration> enumerations) {
        this.enumerations = enumerations != null ? new ArrayList<>(enumerations) : new ArrayList<>();
        return this;
    }

    public ParameterDialogBuilder withInitialData(String name, Map<String, Object> props) {
        this.isEditMode = true;
        this.initialName = name;
        this.initialProps = props;
        return this;
    }

    public ParameterDialogBuilder onSave(BiConsumer<String, Map<String, Object>> action) {
        this.onSaveAction = action;
        return this;
    }

    public ParameterDialogBuilder withEditableFields(boolean editable){
        this.isEditable = editable;
        return this;
    }

    /**
     * Shows the key a property of {@code element} will be addressed by, next to the name field.
     *
     * @param element element the property will belong to
     * @param model   plant used to spot keys already in use
     */
    public ParameterDialogBuilder withVariableKeyPreview(S88Element element, Plc4xPlantModel model) {
        this.previewElement = element;
        this.previewModel = model;
        return this;
    }

    public void show() {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        createWidgets();

        JPanel topSection = new JPanel(new BorderLayout(15, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.weightx = 1.0;
        topSection.add(buildFormPanel(gbc), BorderLayout.CENTER);
        topSection.add(buildButtonPanel(), BorderLayout.EAST);

        wireToggleListeners();
        applyInitialData();
        toggleTypeFields();
        applyStoredDefault();

        btnCancel.addActionListener(e -> dialog.dispose());

        btnOk.addActionListener(e -> {
            if (onSaveAction != null) {
                if (!validateName(dialog)) {
                    return;
                }
                Map<String, Object> parameterBag = buildParameterBag();
                onSaveAction.accept(txtName.getText().trim(), parameterBag);
            }
            dialog.dispose();
        });

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPane.add(topSection, BorderLayout.NORTH);

        dialog.setContentPane(contentPane);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    protected void createWidgets() {
        txtName = new JTextField();
        nameHintLabel = new JLabel("Only A-Z, 0-9 and _ can be used.");
        nameHintLabel.setForeground(HINT_COLOUR);
        nameHintLabel.setFont(nameHintLabel.getFont().deriveFont(Font.PLAIN, 10f));
        ((PlainDocument) txtName.getDocument()).setDocumentFilter(
                RestrictedDocumentFilter.forIdentifier(this::showRejectedInput));
        txtName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshVariableKeyPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshVariableKeyPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshVariableKeyPreview();
            }
        });
        comboType = new JComboBox<>(DataType.displayNames());
        comboEnumeration = new JComboBox<>();
        comboEngineeringUnits = new JComboBox<>();


        txtName.setEnabled(isEditable);
        comboType.setEnabled(isEditable);
        comboEnumeration.setEnabled(isEditable);
        comboEngineeringUnits.setEnabled(isEditable);


        txtMax = new JTextField();
        ((PlainDocument) txtMax.getDocument()).setDocumentFilter(valueFilter);
        txtMin = new JTextField();
        ((PlainDocument) txtMin.getDocument()).setDocumentFilter(valueFilter);
        txtDefault = new JTextField();
        ((PlainDocument) txtDefault.getDocument()).setDocumentFilter(valueFilter);
        comboDefault = new JComboBox<>();

        txtReference = new JTextField();
        txtReference.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e){
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume();
                }
            }
        });

        defaultField = new JPanel(new CardLayout());
        defaultField.add(txtDefault, "TEXT");
        defaultField.add(comboDefault, "COMBO");

        for (S88Enumeration enumeration : enumerations) {
            if (enumeration.getName() != null) {
                comboEnumeration.addItem(enumeration.getName());
            }
        }

        for (EngineeringUnits e : EngineeringUnits.values()) {
            comboEngineeringUnits.addItem(e);
        }

        comboEngineeringUnits.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EngineeringUnits) {
                    setText(((EngineeringUnits) value).name());
                }
                return this;
            }
        });



    }

    protected boolean showRangeFields() {
        return !reportsMode;
    }


    protected JPanel buildFormPanel(GridBagConstraints gbc) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;
        addFormField(formPanel, gbc, row++, "Name", txtName);
        addFullWidthRow(formPanel, gbc, row++, nameHintLabel);
        variableKeyPreview = new VariableKeyPreviewPanel();
        variableKeyPreview.bind(previewElement, previewModel != null ? previewModel.getModel() : null);

        variableKeyPreview.setApplyName(txtName::setText);

        if (isEditMode) {
            variableKeyPreview.setEditingProperty(initialName);
        }
        addFullWidthRow(formPanel, gbc, row++, variableKeyPreview);
        addFormField(formPanel, gbc, row++, "Type", comboType);
        addFormField(formPanel, gbc, row++, "Enumeration", comboEnumeration);
        addFormField(formPanel, gbc, row++, "Engineering Unit", comboEngineeringUnits);

        if (showRangeFields()) {
            addFormField(formPanel, gbc, row++, "Max", txtMax);
            addFormField(formPanel, gbc, row++, "Min", txtMin);
            addFormField(formPanel, gbc, row++, "Default", defaultField);
        }
        if(hasReference) {
            addFormField(formPanel, gbc, row++, "Reference", txtReference);
        }

        return formPanel;
    }

    private void addFullWidthRow(JPanel parent, GridBagConstraints gbc, int row, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
        gbc.gridwidth = 1;
    }

    /**
     * Says why a keystroke was refused, so an id the use cases would reject is never a mystery.
     */
    private void showRejectedInput(String message) {
        if (nameHintLabel == null) {
            return;
        }
        nameHintLabel.setForeground(REJECTED_COLOUR);
        nameHintLabel.setText(message);
    }

    private void refreshVariableKeyPreview() {
        if (variableKeyPreview == null || nameHintLabel == null) {
            return;
        }
        if (nameHintLabel.getForeground().equals(REJECTED_COLOUR)) {
            nameHintLabel.setForeground(HINT_COLOUR);
            nameHintLabel.setText("Only A-Z, 0-9 and _ can be used.");
        }
        variableKeyPreview.update(txtName.getText());
    }

    protected ParameterDialogBuilder withReference(boolean val){
        this.hasReference = val;
        return this;
    }

    protected JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        btnOk = new JButton("OK");
        btnCancel = new JButton("Cancel");
        Dimension btnSize = new Dimension(80, 26);
        btnOk.setMaximumSize(btnSize);
        btnCancel.setMaximumSize(btnSize);
        buttonPanel.add(btnOk);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        buttonPanel.add(btnCancel);
        return buttonPanel;
    }

    protected void refreshDefaultCombo() {
        String previous = Objects.toString(comboDefault.getSelectedItem(), "");
        comboDefault.removeAllItems();
        String enumName = Objects.toString(comboEnumeration.getSelectedItem(), "");
        for (S88Enumeration enumeration : enumerations) {
            if (enumName.equals(enumeration.getName())) {
                for (String label : enumeration.getValues().keySet()) {
                    comboDefault.addItem(label);
                }
                break;
            }
        }
        if (!previous.isEmpty()) {
            comboDefault.setSelectedItem(previous);
        }
    }

    protected void toggleTypeFields() {
        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        if(isEditable) {
            comboEnumeration.setEnabled(isEnumeration);
            comboEngineeringUnits.setEnabled(!isEnumeration);
        }
        applyValueInputFilter();
        if (!showRangeFields()) {
            return;
        }
        txtMax.setEnabled(!isEnumeration);
        txtMin.setEnabled(!isEnumeration);
        CardLayout cl = (CardLayout) defaultField.getLayout();
        cl.show(defaultField, isEnumeration ? "COMBO" : "TEXT");
        if (isEnumeration) {
            refreshDefaultCombo();
        }
    }


    protected void wireToggleListeners() {
        comboType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleTypeFields();
            }
        });

        comboEnumeration.addItemListener(e -> {
            if (showRangeFields() && e.getStateChange() == ItemEvent.SELECTED) {
                refreshDefaultCombo();
            }
        });


    }

    protected void applyInitialData() {
        if (isEditMode && initialProps != null) {
            txtName.setText(initialName);
            txtName.setEditable(false);
            if (initialProps.get("Type") != null) {
                comboType.setSelectedItem(String.valueOf(initialProps.get("Type")).trim().toUpperCase());
            }
            if(initialProps.get("Reference") != null) {
                txtReference.setText(String.valueOf(initialProps.get("Reference")));
            }
            boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
            if (isEnumeration) {
                comboEnumeration.setSelectedItem(Objects.toString(initialProps.get("Eng_Units/Enum"), ""));
            } else {
                comboEngineeringUnits.setSelectedItem(EngineeringUnits.fromName(Objects.toString(initialProps.get("Eng_Units/Enum"), "")));
                if (showRangeFields()) {
                    txtMax.setText(Objects.toString(initialProps.get("Max"), ""));
                    txtMin.setText(Objects.toString(initialProps.get("Min"), ""));
                    txtDefault.setText(Objects.toString(initialProps.get("Default"), ""));
                }
            }
        }
    }

    protected void applyStoredDefault() {
        if (showRangeFields() && isEditMode && initialProps != null
                && DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""))) {
            String storedDefault = Objects.toString(initialProps.get("Default"), "");
            if (!storedDefault.isEmpty() && indexOfItem(comboDefault, storedDefault) >= 0) {
                comboDefault.setSelectedItem(storedDefault);
            }
        }
    }




    protected boolean validateName(JDialog dialog) {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "Name cannot be empty.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    protected Map<String, Object> buildParameterBag() {
        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        Map<String, Object> parameterBag = isEditMode && initialProps != null
                ? new LinkedHashMap<>(initialProps) : new LinkedHashMap<>();
        parameterBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));

        if (isEnumeration) {
            parameterBag.put("Eng_Units/Enum", Objects.toString(comboEnumeration.getSelectedItem(), ""));
            if (showRangeFields()) {
                parameterBag.put("Default", Objects.toString(comboDefault.getSelectedItem(), ""));
                parameterBag.put("Max", "");
                parameterBag.put("Min", "");
            }
        } else {
            parameterBag.put("Eng_Units/Enum", comboEngineeringUnits.getSelectedItem() != null
                    ? ((EngineeringUnits) comboEngineeringUnits.getSelectedItem()).getName()
                    : "");
            if (showRangeFields()) {
                parameterBag.put("Default", txtDefault.getText());
                parameterBag.put("Max", txtMax.getText());
                parameterBag.put("Min", txtMin.getText());
            }
        }
        parameterBag.put("Reference", safeReference(txtReference.getText()));
        return parameterBag;
    }

    private static int safeReference(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void addFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        parent.add(new JLabel(labelText), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
    }

    private static int indexOfItem(JComboBox<String> combo, String item) {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) combo.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            if (Objects.equals(item, model.getElementAt(i))) {
                return i;
            }
        }
        return -1;
    }

    protected void applyValueInputFilter() {
        valueFilter.setType(DataType.fromString(Objects.toString(comboType.getSelectedItem(), "")));
    }
}