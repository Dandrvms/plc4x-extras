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
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities.ChartOp;
import org.apache.plc4x.malbec.s88.core.ChartGraph;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.core.RecipeDeepCopy;
import org.apache.plc4x.malbec.s88.core.StructureQueries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** What the new chart orders do to the charts the buttons build. */
class SfcCapabilitiesTest {

    /** Two steps on the same block can go in one edit, and one undo has both of them. */
    @Test
    void deleteTakesTwoBranchStepsInOneEdit() {
        S88MasterRecipe recipe = fixture("BOTONES_PARALELA");
        List<String> branches = branchSteps(recipe.getProcedureLogic());
        assertEquals(2, branches.size(), "the parallel fixture has two whole branches");
        Set<String> picked = Set.copyOf(branches);

        ChartCapabilities.Capability answer =
                ChartCapabilities.can(recipe, null, ChartOp.DELETE, picked);
        assertTrue(answer.enabled(), answer.reason());

        S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
        ChartCapabilities.perform(trial, null, ChartOp.DELETE, picked);
        for (String branch : branches) {
            assertTrue(trial.getProcedureLogic().findStep(branch).isEmpty(),
                    branch + " is gone");
        }
        assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                "the chart still holds together: " + RecipeConformance.of(trial).excess());
    }

    /** A multi-delete that cannot finish leaves the whole chart as it was. */
    @Test
    void refusedMultiDeleteChangesNothing() {
        S88MasterRecipe recipe = fixture("BOTONES_LINEAL");
        Set<String> picked = Set.of("STEP_1", "T_STEP");
        String before = GraphSnapshot.of(recipe.getProcedureLogic());

        assertFalse(ChartCapabilities.can(recipe, null, ChartOp.DELETE, picked).enabled());
        assertThrows(IllegalStateException.class,
                () -> ChartCapabilities.perform(recipe, null, ChartOp.DELETE, picked));
        assertEquals(before, GraphSnapshot.of(recipe.getProcedureLogic()));
    }

    /** Taking the skip off leaves the step it skipped on the chart. */
    @Test
    void removeSkipLeavesTheStepItSkipped() {
        S88MasterRecipe recipe = fixture("BOTONES_RAMA_OPCIONAL");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        boolean removed = false;
        for (String candidate : transitionsAndLines(chart)) {
            if (!ChartCapabilities.can(recipe, null, ChartOp.REMOVE_SKIP, Set.of(candidate))
                    .enabled()) {
                continue;
            }
            int links = chart.getLinks().size();
            S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
            ChartCapabilities.perform(trial, null, ChartOp.REMOVE_SKIP, Set.of(candidate));
            assertTrue(trial.getProcedureLogic().getLinks().size() < links,
                    "the skip took a line with it");
            assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                    "the chart still holds together: " + RecipeConformance.of(trial).excess());
            removed = true;
            break;
        }
        assertTrue(removed, "the optional fixture offers a skip to remove");
    }

    /** The empty branch left by flattening an optional branch can still be taken off. */
    @Test
    void removeSkipAlsoTakesOffAnEmptyBranchLeftByFlattening() {
        S88MasterRecipe recipe = fixture("RAMA_OPCIONAL_CON_RAMA");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertEquals("PACK", StructureQueries.emptyBranchTarget(chart, "T_SKIP"),
                "the flattened branch rejoins at the step after the block");

        assertTrue(ChartCapabilities.can(recipe, null, ChartOp.REMOVE_SKIP, Set.of("T_SKIP"))
                        .enabled(),
                "the empty branch is offered as something that can come off");

        S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
        ChartCapabilities.perform(trial, null, ChartOp.REMOVE_SKIP, Set.of("T_SKIP"));
        S88ProcedureLogic after = trial.getProcedureLogic();
        assertTrue(after.findTransition("T_SKIP").isEmpty(), "the empty branch is gone");
        assertFalse(after.getLinks().stream().anyMatch(link ->
                        link.getFrom().stream().anyMatch(end -> "T_SKIP".equals(end.getValue()))
                                || link.getTo().stream()
                                        .anyMatch(end -> "T_SKIP".equals(end.getValue()))),
                "and no line still names it");
        assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                "the chart still holds together: " + RecipeConformance.of(trial).excess());
    }

    /** Inserting before a step puts one more step in front of it. */
    @Test
    void insertBeforeMakesOneStepAndPicksIt() {
        S88MasterRecipe recipe = fixture("BOTONES_LINEAL");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        String before = chart.getSteps().stream()
                .map(S88ProcedureStep::getId)
                .filter(id -> ChartCapabilities.can(recipe, null, ChartOp.INSERT_BEFORE, Set.of(id))
                        .enabled())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no step takes one in front"));
        int steps = chart.getSteps().size();

        S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
        String made = ChartCapabilities.perform(trial, null, ChartOp.INSERT_BEFORE, Set.of(before));
        assertNotNull(made, "the insert says which step it made");
        assertEquals(steps + 1, trial.getProcedureLogic().getSteps().size());
        assertTrue(trial.getProcedureLogic().findStep(made).isPresent(), made + " is on the chart");
    }

    /** A step goes before a parallel fork and before a selective one. */
    @Test
    void insertBeforeForkWorksOnBothKinds() {
        assertInsertBeforeFork(SfcFixtures.parallelDivergence(), "T_SPLIT", true);
        assertInsertBeforeFork(SfcFixtures.selectiveDivergence(), "CHARGE", false);
    }

    private static void assertInsertBeforeFork(S88MasterRecipe recipe, String fork,
                                               boolean forkIsBar) {
        int steps = recipe.getProcedureLogic().getSteps().size();
        ChartCapabilities.Capability answer =
                ChartCapabilities.can(recipe, null, ChartOp.INSERT_BEFORE_FORK, Set.of(fork));
        assertTrue(answer.enabled(), answer.reason());

        S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
        String made = ChartCapabilities.perform(
                trial, null, ChartOp.INSERT_BEFORE_FORK, Set.of(fork));
        S88ProcedureLogic chart = trial.getProcedureLogic();
        assertEquals(steps + 1, chart.getSteps().size());
        S88ProcedureLink split = chart.getLinks().stream()
                .filter(S88ProcedureLink::isDivergent)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the split is gone"));
        if (forkIsBar) {
            assertEquals(List.of(made), ChartGraph.of(chart).preds(fork),
                    "the new step is the one that now leads into the fork bar");
        } else {
            assertEquals(made, split.getFrom().getFirst().getValue(),
                    "the new step is the one the split now leaves from");
        }
        assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                "the chart still holds together: " + RecipeConformance.of(trial).excess());
    }

    /** A step goes after a join, picked either on the join itself or on the fork before it. */
    @Test
    void insertAfterJoinWorksFromTheJoinAndFromTheFork() {
        assertInsertAfterJoin(SfcFixtures.parallelDivergence(), "T_MERGE", "T_SPLIT");
        assertInsertAfterJoin(SfcFixtures.selectiveDivergence(), "T_PACKED", "CHARGE");
    }

    private static void assertInsertAfterJoin(S88MasterRecipe recipe, String join, String fork) {
        for (String picked : List.of(join, fork)) {
            ChartCapabilities.Capability answer =
                    ChartCapabilities.can(recipe, null, ChartOp.INSERT_AFTER_JOIN, Set.of(picked));
            assertTrue(answer.enabled(), picked + ": " + answer.reason());

            S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
            String made = ChartCapabilities.perform(
                    trial, null, ChartOp.INSERT_AFTER_JOIN, Set.of(picked));
            S88ProcedureLogic chart = trial.getProcedureLogic();
            assertTrue(chart.findStep(made).isPresent(), made + " is on the chart");
            assertEquals(List.of(join), ChartGraph.of(chart).preds(made),
                    picked + " puts the new step straight after the join");
            assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                    "the chart still holds together: " + RecipeConformance.of(trial).excess());
        }
    }

    /** A line that the chart can do without comes off, and the chart still holds together. */
    @Test
    void deleteTakesALineTheChartCanDoWithout() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            for (S88ProcedureLink line : recipe.getProcedureLogic().getLinks()) {
                String id = line.getId();
                if (!ChartCapabilities.can(recipe, null, ChartOp.DELETE, Set.of(id)).enabled()) {
                    continue;
                }
                S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
                ChartCapabilities.perform(trial, null, ChartOp.DELETE, Set.of(id));
                assertTrue(trial.getProcedureLogic().findLink(id).isEmpty(), id + " is gone");
                assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                        "the chart still holds together: " + RecipeConformance.of(trial).excess());
                return;
            }
        }
        fail("no fixture has a line that can come off on its own");
    }

    /** Two whole branches can be put under one bar, and that bar is what the order picks. */
    @Test
    void joinBranchesPutsOneBarAfterThem() {
        S88MasterRecipe recipe = fixture("BOTONES_PARALELA");
        List<String> branches = branchSteps(recipe.getProcedureLogic());
        Set<String> picked = Set.copyOf(branches);
        ChartCapabilities.Capability answer =
                ChartCapabilities.can(recipe, null, ChartOp.JOIN_BRANCHES, picked);
        if (!answer.enabled()) {
            fail("the parallel fixture should join its branches: " + answer.reason());
        }

        S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
        String bar = ChartCapabilities.perform(trial, null, ChartOp.JOIN_BRANCHES, picked);
        assertNotNull(bar);
        assertTrue(trial.getProcedureLogic().findTransition(bar).isPresent(), bar + " is on the chart");
        assertFalse(trial.getProcedureLogic().getLinks().isEmpty());
    }

    /** A loop puts one bar between a step and itself, and only once. */
    @Test
    void loopStepPutsOneBarBetweenTheStepAndItself() {
        S88MasterRecipe recipe = fixture("BOTONES_LINEAL");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        String step = chart.getSteps().stream()
                .map(S88ProcedureStep::getId)
                .filter(id -> ChartCapabilities.can(recipe, null, ChartOp.LOOP_STEP, Set.of(id))
                        .enabled())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no step takes a loop"));
        int bars = chart.getTransitions().size();

        S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
        String bar = ChartCapabilities.perform(trial, null, ChartOp.LOOP_STEP, Set.of(step));
        S88ProcedureLogic edited = trial.getProcedureLogic();
        assertNotNull(bar);
        assertEquals(bars + 1, edited.getTransitions().size());
        assertTrue(edited.findTransition(bar).isPresent(), bar + " is on the chart");
        ChartGraph graph = ChartGraph.of(edited);
        assertEquals(List.of(step), graph.preds(bar), "the loop bar is entered only from " + step);
        assertEquals(List.of(step), graph.succs(bar), "and it goes back only into " + step);
        assertFalse(ChartCapabilities.can(trial, null, ChartOp.LOOP_STEP, Set.of(step)).enabled(),
                "the step already has a loop");
        assertTrue(RecipeConformance.of(trial).excess().isEmpty(),
                "the chart still holds together: " + RecipeConformance.of(trial).excess());
    }

    /** The stop cannot be looped, and a step inside a parallel block cannot either. */
    @Test
    void loopStepRefusesTheStopAndTheInsideOfAParallelBlock() {
        S88MasterRecipe recipe = fixture("BOTONES_PARALELA");
        assertFalse(ChartCapabilities.can(recipe, null, ChartOp.LOOP_STEP, Set.of("END")).enabled());
        assertFalse(ChartCapabilities.can(recipe, null, ChartOp.LOOP_STEP, Set.of("STEP")).enabled(),
                "STEP runs at the same time as its sibling, so it has no loop");
        assertFalse(ChartCapabilities.can(recipe, null, ChartOp.LOOP_STEP, Set.of("STEP_1")).enabled(),
                "STEP_1 runs at the same time as its sibling, so it has no loop");
    }

    private static S88MasterRecipe fixture(String id) {
        return SfcFixtures.all().stream()
                .filter(recipe -> id.equals(recipe.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no fixture called '" + id + "'"));
    }

    private static List<String> branchSteps(S88ProcedureLogic chart) {
        return chart.getSteps().stream()
                .filter(step -> {
                    S88ProcedureLink arriving = StructureQueries.arrivingAt(chart, step.getId());
                    S88ProcedureLink leaving = StructureQueries.leavingFrom(chart, step.getId());
                    return arriving != null && leaving != null
                            && arriving.isDivergent() && leaving.isConvergent();
                })
                .map(S88ProcedureStep::getId)
                .toList();
    }

    private static List<String> transitionsAndLines(S88ProcedureLogic chart) {
        List<String> ids = new java.util.ArrayList<>();
        chart.getTransitions().stream().map(S88ProcedureTransition::getId).forEach(ids::add);
        chart.getLinks().stream().map(S88ProcedureLink::getId).forEach(ids::add);
        return ids;
    }
}
