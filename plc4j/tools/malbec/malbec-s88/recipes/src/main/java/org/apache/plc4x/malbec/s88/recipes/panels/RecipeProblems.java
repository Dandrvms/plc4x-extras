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
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;

/**
 * Component to see what is wrong with the recipe right now, written out under the view.
 */
final class RecipeProblems extends JPanel {

    private static final long serialVersionUID = 1L;


    private final DefaultListModel<String> lines = new DefaultListModel<>();
    private final JList<String> list = new JList<>(lines);
    private final JLabel heading = new JLabel();

    RecipeProblems() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(4);
        list.setEnabled(false);

        add(heading, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        setVisible(false);
    }

    /**
     * Says what the report found, and takes up no room when it found nothing.
     *
     * @param report what is wrong with the recipe
     */
    void show(RecipeConformance report) {
        List<String> said = saidBy(report);
        lines.clear();
        said.forEach(lines::addElement);
        heading.setText(headingFor(said.size()));
        setVisible(!said.isEmpty());
    }

    /**
     * What the report has to say, in the order that helps most.
     *
     * @param report what is wrong with the recipe
     * @return one line per finding
     */
    private static List<String> saidBy(RecipeConformance report) {
        List<String> said = new java.util.ArrayList<>();
        for (String excess : report.excess()) {
            said.add("Not runnable: " + excess);
        }
        for (String missing : report.deficit()) {
            said.add("Not finished: " + missing);
        }
        return said;
    }

    /**
     * @param howMany how many findings there are
     * @return what the heading says
     */
    private static String headingFor(int howMany) {
        if (howMany == 1) {
            return "1 thing to look at";
        }
        return howMany + " things to look at";
    }

    /** The color of the heading, which says how serious the findings are on its own. */
    Color headingColour() {
        return heading.getForeground();
    }

    /** Whether there is anything to say, which is what decides if the panel is there at all. */
    boolean hasSomethingToSay() {
        return !lines.isEmpty();
    }

    /** What it is saying, one line per finding. */
    List<String> saying() {
        return java.util.Collections.list(lines.elements());
    }
}