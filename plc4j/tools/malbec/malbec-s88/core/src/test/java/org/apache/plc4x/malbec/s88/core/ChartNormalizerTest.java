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

import java.util.ArrayList;
import java.util.List;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
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

class ChartNormalizerTest {

    @Test
    void normalizingTwiceChangesNothing() {
        S88ProcedureLogic chart = chainWithNoise();

        assertTrue(ChartNormalizer.normalize(chart).isEmpty(), "the noise is repairable");
        String once = snapshot(chart);

        assertTrue(ChartNormalizer.normalize(chart).isEmpty(), "nothing is left to repair");
        assertEquals(once, snapshot(chart));
    }

    @Test
    void danglingEndsAndEmptyLinesGo() {
        S88ProcedureLogic chart = chainWithNoise();

        ChartNormalizer.normalize(chart);

        assertEquals(List.of(), chart.getDanglingReferences());
        for (S88ProcedureLink line : chart.getLinks()) {
            assertFalse(line.getFrom().isEmpty(), () -> "line " + line.getId() + " starts nowhere");
            assertFalse(line.getTo().isEmpty(), () -> "line " + line.getId() + " ends nowhere");
        }
        assertTrue(chart.findLink("EMPTY").isEmpty());
        assertTrue(chart.findLink("DANGLING").isEmpty());
    }

    @Test
    void duplicateLinesAreMerged() {
        S88ProcedureLogic chart = chainWithNoise();

        ChartNormalizer.normalize(chart);

        assertEquals(1, chart.getLinks().stream()
                .filter(line -> contains(line.getFrom(), "STEP") && contains(line.getTo(), "T2"))
                .count());
    }

    @Test
    void aDeadEndBranchIsTakenOff() {
        S88ProcedureLogic chart = master("STEP", "A").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T_A");
        transition(chart, "T_DEAD");
        transition(chart, "T_END");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_STEP", transition("T0"), step("STEP"));
        link(chart, "L_SPLIT", List.of(step("STEP")),
                transitions("T_A", "T_DEAD"), S88LinkType.SERIAL_DIVERGENT);
        link(chart, "L_A", transition("T_A"), step("A"));
        link(chart, "L_END", step("A"), transition("T_END"));
        link(chart, "L_STOP", transition("T_END"), step("END"));

        List<String> problems = ChartNormalizer.normalize(chart);

        assertEquals(List.of(), problems);
        assertTrue(chart.findTransition("T_DEAD").isEmpty());
        assertEquals(S88LinkType.CONTROL_LINK,
                chart.findLink("L_SPLIT").orElseThrow().getLinkType());
        assertEquals(List.of(), ChartSoundness.check(chart));
    }

    @Test
    void aDeadEndThatCannotBeRepairedIsReported() {
        S88ProcedureLogic chart = master("STEP").getProcedureLogic();
        transition(chart, "T0");
        transition(chart, "T_DEAD");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T0"));
        link(chart, "L_STEP", transition("T0"), step("STEP"));
        link(chart, "L_DEAD", step("STEP"), transition("T_DEAD"));

        List<String> problems = ChartNormalizer.normalize(chart);

        assertTrue(chart.findTransition("T_DEAD").isPresent());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("T_DEAD"), problems.getFirst());
        assertTrue(problems.getFirst().contains("no exit"), problems.getFirst());
    }

    @Test
    void anOrphanComponentGoes() {
        S88ProcedureLogic chart = chainWithNoise();
        step(chart, "ORPHAN");
        transition(chart, "T_ORPHAN");
        step(chart, "LOST");
        link(chart, "L_ORPHAN", step("ORPHAN"), transition("T_ORPHAN"));
        link(chart, "L_LOST", transition("T_ORPHAN"), step("LOST"));

        ChartNormalizer.normalize(chart);

        assertTrue(chart.findStep("ORPHAN").isEmpty());
        assertTrue(chart.findTransition("T_ORPHAN").isEmpty());
        assertTrue(chart.findStep("LOST").isEmpty());
    }

    @Test
    void onlyTheUnreachableEndOfASharedLineGoes() {
        S88ProcedureLogic chart = master("STEP", "ORPHAN").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        transition(chart, "T_SHARED");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_STEP", transition("T1"), step("STEP"));
        link(chart, "L_NEXT", step("STEP"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));
        link(chart, "L_SHARED", step("STEP"), transition("T_SHARED"));
        link(chart, "L_SHARED_END", transition("T_SHARED"), step("END"));
        link(chart, "L_ORPHAN", step("ORPHAN"), transition("T_SHARED"));

        ChartNormalizer.normalize(chart);

        assertTrue(chart.findStep("ORPHAN").isEmpty());
        assertTrue(chart.findTransition("T_SHARED").isPresent());
        assertTrue(chart.findLink("L_SHARED").isPresent());
        assertEquals(1, chart.getLinks().stream()
                .filter(line -> contains(line.getTo(), "T_SHARED"))
                .count());
    }

    @Test
    void aSkipLeftDanglingByDeletionIsSweptUp() {
        S88ProcedureLogic chart = master("STEP", "STEP_2").getProcedureLogic();
        transition(chart, "T_START");
        transition(chart, "T_STEP");
        transition(chart, "T_SKIP");
        transition(chart, "T_GONE");
        transition(chart, "T_END");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T_START"));
        link(chart, "L_STEP", transition("T_START"), step("STEP"));
        link(chart, "L_SPLIT", List.of(step("STEP")),
                transitions("T_STEP", "T_SKIP"), S88LinkType.SERIAL_DIVERGENT);
        link(chart, "L_GO", transition("T_STEP"), step("STEP_2"));
        link(chart, "L_SKIP", transition("T_SKIP"), transition("T_GONE"));
        link(chart, "L_END", step("STEP_2"), transition("T_END"));
        link(chart, "L_STOP", transition("T_END"), step("END"));

        List<String> problems = ChartNormalizer.normalize(chart);

        assertEquals(List.of(), problems);
        assertTrue(chart.findTransition("T_SKIP").isEmpty(), "the skip goes");
        assertTrue(chart.findTransition("T_GONE").isEmpty(), "and so does what it pointed at");
        assertEquals(S88LinkType.CONTROL_LINK,
                chart.findLink("L_SPLIT").orElseThrow().getLinkType());
        assertEquals(List.of(), ChartSoundness.check(chart));
    }

    @Test
    void relinkReadsTheShapeFromTheEnds() {
        S88ProcedureLogic chart = master("X", "Y").getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        transition(chart, "T3");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_DIVERGE", List.of(step("X")), transitions("T2", "T3"),
                S88LinkType.CONTROL_LINK);
        link(chart, "L_CONVERGE", transitions("T2", "T3"), List.of(step("Y")),
                S88LinkType.CONTROL_LINK);
        link(chart, "L_PARALLEL", List.of(transition("T1")), steps("X", "Y"),
                S88LinkType.CONTROL_LINK);
        link(chart, "L_JOIN", steps("X", "Y"), List.of(transition("T1")),
                S88LinkType.CONTROL_LINK);

        ChartNormalizer.relink(chart);

        assertEquals(S88LinkType.CONTROL_LINK, chart.findLink("L_BEGIN").orElseThrow().getLinkType());
        assertEquals(S88LinkType.SERIAL_DIVERGENT, chart.findLink("L_DIVERGE").orElseThrow().getLinkType());
        assertEquals(S88LinkType.SERIAL_CONVERGENT, chart.findLink("L_CONVERGE").orElseThrow().getLinkType());
        assertEquals(S88LinkType.PARALLEL_DIVERGENT, chart.findLink("L_PARALLEL").orElseThrow().getLinkType());
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, chart.findLink("L_JOIN").orElseThrow().getLinkType());
    }

    private static S88ProcedureLogic chainWithNoise() {
        S88MasterRecipe recipe = master("STEP");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        transition(chart, "T1");
        transition(chart, "T2");
        link(chart, "L_BEGIN", step("BEGIN"), transition("T1"));
        link(chart, "L_STEP", transition("T1"), step("STEP"));
        link(chart, "L_NEXT", step("STEP"), transition("T2"));
        link(chart, "L_END", transition("T2"), step("END"));

        link(chart, "DUPLICATE", step("STEP"), transition("T2"));
        S88ProcedureLink dangling = new S88ProcedureLink("DANGLING");
        dangling.addFrom(transition("T2"));
        dangling.addTo(transition("MISSING"));
        chart.addLink(dangling);
        chart.addLink(new S88ProcedureLink("EMPTY"));
        return chart;
    }

    private static boolean contains(List<S88IdRef> ends, String id) {
        return ends.stream().anyMatch(end -> id.equals(end.getValue()));
    }

    private static String snapshot(S88ProcedureLogic chart) {
        List<String> parts = new ArrayList<>();
        chart.getSteps().stream().map(box -> "S:" + box.getId()).sorted().forEach(parts::add);
        chart.getTransitions().stream().map(bar -> "T:" + bar.getId()).sorted().forEach(parts::add);
        chart.getLinks().stream()
                .map(line -> "L:" + line.getId() + ":" + line.getLinkType() + ":"
                        + ends(line.getFrom()) + "->" + ends(line.getTo()))
                .sorted()
                .forEach(parts::add);
        return String.join("\n", parts);
    }

    private static String ends(List<S88IdRef> ends) {
        return ends.stream().map(S88IdRef::toString).sorted().toList().toString();
    }
}
