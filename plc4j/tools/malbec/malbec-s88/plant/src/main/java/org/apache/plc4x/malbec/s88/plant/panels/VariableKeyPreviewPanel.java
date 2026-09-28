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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.core.NameValidator;
import org.apache.plc4x.malbec.s88.core.NamingAdvisor;
import org.apache.plc4x.malbec.s88.core.VariableKeySupport;

/**
 * Shows the name a variable is published under, while the variable is being named.
 */
final class VariableKeyPreviewPanel extends JPanel {

    private final JLabel keyLabel = new JLabel();
    private final JLabel adviceLabel = new JLabel();
    private final JButton conventionButton = new JButton("Use conventional name");

    private S88Element element;
    private S88PlantModel model;
    private String propertyName = "";
    private String editingProperty;
    private String suggestedName;
    private Consumer<String> applyName;

    VariableKeyPreviewPanel() {
        super(new BorderLayout(5, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Name it will be published under"),
                BorderFactory.createEmptyBorder(0, 5, 5, 5)));

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 0, 1, 0);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        body.add(new JLabel("Name ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        keyLabel.setForeground(Color.DARK_GRAY);
        body.add(keyLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        body.add(new JLabel(" ", SwingConstants.RIGHT), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        adviceLabel.setForeground(new Color(0xb05000));
        body.add(adviceLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        conventionButton.setVisible(false);
        conventionButton.addActionListener(e -> {
            if (applyName != null && suggestedName != null) {
                applyName.accept(suggestedName);
            }
        });
        body.add(conventionButton, gbc);

        add(body, BorderLayout.CENTER);
        setVisible(false);
    }

    /**
     * Points the panel at the element that will own the property.
     *
     * @param element element the property belongs to, {@code null} to hide the panel
     * @param model   plant used to look for names already in use, may be {@code null}
     */
    void bind(S88Element element, S88PlantModel model) {
        this.element = element;
        this.model = model;
        setVisible(element != null);
        refresh();
    }

    /**
     * Receives the name the convention button offers, so the form can put it in the name field.
     *
     * @param applyName called with the conventional name when the button is pressed
     */
    void setApplyName(Consumer<String> applyName) {
        this.applyName = applyName;
    }

    /**
     * Names the property being edited so it is not reported as a collision with itself.
     *
     * @param editingProperty name of the property under edit, {@code null} when creating one
     */
    void setEditingProperty(String editingProperty) {
        this.editingProperty = editingProperty;
        refresh();
    }

    /**
     * Recomputes what to show for a typed name. Safe to call on every keystroke.
     */
    void update(String propertyName) {
        this.propertyName = propertyName != null ? propertyName.trim() : "";
        refresh();
    }

    private void refresh() {
        if (element == null) {
            keyLabel.setText("");
            adviceLabel.setText("");
            hideConvention();
            return;
        }
        if (propertyName.isEmpty()) {
            keyLabel.setText("<html><font color=gray>the name you type is the name it is published under"
                    + "</font></html>");
            adviceLabel.setText("");
            hideConvention();
            return;
        }

        String name = VariableKeySupport.resolve(element, propertyName);
        keyLabel.setText("<html>" + escape(name) + " &nbsp;<font color=gray>(" + name.length() + " of "
                + NameValidator.MAX_LENGTH + " characters)</font></html>");
        keyLabel.setToolTipText("It will be stored as \"" + name + "\", and the batch layer and PVA will "
                + "both address the variable by that same name.");

        List<NamingAdvisor.Advice> advice = new ArrayList<>();
        NamingAdvisor.Advice tooLong = NamingAdvisor.warnIfKeyTooLong(name);
        if (tooLong != null) {
            advice.add(tooLong);
        }
        NamingAdvisor.Advice taken = NamingAdvisor.warnIfKeyTaken(model, name, element, editingProperty);
        if (taken != null) {
            advice.add(taken);
        }
        adviceLabel.setText(render(advice));

        if (editingProperty == null) {
            // Offering the convention only makes sense while naming a new variable. Editing the
            // limits of an existing one cannot change its name, so the button would only tease
            // a name the form would never let the user type in.
            NamingAdvisor.Advice conventional =
                    NamingAdvisor.suggestConventionalVariableName(element, propertyName);
            suggestedName = conventional != null ? conventional.suggestion() : null;
            boolean offerable = suggestedName != null && !suggestedName.equals(name);
            suggestedName = offerable ? suggestedName : null;
            conventionButton.setVisible(offerable);
            conventionButton.setToolTipText(offerable ? "Use " + suggestedName : null);
        } else {
            hideConvention();
        }
    }

    private void hideConvention() {
        suggestedName = null;
        conventionButton.setVisible(false);
        conventionButton.setToolTipText(null);
    }

    private static String render(List<NamingAdvisor.Advice> advice) {
        if (advice.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<html>");
        for (NamingAdvisor.Advice item : advice) {
            String colour = item.severity() == NamingAdvisor.Severity.WARNING ? "#b05000" : "#666666";
            html.append("<font color=").append(colour).append(">&bull; ")
                    .append(escape(item.message()))
                    .append("</font><br>");
        }
        return html.append("</html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
