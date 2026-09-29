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

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.PlainDocument;
import java.awt.*;

public class ClassDialogBuilder {
    private final JDialog dialog;
    private final JPanel formPanel;
    private JTextField txtClassName;
    private int currentRow = 0;
    private final Boolean showButtons;

    private Runnable onOkAction;

    public ClassDialogBuilder(String title) {
        this(title, true, null);
    }

    public ClassDialogBuilder(String title, Window owner) {
        this(title, true, owner);
    }

    public ClassDialogBuilder(String title, boolean showButtons) {
        this(title, showButtons, null);
    }

    public ClassDialogBuilder(String title, boolean showButtons, Window owner) {
        dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        formPanel = new JPanel(new GridBagLayout());
        this.showButtons = showButtons;
    }

    public ClassDialogBuilder withNameField() {
        txtClassName = new JTextField(20);
        JLabel hint = new JLabel("Only A-Z, 0-9 and _ can be used.");
        hint.setForeground(new Color(0x777777));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 10f));
        ((PlainDocument) txtClassName.getDocument()).setDocumentFilter(
                RestrictedDocumentFilter.forIdentifier(message -> {
                    hint.setForeground(new Color(0xb00000));
                    hint.setText(message);
                }));
        addRow("Equipment Type Name:", txtClassName);
        addRow("", hint);
        return this;
    }

    public ClassDialogBuilder withReadOnlyNameField(String value) {
        JTextField field = new JTextField(value != null ? value : "");
        field.setEditable(false);
        addRow("Class Name:", field);
        return this;
    }

    public void addRow(String labelText, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = currentRow;
        gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(component, gbc);
        currentRow++;
    }

    public ClassDialogBuilder addComponentRow(JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridwidth = 2; gbc.gridx = 0; gbc.gridy = currentRow;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(component, gbc);
        currentRow++;
        return this;
    }

    public ClassDialogBuilder onAccept(Runnable action) {
        this.onOkAction = action;
        return this;
    }

    public String getClassName() {
        return txtClassName != null ? txtClassName.getText() : "";
    }

    public JDialog getDialog() {
        return dialog;
    }

    public JDialog build() {
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(15, 15, 15, 15));

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);
        contentPane.add(scrollPane, BorderLayout.CENTER);
        if(Boolean.TRUE.equals(showButtons)) {
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            JButton btnOk = new JButton("OK");
            JButton btnCancel = new JButton("Cancel");
            btnOk.setPreferredSize(new Dimension(80, 26));
            btnCancel.setPreferredSize(new Dimension(80, 26));

            btnCancel.addActionListener(e -> dialog.dispose());

            btnOk.addActionListener(e -> {
                try {
                    if (onOkAction != null) {
                        onOkAction.run();
                    }
                    dialog.dispose();
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            });

            buttonPanel.add(btnOk);
            buttonPanel.add(btnCancel);
            contentPane.add(buttonPanel, BorderLayout.SOUTH);
        }
        dialog.setContentPane(contentPane);

        if(Boolean.TRUE.equals(showButtons)) {
            dialog.getRootPane().setDefaultButton((JButton) null);
        }
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        return dialog;
    }
}
