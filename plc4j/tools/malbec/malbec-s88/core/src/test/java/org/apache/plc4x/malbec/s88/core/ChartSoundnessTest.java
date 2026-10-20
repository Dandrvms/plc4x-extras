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

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88IdScope;
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

class ChartSoundnessTest {

    @Test
    void aStraightRunIsSound() {
        S88ProcedureLogic chart = master("STEP").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_STEP", transition("T1"), step("STEP"));
        link(chart, "L_NEXT", step("STEP"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));

        assertSound(chart);
    }

    @Test
    void aParallelSplitAndJoinIsSound() {
        S88ProcedureLogic chart = master("A", "B").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T1");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_SPLIT", List.of(transition("T0")), steps("A", "B"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_JOIN", steps("A", "B"), List.of(transition("T1")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_END", transition("T1"), step("END"));

        assertSound(chart);
    }

    @Test
    void aSelectiveSplitIsSound() {
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
        link(chart, "L_STOP_A", step("A"), transition("T_END_A"));
        link(chart, "L_STOP_B", step("B"), transition("T_END_B"));
        link(chart, "L_END_A", transition("T_END_A"), step("END"));
        link(chart, "L_END_B", transition("T_END_B"), step("END"));

        assertSound(chart);
    }

    @Test
    void aLoopWithAnExitIsSound() {
        S88ProcedureLogic chart = master("A", "B").getProcedureLogic();
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

        assertSound(chart);
    }

    @Test
    void anOptionalSkipIsSound() {
        S88ProcedureLogic chart = master("A").getProcedureLogic();
        transition(chart, "T_OPEN");
        transition(chart, "T_CLOSE");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T_OPEN"));
        link(chart, "L_STEP", transition("T_OPEN"), step("A"));
        link(chart, "L_SKIP", transition("T_OPEN"), transition("T_CLOSE"));
        link(chart, "L_JOIN", step("A"), transition("T_CLOSE"));
        link(chart, "L_END", transition("T_CLOSE"), step("END"));

        assertSound(chart);
    }

    @Test
    void aBranchInsideABranchIsSound() {
        S88ProcedureLogic chart = master("A", "B", "C", "D", "A2").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T_A");
        transition(chart, "T_CD");
        transition(chart, "T_JOIN");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_OUTER", List.of(transition("T0")), steps("A", "B"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_A", step("A"), transition("T_A"));
        link(chart, "L_INNER", List.of(transition("T_A")), steps("C", "D"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_INNER_JOIN", steps("C", "D"), List.of(transition("T_CD")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_A2", transition("T_CD"), step("A2"));
        link(chart, "L_JOIN", steps("A2", "B"), List.of(transition("T_JOIN")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_END", transition("T_JOIN"), step("END"));

        assertSound(chart);
    }

    @Test
    void aPartialSynchronizationIsSound() {
        S88ProcedureLogic chart = master("A", "B", "C", "D", "E").getProcedureLogic();
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

        assertSound(chart);
    }

    @Test
    void aManyToManyTransitionIsSound() {
        S88ProcedureLogic chart = master("A", "B", "C", "D", "E", "F").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_FIRST", List.of(transition("T0")), steps("A", "B", "C"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_MIDDLE", steps("A", "B", "C"), List.of(transition("T1")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_SECOND", List.of(transition("T1")), steps("D", "E", "F"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_LAST", steps("D", "E", "F"), List.of(transition("T2")),
                S88LinkType.PARALLEL_CONVERGENT);
        link(chart, "L_END", transition("T2"), step("END"));

        assertSound(chart);
    }

    @Test
    void aDeadEndIsReported() {
        S88ProcedureLogic chart = master("STEP").getProcedureLogic();
        transition(chart, "T1");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_STEP", transition("T1"), step("STEP"));

        assertReports(chart, "has no line out of it");
    }

    @Test
    void anOrphanIsReported() {
        S88ProcedureLogic chart = master("X", "Y").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        transition(chart, "T3");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_END", transition("T1"), step("END"));
        link(chart, "L_X", step("X"), transition("T2"));
        link(chart, "L_Y", transition("T2"), step("Y"));
        link(chart, "L_BACK", step("Y"), transition("T3"));
        link(chart, "L_AGAIN", transition("T3"), step("X"));

        assertReports(chart, "is not reachable from the start");
    }

    @Test
    void aStepThatGetsTwoTokensIsReported() {
        S88ProcedureLogic chart = master("A", "B", "C").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T_A");
        transition(chart, "T_B");
        transition(chart, "T1");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_SPLIT", List.of(transition("T0")), steps("A", "B"),
                S88LinkType.PARALLEL_DIVERGENT);
        link(chart, "L_A", step("A"), transition("T_A"));
        link(chart, "L_B", step("B"), transition("T_B"));
        link(chart, "L_JOIN_A", transition("T_A"), step("C"));
        link(chart, "L_JOIN_B", transition("T_B"), step("C"));
        link(chart, "L_END", step("C"), transition("T1"));
        link(chart, "L_STOP", transition("T1"), step("END"));

        assertReports(chart, "more than one token");
    }

    @Test
    void aLineFromAStepToAStepIsReported() {
        S88ProcedureLogic chart = master("A", "B").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_A", transition("T1"), step("A"));
        link(chart, "L_CROSS", step("A"), step("B"));
        link(chart, "L_END", step("B"), transition("T2"));
        link(chart, "L_STOP", transition("T2"), step("END"));

        assertReports(chart, "runs from a step to a step");
    }

    @Test
    void aChartThatLeavesTheProcedureIsOnlyWarnedAbout() {
        S88ProcedureLogic chart = master("A").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_A", transition("T1"), step("A"));
        link(chart, "L_NEXT", step("A"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));
        S88IdRef outside = new S88IdRef("OUT", S88IdRefType.STEP, S88IdScope.EXTERNAL);
        link(chart, "L_OUT", step("A"), outside);

        ChartSoundness.Report report = ChartSoundness.inspect(chart);

        assertEquals(List.of(), report.errors());
        assertFalse(report.warnings().isEmpty());
    }

    private static void assertSound(S88ProcedureLogic chart) {
        ChartSoundness.Report report = ChartSoundness.inspect(chart);
        assertEquals(List.of(), report.errors(), () -> "soundness errors: " + report.errors());
        assertEquals(List.of(), report.warnings(), () -> "soundness warnings: " + report.warnings());
    }

    private static void assertReports(S88ProcedureLogic chart, String fragment) {
        List<String> errors = ChartSoundness.check(chart);
        assertFalse(errors.isEmpty(), "the chart should be reported");
        assertTrue(errors.stream().anyMatch(error -> error.contains(fragment)),
                () -> "expected an error with '" + fragment + "' in " + errors);
    }
}
