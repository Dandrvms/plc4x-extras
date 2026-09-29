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
import org.apache.plc4x.malbec.s88.core.ClassConformance;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ConfigPanelBuilder {
    private final JPanel mainPanel;
    private final JPanel centerPanel;
    private final JPanel bottomPanel;
    private final S88Element element;

    public ConfigPanelBuilder(S88Element element) {
        this.element = element;
        this.mainPanel = new JPanel(new BorderLayout(5, 10));
        this.mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        this.centerPanel = new JPanel(new BorderLayout());
        this.bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
    }

    public ConfigPanelBuilder withInfoPanel() {
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Info", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);

        JTextField name = new JTextField(element.getId());
        name.setEditable(false);

        JTextField elementClass = new JTextField(element.getElementClass() != null ? element.getElementClass().getName() : "");
        elementClass.setEditable(false);

        JTextField parent = new JTextField(element.getParent() != null ? element.getParent().getId() : "");
        parent.setEditable(false);

        int row = 0;
        addFormField(optionsPanel, gbc, row++, "Name:", name);
        addFormField(optionsPanel, gbc, row++, "Class:", elementClass);
        addFormField(optionsPanel, gbc, row++, "Parent:", parent);

        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        return this;
    }


    public ConfigPanelBuilder withCenterComponent(String title, JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        if (title != null && !title.isEmpty()) {
            wrapper.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                    title, TitledBorder.LEFT, TitledBorder.TOP));
        }
        wrapper.add(component, BorderLayout.CENTER);
        centerPanel.add(wrapper, BorderLayout.CENTER);
        return this;
    }

    public ConfigPanelBuilder addBottomButton(JButton button) {
        bottomPanel.add(button);
        return this;
    }

    /**
     * Says, above the editing area, how far the element has drifted from the class it belongs to,
     * and offers the two ways of closing the gap. Both are additive: adding to the class changes
     * what every sibling of the type is expected to publish, so the impact is spelled out before it
     * is applied, and aligning the element touches no sibling at all.
     *
     * @param addToClass     the action that pushes the element's own base names into the class
     * @param alignWithClass the action that publishes on the element what the class already declares
     */
    public ConfigPanelBuilder withConformancePanel(JButton addToClass, JButton alignWithClass) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Equipment type conformance", TitledBorder.LEFT, TitledBorder.TOP));

        JLabel summary = new JLabel(" ");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        buttons.add(addToClass);
        buttons.add(alignWithClass);

        panel.add(summary, BorderLayout.NORTH);
        panel.add(buttons, BorderLayout.CENTER);
        mainPanel.add(panel, BorderLayout.SOUTH);

        addToClass.setName(CONFORMANCE_ADD);
        alignWithClass.setName(CONFORMANCE_ALIGN);
        summary.setName(CONFORMANCE_SUMMARY);
        return this;
    }

    /** Name of the label summarizing how far the element is from its class. */
    public static final String CONFORMANCE_SUMMARY = "conformance.summary";

    /** Name of the button that adds the element's own base names to its class. */
    public static final String CONFORMANCE_ADD = "conformance.addToClass";

    /** Name of the button that makes the element publish what its class declares. */
    public static final String CONFORMANCE_ALIGN = "conformance.alignWithClass";

    /**
     * Sets the text of the conformance summary. The wording names the direction of each gap, so a
     * label is never read as if it meant the other one.
     */
    public static void setConformanceSummary(JPanel built, ClassConformance conformance) {
        if (built == null) {
            return;
        }
        JLabel summary = (JLabel) findByName(built, CONFORMANCE_SUMMARY);
        JButton addToClass = (JButton) findByName(built, CONFORMANCE_ADD);
        JButton alignWithClass = (JButton) findByName(built, CONFORMANCE_ALIGN);
        if (summary == null || addToClass == null || alignWithClass == null) {
            return;
        }
        summary.setText(describe(conformance));
        addToClass.setEnabled(!conformance.excess().isEmpty());
        alignWithClass.setEnabled(!conformance.deficit().isEmpty());
        addToClass.setToolTipText("The class declares what every instance of this type publishes, so "
                + "adding to it also tells the other instances what they are expected to publish.");
        alignWithClass.setToolTipText("Publishes on this element what the class already declares, "
                + "seeded with the class's definition. No sibling is affected.");
    }

    private static String describe(ClassConformance conformance) {
        if (conformance == null || conformance.isConforming()) {
            return "This element publishes exactly what its class declares.";
        }
        StringBuilder text = new StringBuilder("<html><body style='width: 420px;'>");
        if (!conformance.excess().isEmpty()) {
            text.append("Not in class ").append(conformance.excess().size()).append(": ")
                    .append(escape(String.join(", ", conformance.excess())));
            text.append("<br>Declared by the class, not published here: ")
                    .append(conformance.deficit().size());
        } else {
            text.append("Declared by the class, not published here: ")
                    .append(conformance.deficit().size());
        }
        if (!conformance.deficit().isEmpty()) {
            text.append(" (").append(escape(String.join(", ", conformance.deficit()))).append(")");
        }
        return text.append("</body></html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static java.awt.Component findByName(java.awt.Container container, String name) {
        for (java.awt.Component child : container.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof java.awt.Container nested) {
                java.awt.Component found = findByName(nested, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public JPanel build() {
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        if (bottomPanel.getComponentCount() > 0) {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(mainPanel, BorderLayout.CENTER);
            wrapper.add(bottomPanel, BorderLayout.SOUTH);
            wrapper.setBorder(new EmptyBorder(5, 5, 5, 5));
            return wrapper;
        }

        JPanel finalPanel = new JPanel(new BorderLayout());
        finalPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        finalPanel.add(mainPanel, BorderLayout.CENTER);
        return finalPanel;
    }
    
    private void addFormField(JPanel parentPanel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        parentPanel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        parentPanel.add(field, gbc);
    }
}