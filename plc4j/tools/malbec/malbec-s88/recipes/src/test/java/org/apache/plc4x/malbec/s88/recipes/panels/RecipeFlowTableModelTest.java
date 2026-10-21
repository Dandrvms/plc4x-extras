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

import java.util.List;
import org.apache.plc4x.malbec.s88.core.RecipeFlowAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecipeFlowTableModelTest {

    private static RecipeFlowAnalyzer.Route route() {
        return RecipeFlowAnalyzer.analyze(SfcFixtures.lineal());
    }

    @Test
    void showsTheRouteUnderTheColumnsTheViewNames() {
        RecipeFlowTableModel model = new RecipeFlowTableModel();

        model.read(route());

        assertEquals(List.of("#", "Level", "Chart (path)", "Type", "Element", "Detail",
                "Flow", "Note"), columns(model));
        assertEquals(route().rows().size(), model.getRowCount(),
                "and one row for each thing the route passes through");
    }

    @Test
    void findsARowByTheNodeTheChartNames() {
        RecipeFlowAnalyzer.Route route = route();
        RecipeFlowTableModel model = new RecipeFlowTableModel();
        model.read(route);
        RecipeFlowAnalyzer.Row withId = route.rows().stream()
                .filter(row -> row.id() != null)
                .findFirst()
                .orElseThrow();

        assertEquals(route.rows().indexOf(withId), model.findRow(withId.id()),
                "and a chart selection lands on the row for the same node");
        assertEquals(-1, model.findRow("NOT_IN_THE_ROUTE"), "and an unknown node lands nowhere");
    }

    @Test
    void findsARowByTheElementBehindTheNode() {
        RecipeFlowAnalyzer.Route route = route();
        RecipeFlowTableModel model = new RecipeFlowTableModel();
        model.read(route);
        RecipeFlowAnalyzer.Row withElement = route.rows().stream()
                .filter(row -> row.elementId() != null)
                .findFirst()
                .orElseThrow();

        assertEquals(route.rows().indexOf(withElement), model.findRow(withElement.elementId()),
                "and a node that has no route row of its own still finds the step behind it");
    }

    @Test
    void prefersTheRowInTheChartOnShow() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(
                SfcFixtures.twoLevelsOfSubchart());
        RecipeFlowTableModel model = new RecipeFlowTableModel();
        model.read(route);
        RecipeFlowAnalyzer.Row nestedStart = route.rows().stream()
                .filter(row -> "BEGIN".equals(row.id()) && row.chartPath().contains("/"))
                .findFirst()
                .orElseThrow();

        assertEquals(route.rows().indexOf(nestedStart),
                model.findRow("BEGIN", nestedStart.chartPath()),
                "and a node of the same name in another chart does not win");
    }

    @Test
    void readsTheChartOwnerFromTheEndOfThePath() {
        assertNull(RecipeFlowView.chartOwnerId("RECIPE"),
                "and the chart of the recipe belongs to no step");
        assertEquals("TRAMO", RecipeFlowView.chartOwnerId("RECIPE/TRAMO"),
                "and a subchart belongs to the step its path ends with");
        assertEquals("PASO", RecipeFlowView.chartOwnerId("RECIPE/TRAMO/PASO"),
                "and a chart inside a chart belongs to the step nearest to it");
    }

    @Test
    void readingAnotherRouteDropsTheOldRows() {
        RecipeFlowTableModel model = new RecipeFlowTableModel();
        model.read(route());

        model.read(new RecipeFlowAnalyzer.Route(List.of(), List.of()));

        assertEquals(0, model.getRowCount(), "and the table does not keep rows it no longer shows");
    }

    private static List<String> columns(RecipeFlowTableModel model) {
        return java.util.stream.IntStream.range(0, model.getColumnCount())
                .mapToObj(model::getColumnName)
                .toList();
    }
}
