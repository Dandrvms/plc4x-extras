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
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Joining and separating nodes that may already have more than one line.
 */
class ChartEditOpsTest {

    @Test
    void aLineThatWasTakenOffCanBePutBack() {
        S88MasterRecipe recipe = withOneStep();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        removeLinksTouching(chart, "BEGIN");
        assertFalse(ChartSoundness.check(chart).isEmpty(), "and the chart cannot run without it");

        S88ProcedureLink joined = ChartEditOps.connect(recipe, null, "BEGIN", "T_STEP");

        assertEquals(S88LinkType.CONTROL_LINK, joined.getLinkType());
        assertEquals(List.of("T_STEP"), targetsOf(chart, "BEGIN"));
        assertTrue(ChartSoundness.check(chart).isEmpty(), "and the chart runs again");
    }

    @Test
    void aSkipCanBeTakenOffWhileTheStepItSkippedStays() {
        S88MasterRecipe recipe = withOneStep();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        S88ProcedureLink skip = new S88ProcedureLink("L_SKIP");
        skip.addFrom(S88IdRef.transition("T_STEP"));
        skip.addTo(S88IdRef.transition("T1"));
        skip.setLinkType(S88LinkType.CONTROL_LINK);
        chart.addLink(skip);

        ChartEditOps.disconnect(recipe, null, "T_STEP", "T1");

        assertFalse(targetsOf(chart, "T_STEP").contains("T1"),
                "and the step before is still reached through the step, not around it");
        assertTrue(ChartSoundness.check(chart).isEmpty());
    }

    @Test
    void takingOffALineTheFlowNeedsIsRefused() {
        S88MasterRecipe recipe = withOneStep();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        List<S88ProcedureLink> before = List.copyOf(chart.getLinks());

        assertThrows(IllegalStateException.class,
                () -> ChartEditOps.disconnect(recipe, null, "BEGIN", "T_STEP"));

        assertEquals(before, List.copyOf(chart.getLinks()), "and the chart is left as it was");
    }

    @Test
    void theSkipOfAnOptionalStepCanBeTakenOff() {
        S88MasterRecipe recipe = withOneStep();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP");
        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertTrue(chart.findTransition("T_SKIP").isPresent());
        assertTrue(ChartSoundness.check(chart).isEmpty());

        ChartEditOps.removeSkip(recipe, null, "T_SKIP", "T_STEP_1");

        assertTrue(chart.findTransition("T_SKIP").isEmpty(), "and the bar that went round is gone");
        assertTrue(ChartSoundness.check(chart).isEmpty(), "and the run is the plain one again");
    }

    @Test
    void severalBranchEndsCanBeBroughtTogetherOnANewBar() {
        S88MasterRecipe recipe = withOneStep();
        ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertTrue(ChartSoundness.check(chart).isEmpty());

        S88ProcedureTransition joined = ChartEditOps.joinBranches(
                recipe, null, List.of("STEP", "STEP_1"), "T_BOTH", "END");

        assertEquals("T_BOTH", joined.getId());
        assertEquals(List.of("STEP", "STEP_1"), sourcesOf(chart, "T_BOTH"),
                "and the new bar waits for both branches");
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, lineInto(chart, "T_BOTH").getLinkType());
        assertTrue(chart.findTransition("T1").isEmpty(), "and the old join is gone");
        assertEquals(List.of("END"), targetsOf(chart, "T_BOTH"));
        assertTrue(ChartSoundness.check(chart).isEmpty());
    }

    @Test
    void aBarCanBeMadeToFeedTwoBranchesThatAlreadyComeBackTogether() {
        S88MasterRecipe recipe = withOneStep();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        removeLinksTouching(chart, "T_STEP_1");
        chart.removeTransition(chart.findTransition("T_STEP_1").orElseThrow());
        removeLinksEndingAt(chart, "T1");
        S88ProcedureLink join = new S88ProcedureLink("L_JOIN");
        join.addFrom(S88IdRef.step("STEP"));
        join.addFrom(S88IdRef.step("STEP_1"));
        join.addTo(S88IdRef.transition("T1"));
        join.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        chart.addLink(join);
        assertFalse(ChartSoundness.check(chart).isEmpty(), "and one branch has no way in yet");

        S88ProcedureLink split = ChartEditOps.splitAfter(
                recipe, null, "T_STEP", List.of("STEP", "STEP_1"));

        assertEquals(List.of("STEP", "STEP_1"), targetsOf(chart, "T_STEP"));
        assertEquals(S88LinkType.PARALLEL_DIVERGENT, split.getLinkType());
        assertEquals(S88LinkType.PARALLEL_CONVERGENT, lineInto(chart, "T1").getLinkType());
        assertTrue(ChartSoundness.check(chart).isEmpty(), "and both branches now run");
    }

    @Test
    void aSplitNeedsTwoDifferentSteps() {
        S88MasterRecipe recipe = withOneStep();

        assertThrows(IllegalArgumentException.class,
                () -> ChartEditOps.splitAfter(recipe, null, "T_STEP", List.of("STEP")));
    }

    private static S88MasterRecipe withOneStep() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        return recipe;
    }

    private static void removeLinksTouching(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            if (contains(link.getFrom(), id) || contains(link.getTo(), id)) {
                chart.removeLink(link);
            }
        }
    }

    private static void removeLinksEndingAt(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            if (contains(link.getTo(), id)) {
                chart.removeLink(link);
            }
        }
    }

    private static boolean contains(List<S88IdRef> ends, String id) {
        return ends.stream().anyMatch(end -> id.equals(end.getValue()));
    }

    private static S88ProcedureLink lineInto(S88ProcedureLogic chart, String id) {
        return chart.getLinks().stream()
                .filter(link -> contains(link.getTo(), id))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> sourcesOf(S88ProcedureLogic chart, String id) {
        return lineInto(chart, id).getFrom().stream().map(S88IdRef::getValue).toList();
    }

    private static List<String> targetsOf(S88ProcedureLogic chart, String id) {
        List<String> targets = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (contains(link.getFrom(), id)) {
                link.getTo().forEach(end -> targets.add(end.getValue()));
            }
        }
        return targets;
    }
}
