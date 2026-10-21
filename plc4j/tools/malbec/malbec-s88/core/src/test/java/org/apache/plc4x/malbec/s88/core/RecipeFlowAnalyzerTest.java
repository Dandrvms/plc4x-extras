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
package org.apache.plc4x.malbec.s88.core;

import java.util.List;

import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.junit.jupiter.api.Test;

import static org.apache.plc4x.malbec.s88.core.ChartFixtures.link;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.master;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.step;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.steps;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.transition;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.transitions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFlowAnalyzerTest {

    @Test
    void aChainIsReadFromStartToStop() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(chain());

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  Transition T1",
                "3  Step STEP",
                "4  Transition T2",
                "5  END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void aParallelBlockIsReadOnce() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(parallel());

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  in parallel (P1)  [2 branches]",
                "3    [P1.a] Step A",
                "4    [P1.b] Step B",
                "5  join",
                "6  END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void aSelectiveBlockIsReadOnce() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(selective());

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  Transition T0",
                "3  Step STEP",
                "4  choice (A1)  [2 paths]",
                "5    [A1.1] Transition T_A",
                "6    [A1.1] Step A",
                "7    [A1.1] Transition T_END_A",
                "8    [A1.2] Transition T_B",
                "9    [A1.2] Step B",
                "10    [A1.2] Transition T_END_B",
                "11  join",
                "12  END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void aLoopIsReadOnce() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(loop());

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  Transition T_OPEN",
                "3  Step A  (loop start)",
                "4  Transition T_NEXT",
                "5  Step B",
                "6  choice (A1)  [2 paths]",
                "7    [A1.1] Transition T_BACK",
                "8    [A1.1] back to A",
                "9    [A1.2] Transition T_EXIT",
                "10    [A1.2] END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void anOptionalBranchEmitsASkip() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(optional());

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  choice (A1)  [2 paths]",
                "3    [A1.1] Step A",
                "4    [A1.2] no steps  [skips to T_CLOSE]",
                "5  join",
                "6  END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void aPartialSynchronizationIsReadOnce() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(partialSync());

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  in parallel (P1)  [3 branches]",
                "3    [P1.a] Step A",
                "4    [P1.b] Step B",
                "5    [P1.c] Step C",
                "6  join",
                "7  in parallel (P2)  [2 branches]",
                "8    [P2.a] Step D",
                "9    [P2.b] Step E",
                "10  join",
                "11  END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void aSubchartIsExpanded() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(subchart(false));

        assertEquals(String.join("\n",
                "1  BEGIN",
                "2  Transition T1",
                "3  Step U",
                "4  subchart U",
                "5    BEGIN",
                "6    Transition T_C1",
                "7    Step CHILD",
                "8    Transition T_C2",
                "9    END",
                "10  end of subchart U",
                "11  Transition T2",
                "12  END"),
                RecipeFlowFormatter.toText(route));
    }

    @Test
    void aRecursiveSubchartIsNotExpanded() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(subchart(true));

        assertTrue(route.warnings().stream().anyMatch(warning -> warning.contains("recursive")),
                () -> "expected a recursion warning in " + route.warnings());
        assertEquals(1, route.rows().stream()
                .filter(row -> row.kind() == RecipeFlowAnalyzer.RowKind.SUBCHART_ENTER)
                .count());
    }

    @Test
    void aShortLimitIsReportedAsAWarning() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(chain(),
                new RecipeFlowAnalyzer.Options(false, 3, 12));

        assertEquals(3, route.rows().size());
        assertTrue(route.warnings().stream().anyMatch(warning -> warning.contains("cut after 3 rows")),
                () -> "expected a truncation warning in " + route.warnings());
    }

    @Test
    void csvKeepsTheSameRows() {
        RecipeFlowAnalyzer.Route route = RecipeFlowAnalyzer.analyze(chain());

        List<String> lines = RecipeFlowFormatter.toCsv(route).lines().toList();

        assertEquals(6, lines.size());
        assertEquals("index,depth,chart,kind,id,elementId,label,detail,flow,note", lines.get(0));
        assertTrue(lines.get(1).startsWith("1,0,R,BEGIN,BEGIN,"));
    }

    private static S88MasterRecipe chain() {
        S88MasterRecipe recipe = master("STEP");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_STEP", transition("T1"), step("STEP"));
        link(chart, "L_NEXT", step("STEP"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));
        return recipe;
    }

    private static S88MasterRecipe parallel() {
        S88MasterRecipe recipe = master("A", "B");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T1");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_SPLIT", List.of(transition("T0")), steps("A", "B"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_JOIN", steps("A", "B"), List.of(transition("T1")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_END", transition("T1"), step("END"));
        return recipe;
    }

    private static S88MasterRecipe selective() {
        S88MasterRecipe recipe = master("STEP", "A", "B");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T_A");
        transition(chart, "T_B");
        transition(chart, "T_END_A");
        transition(chart, "T_END_B");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_STEP", transition("T0"), step("STEP"));
        link(chart, "L_SPLIT", List.of(step("STEP")), transitions("T_A", "T_B"),
                S88LinkType.SERIAL_DIVERGENT);
        link(chart, "L_A", transition("T_A"), step("A"));
        link(chart, "L_B", transition("T_B"), step("B"));
        link(chart, "L_END_A", step("A"), transition("T_END_A"));
        link(chart, "L_END_B", step("B"), transition("T_END_B"));
        link(chart, "L_STOP_A", transition("T_END_A"), step("END"));
        link(chart, "L_STOP_B", transition("T_END_B"), step("END"));
        return recipe;
    }

    private static S88MasterRecipe loop() {
        S88MasterRecipe recipe = master("A", "B");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T_OPEN");
        transition(chart, "T_NEXT");
        transition(chart, "T_BACK");
        transition(chart, "T_EXIT");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T_OPEN"));
        link(chart, "L_A", transition("T_OPEN"), step("A"));
        link(chart, "L_NEXT", step("A"), transition("T_NEXT"));
        link(chart, "L_B", transition("T_NEXT"), step("B"));
        link(chart, "L_BACK", step("B"), transition("T_BACK"));
        link(chart, "L_AGAIN", transition("T_BACK"), step("A"));
        link(chart, "L_EXIT", step("B"), transition("T_EXIT"));
        link(chart, "L_END", transition("T_EXIT"), step("END"));
        return recipe;
    }

    private static S88MasterRecipe optional() {
        S88MasterRecipe recipe = master("A");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T_OPEN");
        transition(chart, "T_CLOSE");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T_OPEN"));
        link(chart, "L_STEP", transition("T_OPEN"), step("A"));
        link(chart, "L_SKIP", transition("T_OPEN"), transition("T_CLOSE"));
        link(chart, "L_JOIN", step("A"), transition("T_CLOSE"));
        link(chart, "L_END", transition("T_CLOSE"), step("END"));
        return recipe;
    }

    private static S88MasterRecipe partialSync() {
        S88MasterRecipe recipe = master("A", "B", "C", "D", "E");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T_OPEN");
        transition(chart, "T_MID");
        transition(chart, "T_CLOSE");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T_OPEN"));
        link(chart, "L_OPEN", List.of(transition("T_OPEN")), steps("A", "B", "C"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_MID", steps("A", "B", "C"), List.of(transition("T_MID")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_SPLIT", List.of(transition("T_MID")), steps("D", "E"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_CLOSE", steps("D", "E"), List.of(transition("T_CLOSE")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_END", transition("T_CLOSE"), step("END"));
        return recipe;
    }

    private static S88MasterRecipe subchart(boolean recursive) {
        S88MasterRecipe recipe = master("U");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_U", transition("T1"), step("U"));
        link(chart, "L_NEXT", step("U"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));

        S88RecipeElement unit = recipe.findElement("U").orElseThrow();
        unit.setKind(S88RecipeElementKind.UNIT_PROCEDURE);
        unit.setProcedureLogic(childChart(recursive));
        return recipe;
    }

    private static S88ProcedureLogic childChart(boolean recursive) {
        S88MasterRecipe childRecipe = master(recursive ? "U" : "CHILD");
        S88ProcedureLogic child = childRecipe.getProcedureLogic();
        String inner = recursive ? "U" : "CHILD";
        transition(child, "T_C1");
        transition(child, "T_C2");
        link(child, "L_C_BEGIN", step("BEGIN"), transition("T_C1"));
        link(child, "L_C_INNER", transition("T_C1"), step(inner));
        link(child, "L_C_NEXT", step(inner), transition("T_C2"));
        link(child, "L_C_END", transition("T_C2"), step("END"));
        return child;
    }
}
