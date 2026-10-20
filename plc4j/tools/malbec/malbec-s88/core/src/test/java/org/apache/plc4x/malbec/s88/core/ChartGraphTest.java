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
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.junit.jupiter.api.Test;

import static org.apache.plc4x.malbec.s88.core.ChartFixtures.link;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.master;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.step;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.steps;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.transition;
import static org.apache.plc4x.malbec.s88.core.ChartFixtures.transitions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartGraphTest {

    @Test
    void everyLineIntoANodeIsCounted() {
        S88ProcedureLogic chart = parallelChart();
        ChartGraph graph = ChartGraph.of(chart);

        assertEquals(List.of("A", "B"), graph.preds("T1"));
        assertEquals(List.of("A", "B"), graph.succs("T0"));
        assertEquals(List.of("T0"), graph.preds("A"));
        assertEquals(List.of("T1"), graph.succs("A"));
    }

    @Test
    void aLineBackToAnEarlierNodeIsABackEdge() {
        S88ProcedureLogic chart = master("A", "B").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T1");
        transition(chart, "T_BACK");
        transition(chart, "T_EXIT");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_A", transition("T0"), step("A"));
        link(chart, "L_NEXT", step("A"), transition("T1"));
        link(chart, "L_B", transition("T1"), step("B"));
        link(chart, "L_BACK", step("B"), transition("T_BACK"));
        link(chart, "L_AGAIN", transition("T_BACK"), step("A"));
        link(chart, "L_EXIT", step("B"), transition("T_EXIT"));
        link(chart, "L_END", transition("T_EXIT"), step("END"));

        assertTrue(ChartGraph.of(chart).backEdges()
                .contains(new ChartGraph.Edge("T_BACK", "A")));
    }

    @Test
    void aTransitionToATransitionIsASkip() {
        S88ProcedureLogic chart = optionalChart();
        ChartGraph graph = ChartGraph.of(chart);

        assertTrue(graph.isSkip("T_OPEN", "T_CLOSE"));
        assertFalse(graph.isSkip("A", "T_CLOSE"));
        assertEquals(List.of("T_OPEN", "A"), graph.preds("T_CLOSE"));
    }

    @Test
    void aChainFiresFromStartToStop() {
        S88ProcedureLogic chart = master("STEP").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_STEP", transition("T1"), step("STEP"));
        link(chart, "L_NEXT", step("STEP"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));
        ChartGraph graph = ChartGraph.of(chart);

        Marking marking = graph.initialMarking();
        assertEquals(Set.of("BEGIN"), marking.active());
        assertEquals(List.of("T1"), graph.enabled(marking));
        marking = graph.fire(marking, "T1");
        assertEquals(Set.of("STEP"), marking.active());
        assertEquals(List.of("T2"), graph.enabled(marking));
        marking = graph.fire(marking, "T2");
        assertEquals(Set.of("END"), marking.active());
        assertTrue(graph.enabled(marking).isEmpty());
    }

    @Test
    void aParallelSplitPutsATokenOnEveryBranch() {
        S88ProcedureLogic chart = parallelChart();
        ChartGraph graph = ChartGraph.of(chart);

        Marking marking = graph.fire(graph.initialMarking(), "T0");

        assertEquals(1, marking.count("A"));
        assertEquals(1, marking.count("B"));
        assertEquals(List.of("T1"), graph.enabled(marking));
        assertEquals(Set.of("END"), graph.fire(marking, "T1").active());
    }

    @Test
    void aSelectiveSplitChoosesOneBranch() {
        S88ProcedureLogic chart = master("STEP", "A", "B").getProcedureLogic();
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
        ChartGraph graph = ChartGraph.of(chart);

        List<ChartGraph.Firing> after = graph.successors(graph.fire(graph.initialMarking(), "T0"));

        assertEquals(2, after.size());
        assertEquals(Set.of("A"), after.get(0).marking().active());
        assertEquals(Set.of("B"), after.get(1).marking().active());
    }

    @Test
    void anOptionalBranchCanSkipItsStep() {
        S88ProcedureLogic chart = optionalChart();
        ChartGraph graph = ChartGraph.of(chart);

        List<ChartGraph.Firing> after = graph.successors(graph.initialMarking());
        ChartGraph.Firing throughTheStep = after.stream()
                .filter(firing -> firing.marking().has("A"))
                .findFirst()
                .orElseThrow();
        ChartGraph.Firing aroundTheStep = after.stream()
                .filter(firing -> firing.marking().has("END"))
                .findFirst()
                .orElseThrow();

        assertEquals(Set.of("T_OPEN"), throughTheStep.firedTransitions());
        assertEquals(Set.of("T_OPEN", "T_CLOSE"), aroundTheStep.firedTransitions());
    }

    @Test
    void aMarkingCanHoldTwoTokensAtOneStep() {
        Marking marking = Marking.of("A", "A");

        assertEquals(2, marking.count("A"));
        assertEquals("A=2;", marking.key());
        assertEquals(1, marking.remove("A").count("A"));
        assertEquals(0, marking.remove("A").remove("A").count("A"));
    }

    private static S88ProcedureLogic parallelChart() {
        S88ProcedureLogic chart = master("A", "B").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T1");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_SPLIT", List.of(transition("T0")), steps("A", "B"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_JOIN", steps("A", "B"), List.of(transition("T1")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_END", transition("T1"), step("END"));
        return chart;
    }

    private static S88ProcedureLogic optionalChart() {
        S88ProcedureLogic chart = master("A").getProcedureLogic();
        transition(chart, "T_OPEN");
        transition(chart, "T_CLOSE");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T_OPEN"));
        link(chart, "L_STEP", transition("T_OPEN"), step("A"));
        link(chart, "L_SKIP", transition("T_OPEN"), transition("T_CLOSE"));
        link(chart, "L_JOIN", step("A"), transition("T_CLOSE"));
        link(chart, "L_END", transition("T_CLOSE"), step("END"));
        return chart;
    }

}
