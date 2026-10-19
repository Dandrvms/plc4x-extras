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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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

    /** The node each line is about, lined up with the lines, {@code null} for a line about none. */
    private List<String> picks = List.of();

    /** What to do with the node of a picked line, which is to put the frame on it. */
    private Consumer<String> onPick = node -> { };

    RecipeProblems() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(4);
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                pickTheNodeOfTheChosenLine();
            }
        });

        add(heading, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        setVisible(false);
    }

    /**
     * Says what to do when a line is picked, which is to put the frame on the node it is about.
     *
     * @param picker what to do with the id of the node
     */
    void onPick(Consumer<String> picker) {
        this.onPick = picker;
    }

    /**
     * Puts the frame on the node of the chosen line, and takes the choice off again so the same
     * line can be picked a second time.
     */
    private void pickTheNodeOfTheChosenLine() {
        int chosen = list.getSelectedIndex();
        if (chosen < 0 || chosen >= picks.size()) {
            return;
        }
        String node = picks.get(chosen);
        if (node == null) {
            return;
        }
        onPick.accept(node);
        list.clearSelection();
    }

    /**
     * Picks the line at the given place, as clicking it would, which is how a test drives the
     * picking without a pointer.
     *
     * @param line place of the line, counted from the top
     */
    void pickLineAt(int line) {
        if (line >= 0 && line < lines.size()) {
            list.setSelectedIndex(line);
        }
    }

    /**
     * Says what the report found, and takes up no room when it found nothing.
     *
     * @param report what is wrong with the recipe
     */
    void show(RecipeConformance report) {
        show(saidBy(report));
    }

    /**
     * Says what the report found, what the chart could not be arranged as, and what the editor is
     * warning about, taking up no room when there is nothing to say.
     *
     * @param report          what is wrong with the recipe
     * @param chartComplaints what the layout could not arrange, empty when it arranged everything
     * @param warning         what the editor has to say, or {@code null} for nothing
     */
    void show(RecipeConformance report, List<String> chartComplaints, String warning) {
        List<String> said = saidBy(report);
        List<String> nodes = new ArrayList<>(picksOf(report));
        for (String complaint : chartComplaints) {
            said.add(complaint);
            nodes.add(null);
        }
        if (warning != null) {
            said.add(warning);
            nodes.add(null);
        }
        show(said, nodes);
    }

    /**
     * Says what is wrong, and takes up no room when nothing is.
     *
     * @param said one line per thing to say
     */
    void show(List<String> said) {
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < said.size(); i++) {
            nodes.add(null);
        }
        show(said, nodes);
    }

    /**
     * Says what is wrong and which node each line is about.
     *
     * @param said  one line per thing to say
     * @param nodes the node each line is about, lined up with the lines
     */
    private void show(List<String> said, List<String> nodes) {
        lines.clear();
        said.forEach(lines::addElement);
        picks = new ArrayList<>(nodes);
        heading.setText(headingFor(said.size()));
        setVisible(!said.isEmpty());
    }

    /**
     * Everything the panel has to say, in the order that helps most: what the recipe cannot run,
     * what the chart cannot be arranged as, and then whatever the editor is warning about.
     *
     * @param report          what is wrong with the recipe
     * @param chartComplaints what the layout could not arrange, empty when it arranged everything
     * @param warning         what the editor has to say, or {@code null} for nothing
     * @return one line per thing to say
     */
    static List<String> toShow(RecipeConformance report, List<String> chartComplaints, String warning) {
        List<String> said = new java.util.ArrayList<>(saidBy(report));
        said.addAll(chartComplaints);
        if (warning != null) {
            said.add(warning);
        }
        return said;
    }

    /**
     * What the report has to say, in the order that helps most.
     *
     * @param report what is wrong with the recipe
     * @return one line per finding
     */
    static List<String> saidBy(RecipeConformance report) {
        List<String> said = new java.util.ArrayList<>();
        for (RecipeConformance.Finding finding : report.excessFindings()) {
            said.add("Not runnable: " + finding.message());
        }
        for (RecipeConformance.Finding finding : report.deficitFindings()) {
            said.add("Not finished: " + finding.message());
        }
        for (RecipeConformance.Finding finding : report.warningFindings()) {
            said.add("Warning: " + finding.message());
        }
        return said;
    }

    /**
     * The node each finding is about, lined up with {@link #saidBy}.
     *
     * @param report what is wrong with the recipe
     * @return the node of each finding, {@code null} for a finding about the recipe
     */
    private static List<String> picksOf(RecipeConformance report) {
        List<String> nodes = new java.util.ArrayList<>();
        for (RecipeConformance.Finding finding : report.excessFindings()) {
            nodes.add(finding.nodeId());
        }
        for (RecipeConformance.Finding finding : report.deficitFindings()) {
            nodes.add(finding.nodeId());
        }
        for (RecipeConformance.Finding finding : report.warningFindings()) {
            nodes.add(finding.nodeId());
        }
        return nodes;
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