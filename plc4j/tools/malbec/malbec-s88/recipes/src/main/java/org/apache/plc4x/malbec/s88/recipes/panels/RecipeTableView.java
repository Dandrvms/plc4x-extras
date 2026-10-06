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
import java.awt.Font;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;

/**
 * The steps of a recipe, one per row.
 * <p>
 * Every step of the recipe is here, whatever it is nested inside, and the column of nesting says how
 * deep it is. A recipe is a tree of steps, but a table of all of them is what answers "what is in
 * this recipe" in one look, and the nesting is a column rather than a tree because the graph view is
 * where the shape of the recipe is read.
 * <p>
 * <b>The equipment column is what the recipe says, not what is resolved.</b> A recipe written by class
 * names a class of equipment, and the module of the plant it ends up on is decided later, when it is
 * bound. Showing a module here would be showing something the recipe does not carry.
 */
public final class RecipeTableView extends AbstractRecipeView {

    private static final String[] COLUMNS =
            {"Step", "Kind", "Equipment", "Parameters"};

    private final StepTableModel tableModel = new StepTableModel();

    public RecipeTableView(RecipeEditorModel model) {
        super(model);
        JTable table = new JTable(tableModel);
        table.setFont(table.getFont().deriveFont(Font.PLAIN));
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public String viewName() {
        return "Steps";
    }

    @Override
    protected void redraw() {
        tableModel.read(model.getRecipe(), model.conformance());
    }

    /** One row per step of the recipe, flattened and with how deep each one sits. */
    private static final class StepTableModel extends AbstractTableModel {

        private final Map<S88RecipeElement, Integer> depth = new IdentityHashMap<>();
        private final Map<S88RecipeElement, List<String>> complaints = new IdentityHashMap<>();
        private List<S88RecipeElement> rows = new ArrayList<>();

        void read(S88MasterRecipe recipe, RecipeConformance conformance) {
            depth.clear();
            complaints.clear();
            rows = new ArrayList<>();
            if (recipe != null) {
                collect(recipe.getRecipeElements(), 0);
            }
            // Whatever the report says about a step is held against that step rather than shown in
            // one place, because the point of this view is to see which step is the problem.
            for (String line : conformance.excess()) {
                attach(line);
            }
            for (String line : conformance.deficit()) {
                attach(line);
            }
            fireTableDataChanged();
        }

        private void collect(List<S88RecipeElement> elements, int level) {
            for (S88RecipeElement element : elements) {
                rows.add(element);
                depth.put(element, level);
                collect(element.getRecipeElements(), level + 1);
            }
        }

        /**
         * Puts a report line against the step it names.
         * <p>
         * Matched on the quoted name the report uses, which is the same name the row shows. A line
         * that names no step in this recipe is left out, because there is no row to put it on.
         */
        private void attach(String line) {
            String quoted = between(line, '\'', '\'');
            if (quoted == null) {
                return;
            }
            for (S88RecipeElement element : rows) {
                if (quoted.equals(element.getId())) {
                    complaints.computeIfAbsent(element, key -> new ArrayList<>()).add(line);
                    return;
                }
            }
        }

        private static String between(String text, char from, char to) {
            int start = text.indexOf(from);
            if (start < 0) {
                return null;
            }
            int end = text.indexOf(to, start + 1);
            return end < 0 ? null : text.substring(start + 1, end);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            S88RecipeElement element = rows.get(row);
            return switch (column) {
                case 0 -> indent(element) + (element.getId() == null ? "" : element.getId());
                case 1 -> element.getKind() == null ? "" : element.getKind().getXmlName();
                case 2 -> equipment(element);
                case 3 -> parameters(element);
                default -> "";
            };
        }

        /**
         * The equipment the step works on, or what is wrong about it.
         * <p>
         * A step that names nothing is a step that cannot run, and on a class recipe that is the
         * normal state of a step that has not been bound yet. Saying so in this column is more use
         * than leaving it blank, and it is where an operator looks for it.
         */
        private String equipment(S88RecipeElement element) {
            List<String> problems = complaints.get(element);
            if (element.getEquipmentClassId() != null && element.getActualEquipmentIds().isEmpty()) {
                return element.getEquipmentClassId();
            }
            if (!element.getActualEquipmentIds().isEmpty()) {
                return String.join(", ", element.getActualEquipmentIds());
            }
            return problems == null || problems.isEmpty() ? "" : "not named yet";
        }

        private String parameters(S88RecipeElement element) {
            List<String> values = new ArrayList<>();
            element.getParameters().forEach(parameter -> {
                String value = parameter.getFirstValue() == null
                        ? "" : parameter.getFirstValue().getFirstValueString();
                values.add(parameter.getId() + " = " + value);
            });
            String joined = String.join("; ", values);
            List<String> problems = complaints.get(element);
            if (problems == null || problems.isEmpty()) {
                return joined;
            }
            // Anything the report says that is not about the equipment goes here, because the other
            // two columns are already saying what the step is.
            return joined.isEmpty() ? String.join(" ", problems) : joined + "  " + String.join(" ", problems);
        }

        /** Indents by how deep the step sits, so nesting is readable in a flat table. */
        private String indent(S88RecipeElement element) {
            return "    ".repeat(depth.getOrDefault(element, 0));
        }
    }
}
