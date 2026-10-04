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

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.core.BaseNameSupport;
import org.apache.plc4x.malbec.s88.core.CreateElementUseCase;
import org.apache.plc4x.malbec.s88.core.NamingAdvisor;
import org.apache.plc4x.malbec.s88.core.VariableKeySupport;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.PlainDocument;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;

public class NewElementDialog extends JDialog{

    private static final Color HINT_COLOUR = new Color(0x777777);
    private static final Color REJECTED_COLOUR = new Color(0xb00000);

    private final JList<S88ElementClass> classList;
    private final DefaultListModel<S88ElementClass> classListModel;
    private final Plc4xPlantModel model;
    private final S88Element parent;
    private JTextField txtClass;
    private JTextField IDField;
    private JLabel idHintLabel;
    private JLabel variableKeyPreviewLabel;
    private JLabel variableKeyAdviceLabel;
    private JButton btnApplySuggestion;
    private JCheckBox chkCreateNewType;
    private JButton btnNewClass;
    private String pendingSuggestion;

    public NewElementDialog(Plc4xPlantModel model, S88Element parent, List<S88ElementClass> definedClasses) {

        this.classListModel = new DefaultListModel<>();
        for(S88ElementClass ec : definedClasses){
            this.classListModel.addElement(ec);
        }

        classList = new JList<>(classListModel);

        this.parent = parent;
        this.model = model;

        setTitle("Create New Element");
        setModal(true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));



        mainPanel.add(createTopPanel(), BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        add(mainPanel);


        if (!definedClasses.isEmpty() && !chkCreateNewType.isSelected()) {
            classList.setSelectedIndex(0);
        }

        IDField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateVariableKeyPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateVariableKeyPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateVariableKeyPreview();
            }
        });
        updateVariableKeyPreview();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createTopPanel() {
        JPanel middlePanel = new JPanel(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));

        JPanel headerClassesPanel = new JPanel(new BorderLayout());
        headerClassesPanel.add(new JLabel("Equipment types"), BorderLayout.WEST);

        chkCreateNewType = new JCheckBox("Create a new equipment type", true);
        chkCreateNewType.addActionListener(e -> applyClassMode());

        btnNewClass = new JButton("New Equipment Type");
        btnNewClass.setEnabled(false);

        btnNewClass.addActionListener(e -> {
            S88Level childLevel = parent.getLevel() != null ? parent.getLevel().getChildLevel() : null;
            int sizeBefore = classListModel.size();
            ClassFactory.createDialog(parent, model, this);


            S88ElementClass created = null;
            if (model.getModel() != null) {
                List<S88ElementClass> updated = model.getModel().getClassesForChildLevel(childLevel);
                for (S88ElementClass ec : updated) {
                    if (!containsClass(ec)) {
                        classListModel.addElement(ec);
                        if (classListModel.size() > sizeBefore) {
                            created = ec;
                        }
                    }
                }
            }
            if (created != null) {
                classList.setSelectedValue(created, true);
            }
        });
        headerClassesPanel.add(btnNewClass, BorderLayout.EAST);


        leftPanel.add(headerClassesPanel, BorderLayout.NORTH);

        classList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof S88ElementClass) {
                    setText(((S88ElementClass) value).getName());
                }
                return this;
            }
        });
        classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        classList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && classList.getSelectedValue() != null) {
                S88ElementClass ec = classList.getSelectedValue();
                txtClass.setText(ec.getName().toUpperCase());
                updateVariableKeyPreview();
            }
        });

        classList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                if (e.getClickCount() == 2) {
                    int index = classList.locationToIndex(e.getPoint());
                    if (index >= 0 && classList.getCellBounds(index, index).contains(e.getPoint())) {
                        S88ElementClass ec = classList.getModel().getElementAt(index);
                        ClassFactory.showClass(ec);
                    }
                }
            }
        });

        JScrollPane listScrollPane = new JScrollPane(classList);
        listScrollPane.setPreferredSize(new Dimension(250, 0));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        leftPanel.add(chkCreateNewType, BorderLayout.SOUTH);
        middlePanel.add(leftPanel, BorderLayout.WEST);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("General", createDetailsTab());
        middlePanel.add(tabbedPane, BorderLayout.CENTER);

        applyClassMode();

        return middlePanel;
    }

    /**
     * Switches the dialog between creating a brand new equipment type (derived from the element id,
     * the default, so the class list never silently captures a first-class selection) and reusing
     * an existing one.
     */
    private void applyClassMode() {
        if (chkCreateNewType == null || btnNewClass == null) {
            return;
        }
        boolean createNew = chkCreateNewType.isSelected();
        boolean leaf = parent.getLevel() == null || parent.getLevel().getChildLevel() == null;
        classList.setEnabled(!createNew && !leaf);
        btnNewClass.setEnabled(!createNew && !leaf);
        if (createNew) {
            classList.clearSelection();
        } else if (classList.getSelectedValue() == null && classListModel.size() > 0) {
            classList.setSelectedIndex(0);
        }
        updateVariableKeyPreview();
    }

    private boolean containsClass(S88ElementClass candidate) {
        for (int i = 0; i < classListModel.size(); i++) {
            if (Objects.equals(classListModel.get(i).getName(), candidate.getName())) {
                return true;
            }
        }
        return false;
    }

    private JPanel createDetailsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        S88Level childLevel = parent.getLevel() != null ? parent.getLevel().getChildLevel() : null;
        IDField = createField("", true);
        idHintLabel = new JLabel("Only A-Z, 0-9 and _ can be used.");
        idHintLabel.setForeground(HINT_COLOUR);
        idHintLabel.setFont(idHintLabel.getFont().deriveFont(Font.PLAIN, 10f));
        ((PlainDocument) IDField.getDocument()).setDocumentFilter(
                RestrictedDocumentFilter.forIdentifier(this::showRejectedInput));
        addFormRow(panel, gbc, row++, "Element ID", IDField);
        addFormRow(panel, gbc, row++, " ", idHintLabel);
        addFormRow(panel, gbc, row++, "Level", createField(childLevel != null ? childLevel.toString() : "", false));


        txtClass = createField("", false);
        addFormRow(panel, gbc, row++, "Equipment type", txtClass);

        variableKeyPreviewLabel = new JLabel();
        variableKeyPreviewLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        variableKeyPreviewLabel.setForeground(Color.GRAY);
        gbc.gridy = row++;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Variable key preview ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(variableKeyPreviewLabel, gbc);

        variableKeyAdviceLabel = new JLabel();
        variableKeyAdviceLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        gbc.gridy = row++;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(" ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(variableKeyAdviceLabel, gbc);

        btnApplySuggestion = new JButton("Use suggested name");
        btnApplySuggestion.setVisible(false);
        btnApplySuggestion.addActionListener(e -> {
            String suggestion = pendingSuggestion;
            if (suggestion != null) {
                IDField.setText(suggestion);
                IDField.requestFocusInWindow();
            }
        });
        gbc.gridy = row++;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(" ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(btnApplySuggestion, gbc);

        updateVariableKeyPreview();


        return panel;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field) {        gbc.gridy = row;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(labelText + " ", SwingConstants.RIGHT), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }

    private JTextField createField(String text, Boolean enabled) {
        JTextField tf = new JTextField(text);
        tf.setEnabled(enabled);
        return tf;
    }

    /**
     * Says why a keystroke was refused, so an id the use cases would reject is never a mystery.
     */
    private void showRejectedInput(String message) {
        if (idHintLabel == null) {
            return;
        }
        idHintLabel.setForeground(new Color(0xb00000));
        idHintLabel.setText(message);
    }

    /**
     * Shows how a property of the new element would be addressed by a downstream consumer, so
     * an id that produces overlong or clashing keys is noticed while naming rather than later
     * when batch or comm reads the plant. Purely informational: the structural convention is a
     * suggestion the user may take or leave, and creation itself stays governed by the use cases.
     */
    private void updateVariableKeyPreview() {
        if (variableKeyPreviewLabel == null) {
            return;
        }
        if (idHintLabel != null && idHintLabel.getForeground().equals(REJECTED_COLOUR)) {
            idHintLabel.setForeground(HINT_COLOUR);
            idHintLabel.setText("Only A-Z, 0-9 and _ can be used.");
        }
        String id = IDField.getText();
        if (id == null || id.isBlank()) {
            variableKeyPreviewLabel.setText("enter an Element ID to see the resulting key");
            showAdvice(List.of());
            return;
        }

        S88ElementClass selected = classList.getSelectedValue();
        if (selected == null) {
            txtClass.setText(BaseNameSupport.baseIdOf(id) != null
                    ? BaseNameSupport.baseIdOf(id).toUpperCase() : "");
            txtClass.setToolTipText("No equipment type chosen: this type will be created from the"
                    + " element id.");
        }
        S88Element probe = new S88Element().setId(id.trim().toUpperCase());
        S88Level childLevel = parent.getLevel() != null ? parent.getLevel().getChildLevel() : null;
        probe.setLevel(childLevel);
        probe.setClass(selected);
        probe.setParent(parent);

        String id2 = id;
        List<String> names = variableNamesOf(selected, id);
        if (names.isEmpty()) {
            variableKeyPreviewLabel.setText(selected == null
                    ? "no equipment type selected: the type derived from the id is created empty"
                    : "this equipment type brings no variable names");
        } else {
            String first = names.get(0);
            variableKeyPreviewLabel.setText("<html><body style='width: 320px;'>" + escape(first)
                    + (names.size() > 1 ? " &nbsp;<font color=gray>(and " + (names.size() - 1)
                            + " more)</font>" : "") + "</html>");
            variableKeyPreviewLabel.setToolTipText(
                    "This element will publish: " + String.join(", ", names));
        }

        List<NamingAdvisor.Advice> advice = new ArrayList<>();
        for (String name : names) {
            NamingAdvisor.Advice tooLong = NamingAdvisor.warnIfKeyTooLong(name);
            if (tooLong != null) {
                advice.add(tooLong);
            }
            NamingAdvisor.Advice taken = NamingAdvisor.warnIfKeyTaken(model.getModel(), name);
            if (taken != null) {
                advice.add(taken);
            }
        }
        NamingAdvisor.Advice convention = NamingAdvisor.suggestConventionalId(probe, id2);
        if (convention != null) {
            advice.add(convention);
        }
        showAdvice(advice);
    }

    /**
     * The names the variables of a class will be published under once it is instantiated: an
     * instance names its variables after itself, so each base name of the class is re-suffixed
     * with the element id ({@code TEMPERATURA_SP} becoming {@code TEMPERATURA_SP_OLLA_1}).
     */
    private static List<String> variableNamesOf(S88ElementClass elementClass, String id) {
        List<String> names = new ArrayList<>();
        if (elementClass == null) {
            return names;
        }
        for (String container : List.of(VariableKeySupport.PARAMETERS, VariableKeySupport.REPORTS)) {
            if (elementClass.getProperty(container) instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    names.add(BaseNameSupport.rePrefixed(String.valueOf(key), null, id));
                }
            }
        }
        return names;
    }

    private void showAdvice(List<NamingAdvisor.Advice> advice) {
        pendingSuggestion = advice.stream()
                .map(NamingAdvisor.Advice::suggestion)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        btnApplySuggestion.setVisible(pendingSuggestion != null);
        btnApplySuggestion.setToolTipText(pendingSuggestion == null
                ? null
                : "Replace the Element ID with " + pendingSuggestion);

        if (advice.isEmpty()) {
            variableKeyAdviceLabel.setText("");
        } else {
            StringBuilder html = new StringBuilder("<html><body style='width: 320px;'>");
            for (NamingAdvisor.Advice item : advice) {
                String colour = item.severity() == NamingAdvisor.Severity.WARNING ? "#b05000" : "#666666";
                html.append("<font color=").append(colour).append(">&bull; ")
                    .append(escape(item.message()));
                if (item.suggestion() != null) {
                    html.append(" Suggested: <b>").append(escape(item.suggestion())).append("</b>");
                }
                html.append("</font><br>");
            }
            html.append("</html>");
            variableKeyAdviceLabel.setText(html.toString());
        }
        revalidate();
        pack();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton btnCreate = new JButton("Create");
        btnCreate.setPreferredSize(new Dimension(100, 26));

        if (parent.getLevel() == null || parent.getLevel().getChildLevel() == null) {
            btnCreate.setEnabled(false);
            btnCreate.setToolTipText("Cannot create an element under a leaf level.");
        }


        btnCreate.addActionListener(this::actionPerformed);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setPreferredSize(new Dimension(100, 26));
        btnCancel.addActionListener(e -> dispose());

        bottomPanel.add(btnCreate);
        bottomPanel.add(btnCancel);

        return bottomPanel;
    }

    private void actionPerformed(ActionEvent e) {
        S88ElementClass selected = classList.getSelectedValue();

            try {
                S88Element currentParent = model.getModel().findById(parent.getId()).orElse(parent);
                CreateElementUseCase.execute(model.getModel(), currentParent, IDField.getText(), selected);
                model.save();
                dispose();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
    }
}
