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

import org.apache.plc4x.malbec.s88.core.NameValidator;
import org.apache.plc4x.malbec.s88.core.NamingAdvisor;
import org.apache.plc4x.malbec.s88.core.VariableKeySupport;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows what an imported plant gets wrong with its variable names, and lets the user decide
 * whether to bring it in anyway.
 */
public final class ModelDefectsDialog {

    private static final String IMPORT_ANYWAY = "Import anyway";
    private static final String CANCEL = "Cancel";

    private ModelDefectsDialog() {
        /* This utility class should not be instantiated */
    }

    /**
     * Lists the defects of an imported plant and asks whether to continue.
     *
     * @param parent component to center the dialog on, may be {@code null}
     * @param report defects to show, as gathered by
     *               {@link NamingAdvisor#describeModel(org.apache.plc4x.malbec.s88.api.S88PlantModel)}
     * @return {@code true} when the user chose to import the model anyway, {@code false} when
     *         they cancelled
     */
    public static boolean showConfirm(Component parent, NamingAdvisor.ModelReport report) {
        JTree tree = buildTree(report);
        expandAll(tree);

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(620, 340));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(new JLabel("<html><body style='width:600px'>"
                + summary(report) + "</body></html>"), BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);

        int choice = JOptionPane.showOptionDialog(parent, content,
                "Imported Model Has Naming Problems",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new Object[] {IMPORT_ANYWAY, CANCEL}, IMPORT_ANYWAY);
        return choice == 0;
    }

    private static String summary(NamingAdvisor.ModelReport report) {
        List<String> problems = new ArrayList<>();
        if (!report.conflicts().isEmpty()) {
            problems.add(count(report.conflicts().size(), "variable key")
                    + " used by more than one property");
        }
        if (!report.overlong().isEmpty()) {
            problems.add(count(report.overlong().size(), "variable name")
                    + " longer than the recommended " + NameValidator.MAX_LENGTH + " characters");
        }
        if (!report.duplicateIds().isEmpty()) {
            problems.add(count(report.duplicateIds().size(), "element id")
                    + " reused by more than one element");
        }

        return "<b>This model needs attention before it can be used.</b>"
                + "<br>It publishes " + join(problems) + "."
                + "<br><br>Variable names are derived from the element id, the class and the"
                + " property name, so renaming any of them resolves the problem. Nothing is changed"
                + " by importing, and nothing is rejected: you can import the model and fix it"
                + " afterwards.";
    }

    private static String count(int value, String noun) {
        return value + " " + noun + (value == 1 ? "" : "s");
    }

    /** Joins with commas and a final "and", so the sentence reads like English. */
    private static String join(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1))
                + " and " + items.getLast();
    }

    private static JTree buildTree(NamingAdvisor.ModelReport report) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Problems found");

        if (!report.conflicts().isEmpty()) {
            DefaultMutableTreeNode collisions = new DefaultMutableTreeNode(
                    "Variable keys used by more than one property (" + report.conflicts().size() + ")");
            for (VariableKeySupport.VariableKeyConflict conflict : report.conflicts()) {
                DefaultMutableTreeNode entry = new DefaultMutableTreeNode("'" + conflict.key() + "' ("
                        + conflict.owners().size() + " properties)");
                for (String owner : conflict.owners()) {
                    entry.add(new DefaultMutableTreeNode(owner));
                }
                collisions.add(entry);
            }
            root.add(collisions);
        }

        if (!report.overlong().isEmpty()) {
            DefaultMutableTreeNode overlong = new DefaultMutableTreeNode(
                    "Variable names over " + NameValidator.MAX_LENGTH + " characters ("
                            + report.overlong().size() + ")");
            for (VariableKeySupport.VariableKey variable : report.overlong()) {
                DefaultMutableTreeNode entry = new DefaultMutableTreeNode("'" + variable.key() + "' ("
                        + variable.key().length() + " characters)");
                entry.add(new DefaultMutableTreeNode(variable.owner()));
                overlong.add(entry);
            }
            root.add(overlong);
        }

        if (!report.duplicateIds().isEmpty()) {
            DefaultMutableTreeNode duplicates = new DefaultMutableTreeNode(
                    "Element ids used more than once (" + report.duplicateIds().size() + ")");
            for (String id : report.duplicateIds()) {
                duplicates.add(new DefaultMutableTreeNode(id));
            }
            root.add(duplicates);
        }

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        return tree;
    }

    private static void expandAll(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }
}
