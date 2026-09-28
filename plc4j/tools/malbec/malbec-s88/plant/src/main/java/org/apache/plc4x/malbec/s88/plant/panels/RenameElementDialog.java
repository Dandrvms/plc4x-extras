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
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.PlainDocument;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.core.NamingAdvisor;
import org.apache.plc4x.malbec.s88.core.RenameElementUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

/**
 * Asks for a new element id and keeps the window open until the name is actually accepted.
 */
public final class RenameElementDialog extends JDialog {

    private static final Color HINT_COLOUR = new Color(0x777777);
    private static final Color REJECTED_COLOUR = new Color(0xb00000);
    private static final Color IMPACT_COLOUR = new Color(0xb05000);

    private final Plc4xPlantModel model;
    private final S88Element element;
    private final JTextField txtId;
    private final JLabel hintLabel;
    private final JPanel impactPanel;
    private final JButton btnOk;

    private boolean applied;

    private RenameElementDialog(Window owner, Plc4xPlantModel model, S88Element element, String proposedId) {
        super(owner, "Rename Element", ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.element = element;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        txtId = new JTextField(proposedId != null ? proposedId : element.getId(), 24);
        ((PlainDocument) txtId.getDocument()).setDocumentFilter(
                RestrictedDocumentFilter.forIdentifier(this::showRejectedInput));

        hintLabel = new JLabel("Only A-Z, 0-9 and _ can be used.");
        hintLabel.setForeground(HINT_COLOUR);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 10f));

        impactPanel = new JPanel(new BorderLayout());
        impactPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("Element ID ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        form.add(txtId, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        form.add(new JLabel(" ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        form.add(hintLabel, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        form.add(impactPanel, gbc);

        btnOk = new JButton("Rename");
        btnOk.setPreferredSize(new Dimension(100, 26));
        JButton btnCancel = new JButton("Cancel");
        btnCancel.setPreferredSize(new Dimension(100, 26));
        btnCancel.addActionListener(e -> dispose());
        btnOk.addActionListener(e -> attemptRename());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttons.add(btnOk);
        buttons.add(btnCancel);

        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(btnOk);

        showImpact();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and applies the rename only if the name is accepted.
     *
     * @param owner      window to centre on, may be {@code null}
     * @param model      plant holding the element
     * @param element    element whose id is being changed
     * @param proposedId name the user had already typed
     * @return {@code true} when the element was actually renamed
     */
    public static boolean rename(Window owner, Plc4xPlantModel model, S88Element element, String proposedId) {
        RenameElementDialog dialog = new RenameElementDialog(owner, model, element, proposedId);
        dialog.setVisible(true);
        return dialog.applied;
    }

    private void showRejectedInput(String message) {
        hintLabel.setForeground(REJECTED_COLOUR);
        hintLabel.setText(message);
    }

    private void showImpact() {
        NamingAdvisor.Advice impact = RenameElementUseCase.impactOf(element);
        impactPanel.removeAll();
        impactPanel.setVisible(impact != null);
        if (impact != null) {
            JLabel label = new JLabel("<html><font color=#b05000>" + escape(impact.message()) + "</font></html>");
            label.setForeground(IMPACT_COLOUR);
            impactPanel.add(label, BorderLayout.WEST);
        }
        impactPanel.revalidate();
        impactPanel.repaint();
    }

    /**
     * Validates the name and renames when it is accepted.
     */
    private void attemptRename() {
        String typed = txtId.getText();
        String proposed = typed == null ? "" : typed.trim().toUpperCase(Locale.ROOT);

        try {
            RenameElementUseCase.execute(model.getModel(), element, proposed);
            model.save();
            applied = true;
            dispose();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showRejectedInput(ex.getMessage());
        } catch (Exception ex) {
            showRejectedInput("The element could not be renamed: " + ex.getMessage());
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
