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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities.ChartOp;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.core.RecipeDeepCopy;
import org.apache.plc4x.malbec.s88.core.StructureQueries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the toolbar buttons build, checked against the rules of the drawing.
 * <p>
 * Every chart here is made by the same use cases a button press makes, so a chart that holds
 * together here is a chart the operator gets by pressing the buttons. The picture of each one is in
 * {@code target/sfc}, written by {@link SfcPicturesTest}.
 * <p>
 * The conformance already says a chart has no step nothing reaches and no line pointing at
 * something the recipe does not carry. What is checked here on top of that is the shape of the
 * lines: a split that leaves one place and arrives at more than one, a join that comes from more
 * than one and arrives at one, and a plain line between the two.
 */
class SfcButtonsMatrixTest {

    /**
     * Every chart the buttons build holds together, and every line of it has the shape its kind
     * says.
     */
    @Test
    void everyButtonChartHoldsTogetherAndItsLinesHaveTheRightShape() {
        for (S88MasterRecipe recipe : SfcFixtures.buttons()) {
            String id = recipe.getId();
            S88ProcedureLogic chart = recipe.getProcedureLogic();

            assertEquals(1, boxesOf(recipe, S88RecipeElementKind.BEGIN).size(),
                    id + " has one place the flow starts");
            assertEquals(1, boxesOf(recipe, S88RecipeElementKind.END).size(),
                    id + " has one place the flow stops");
            assertTrue(StructureQueries.linesThatDoNotCross(chart).isEmpty(),
                    id + " has a line that runs between two boxes or two bars, which the drawing"
                            + " cannot follow: " + StructureQueries.linesThatDoNotCross(chart));
            assertTrue(RecipeConformance.of(recipe).excess().isEmpty(),
                    id + " has something in the way: " + RecipeConformance.of(recipe).excess());

            for (S88ProcedureLink link : chart.getLinks()) {
                assertShape(id, link);
            }
        }
    }

    private static void assertShape(String id, S88ProcedureLink link) {
        int from = link.getFrom().size();
        int to = link.getTo().size();
        if (link.isDivergent()) {
            assertEquals(1, from, id + ": a split leaves one place, in '" + link.getId() + "'");
            assertTrue(to >= 2, id + ": a split arrives at two or more places, in '"
                    + link.getId() + "'");
        } else if (link.isConvergent()) {
            assertTrue(from >= 2, id + ": a join comes from two or more places, in '"
                    + link.getId() + "'");
            assertEquals(1, to, id + ": a join arrives at one place, in '" + link.getId() + "'");
        } else {
            assertEquals(1, from, id + ": a plain line leaves one place, in '" + link.getId() + "'");
            assertEquals(1, to, id + ": a plain line arrives at one place, in '" + link.getId() + "'");
        }
    }

    /** A block asked for twice has three branches, and a block asked for once has two. */
    @Test
    void aBlockHasTheBranchesItWasAskedFor() {
        assertSplit("BOTONES_PARALELA", 2, true);
        assertSplit("BOTONES_PARALELA_TRES", 3, true);
        assertSplit("BOTONES_ALTERNATIVA", 2, false);
        assertSplit("BOTONES_ALTERNATIVA_TRES", 3, false);
    }

    private static void assertSplit(String id, int sides, boolean parallel) {
        List<S88ProcedureLink> splits = chartOf(id).getLinks().stream()
                .filter(S88ProcedureLink::isDivergent)
                .filter(link -> link.getLinkType().isParallel() == parallel)
                .toList();
        assertEquals(1, splits.size(), id + " has one split of that kind");
        assertEquals(sides, splits.get(0).getTo().size(),
                id + " splits towards " + sides + " branches");
    }

    /**
     * A block put under a step of another is a block of its own, and the block it is put in is left
     * alone. A parallel block inside a parallel one adds a parallel split; a block of turns inside
     * one adds a split of turns.
     */
    @Test
    void aBlockPutInsideAnotherAddsABlockOfItsOwnKind() {
        assertBlocks("BOTONES_PARALELA_EN_ALTERNATIVA", 1, 1);
        assertBlocks("BOTONES_ALTERNATIVA_EN_PARALELA", 1, 1);
        assertBlocks("BOTONES_PARALELA_EN_PARALELA", 2, 0);
        assertBlocks("BOTONES_ALTERNATIVA_EN_ALTERNATIVA", 0, 2);
    }

    private static void assertBlocks(String id, int parallelBlocks, int selectiveBlocks) {
        List<S88ProcedureLink> splits = chartOf(id).getLinks().stream()
                .filter(S88ProcedureLink::isDivergent)
                .toList();
        long parallel = splits.stream().filter(link -> link.getLinkType().isParallel()).count();
        long selective = splits.size() - parallel;
        assertEquals(parallelBlocks, parallel, id + " has " + parallelBlocks + " blocks that run"
                + " at once");
        assertEquals(selectiveBlocks, selective, id + " has " + selectiveBlocks + " blocks of turns");
        for (S88ProcedureLink split : splits) {
            assertEquals(2, split.getTo().size(), id + ": every block of the matrix has two branches");
        }
    }

    /**
     * For every fixture and every order, the answer and the real order agree.
     *
     * <p>The candidate picks cover each step, each bar, each line, each pair of steps, and the
     * whole chart. When the answer is enabled, a fresh copy runs it without throwing. When the
     * answer is refused, a fresh copy throws rather than running, and the reason says why. A
     * {@link NullPointerException} is never an answer.
     */
    @Test
    void everyOrderThatIsOfferedRunsAndEveryRefusalHolds() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            String id = recipe.getId();
            List<Set<String>> picks = picksOf(recipe.getProcedureLogic());
            boolean anyOffered = false;
            for (ChartOp op : ChartOp.values()) {
                for (Set<String> pick : picks) {
                    ChartCapabilities.Capability answer =
                            ChartCapabilities.can(recipe, null, op, pick);
                    S88MasterRecipe trial = RecipeDeepCopy.copyMasterRecipe(recipe);
                    if (answer.enabled()) {
                        anyOffered = true;
                        assertDoesNotThrow(
                                () -> ChartCapabilities.perform(trial, null, op, pick),
                                id + ": " + op + " was offered for " + pick + " but did not run");
                        continue;
                    }
                    assertTrue(answer.reason() != null && !answer.reason().isBlank(),
                            id + ": " + op + " is refused without a reason");
                    RuntimeException refused = assertThrows(RuntimeException.class,
                            () -> ChartCapabilities.perform(trial, null, op, pick),
                            id + ": " + op + " was refused for " + pick + " but ran");
                    assertFalse(refused instanceof NullPointerException,
                            id + ": " + op + " was refused for " + pick + " with a null pointer");
                }
            }
            assertTrue(anyOffered, id + " has no order that can run on any pick");
        }
    }

    /** Picks to try each order against: none, each node, each line, each pair of steps, all. */
    private static List<Set<String>> picksOf(S88ProcedureLogic chart) {
        List<Set<String>> picks = new ArrayList<>();
        picks.add(Set.of());
        List<String> boxes = chart.getSteps().stream().map(S88ProcedureStep::getId).toList();
        List<String> bars = chart.getTransitions().stream()
                .map(S88ProcedureTransition::getId).toList();
        for (String box : boxes) {
            picks.add(Set.of(box));
        }
        for (String bar : bars) {
            picks.add(Set.of(bar));
        }
        for (S88ProcedureLink line : chart.getLinks()) {
            picks.add(Set.of(line.getId()));
        }
        for (int left = 0; left < boxes.size(); left++) {
            for (int right = left + 1; right < boxes.size(); right++) {
                picks.add(Set.of(boxes.get(left), boxes.get(right)));
            }
        }
        Set<String> every = new LinkedHashSet<>(boxes);
        every.addAll(bars);
        chart.getLinks().forEach(line -> every.add(line.getId()));
        picks.add(every);
        return picks;
    }

    private static S88ProcedureLogic chartOf(String id) {
        return SfcFixtures.buttons().stream()
                .filter(recipe -> id.equals(recipe.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button chart called '" + id + "'"))
                .getProcedureLogic();
    }

    private static List<String> boxesOf(S88MasterRecipe recipe, S88RecipeElementKind kind) {
        return recipe.getProcedureLogic().getSteps().stream()
                .filter(step -> recipe.findElement(step.getRecipeElementId())
                        .map(element -> element.getKind() == kind)
                        .orElse(false))
                .map(S88ProcedureStep::getId)
                .toList();
    }
}
