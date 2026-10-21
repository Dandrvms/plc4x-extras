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

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.apache.plc4x.malbec.s88.core.RecipeFlowAnalyzer;

/** The rows of a route, as a table. */
final class RecipeFlowTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS =
            {"#", "Level", "Chart (path)", "Type", "Element", "Detail", "Flow", "Note"};

    private List<RecipeFlowAnalyzer.Row> rows = new ArrayList<>();

    /**
     * Shows another route, replacing everything the table held before.
     *
     * @param route the route to show
     */
    void read(RecipeFlowAnalyzer.Route route) {
        rows = new ArrayList<>(route.rows());
        fireTableDataChanged();
    }

    /**
     * The route row at a place in the table.
     *
     * @param row place of the row, counted from the top
     * @return the row, or {@code null} when there is no row there
     */
    RecipeFlowAnalyzer.Row rowAt(int row) {
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }

    /**
     * The place of the first row about a chart node.
     *
     * @param nodeId the node to find, as the chart names it
     * @return the place of the row, or -1 when the route does not name that node
     */
    int findRow(String nodeId) {
        return findRow(nodeId, null);
    }

    /**
     * The place of the row about a chart node in one chart.
     * <p>
     * Two charts can hold a node of the same name, so the chart decides which row is meant. A node
     * that is not in that chart still finds a row if the route has one elsewhere.
     *
     * @param nodeId    the node to find, as the chart names it
     * @param chartPath the path of the chart that holds it, {@code null} to accept any chart
     * @return the place of the row, or -1 when the route does not name that node
     */
    int findRow(String nodeId, String chartPath) {
        if (nodeId == null) {
            return -1;
        }
        int byId = findRow(nodeId, chartPath, true);
        return byId >= 0 ? byId : findRow(nodeId, chartPath, false);
    }

    private int findRow(String nodeId, String chartPath, boolean byId) {
        int elsewhere = -1;
        for (int row = 0; row < rows.size(); row++) {
            RecipeFlowAnalyzer.Row candidate = rows.get(row);
            String named = byId ? candidate.id() : candidate.elementId();
            if (!nodeId.equals(named)) {
                continue;
            }
            if (chartPath == null || chartPath.equals(candidate.chartPath())) {
                return row;
            }
            if (elsewhere < 0) {
                elsewhere = row;
            }
        }
        return elsewhere;
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
        RecipeFlowAnalyzer.Row route = rows.get(row);
        return switch (column) {
            case 0 -> route.index();
            case 1 -> route.depth();
            case 2 -> route.chartPath();
            case 3 -> kind(route.kind());
            case 4 -> indent(route) + route.label();
            case 5 -> route.detail();
            case 6 -> route.flow();
            case 7 -> route.note();
            default -> "";
        };
    }

    private static String indent(RecipeFlowAnalyzer.Row row) {
        int branches = row.flow() == null || row.flow().isBlank()
                ? 0 : row.flow().split("\\.").length - 1;
        return "    ".repeat(Math.max(0, row.depth() + branches));
    }

    private static String kind(RecipeFlowAnalyzer.RowKind kind) {
        return switch (kind) {
            case BEGIN -> "start";
            case STEP -> "step";
            case TRANSITION -> "transition";
            case FORK_PARALLEL -> "\u2551 parallel split";
            case JOIN_PARALLEL -> "\u2551 parallel join";
            case CHOICE -> "\u25c7 choice";
            case MERGE -> "\u25c7 merge";
            case SKIP -> "\u293c skip";
            case LOOP_BACK -> "\u21ba loop back";
            case SUBCHART_ENTER -> "enter subchart";
            case SUBCHART_EXIT -> "exit subchart";
            case END -> "end";
        };
    }
}
