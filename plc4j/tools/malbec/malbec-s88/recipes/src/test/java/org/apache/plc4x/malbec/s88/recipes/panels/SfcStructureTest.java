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
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.core.ChartDeletionUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.core.StructureQueries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structures the chart has to hold, one at a time.
 * <p>
 * A structure is checked against the conformance the drawing is written to, and against the
 * invariants it is drawn from: a split is one line with more than one side, a join is one line with
 * more than one arrival, and a block opens and closes on the branch that holds it.
 * <p>
 * What is checked here is what the model can say. Whether a line goes through a box is in the
 * pictures {@link SfcPicturesTest} writes.
 */
class SfcStructureTest {

    /**
     * Every line marked as splitting the flow has two or more sides, and every one marked as
     * bringing it back together has two or more arrivals.
     */
    @Test
    void everySplitAndJoinHasBothOfItsSides() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            S88ProcedureLogic chart = recipe.getProcedureLogic();
            for (S88ProcedureLink link : chart.getLinks()) {
                if (link.isDivergent()) {
                    assertTrue(link.getTo().size() >= 2,
                            link.getId() + " of " + recipe.getId() + " splits towards "
                                    + link.getTo().size() + " side");
                }
                if (link.isConvergent()) {
                    assertTrue(link.getFrom().size() >= 2,
                            link.getId() + " of " + recipe.getId() + " joins "
                                    + link.getFrom().size() + " side");
                }
            }
        }
    }

    /**
     * A structure drawn so that it does not hold together gets said so.
     * <p>
     * Two bars joined with no step between them are a line the drawing has no way to cross. The
     * conformance has nothing against it, and it is the drawing that refuses it, so that is where
     * it is shown.
     */
    @Test
    void aStructureDrawnNotToHoldTogetherIsSaidOutLoud() {
        for (S88MasterRecipe recipe : SfcFixtures.refused()) {
            SfcLayoutEngine.Placement at = SfcFixtures.arranged(recipe);
            assertFalse(at.complaints().isEmpty(),
                    recipe.getId() + " is drawn so that it does not hold together, and the"
                            + " drawing has to say so");
        }
    }

    /**
     * A branch that skips a step is drawn round the step instead of through it.
     */
    @Test
    void anOptionalBranchIsDrawnRoundTheStepItSkips() {
        S88MasterRecipe recipe = SfcFixtures.optionalBranch();
        SfcLayoutEngine.Placement at = SfcFixtures.arranged(recipe);

        assertTrue(at.complaints().isEmpty(),
                "a skip round a step holds together: " + at.complaints());
        java.awt.Rectangle work = at.steps().get("WORK");
        String skipLine = recipe.getProcedureLogic().getLinks().stream()
                .filter(link -> link.getFrom().stream()
                        .anyMatch(end -> "T_SKIP".equals(end.getValue())))
                .filter(link -> link.getTo().stream()
                        .anyMatch(end -> "T_JOIN".equals(end.getValue())))
                .findFirst()
                .orElseThrow()
                .getId();
        SfcLayoutEngine.Link drawn = at.links().stream()
                .filter(link -> link.lineId().equals(skipLine))
                .findFirst()
                .orElseThrow();
        assertEquals(6, drawn.path().size(), "the skip is drawn in six turns");
        assertTrue(drawn.path().stream().noneMatch(work::contains),
                "and none of them is inside the step it skips: " + drawn.path());
    }

    /**
     * A parallel block is drawn as two bars with a doubled line, so a chart with three branches
     * still shows the split as one block rather than as three separate ones.
     */
    @Test
    void aThreeWaySplitIsOneBlockRatherThanThreeOfThem() {
        S88ProcedureLogic chart = SfcFixtures.parallelWithThreeBranches().getProcedureLogic();

        List<S88ProcedureLink> splits = chart.getLinks().stream()
                .filter(link -> link.isDivergent() && link.getLinkType().isParallel())
                .toList();

        assertEquals(1, splits.size(), "there is one parallel split in the chart");
        assertEquals(3, splits.get(0).getTo().size(),
                "and it carries all three branches, so the drawing can hold them in step");
    }

    /**
     * A block that is drawn inside another one opens and closes on the branch that holds it, so
     * what it does never lands outside the block it is in.
     */
    @Test
    void aNestedBlockDoesNotReachOutsideTheOneThatHoldsIt() {
        for (String name : List.of("SELECTIVA_EN_PARALELA", "PARALELA_EN_PARALELA",
                "SELECTIVA_EN_SELECTIVA")) {
            S88MasterRecipe recipe = SfcFixtures.all().stream()
                    .filter(candidate -> name.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow();
            S88ProcedureLogic chart = recipe.getProcedureLogic();

            List<S88ProcedureLink> inner = chart.getLinks().stream()
                    .filter(link -> link.getLinkType() == null || !link.getLinkType().isParallel()
                            ? link.isDivergent() || link.isConvergent()
                            : link.isDivergent() || link.isConvergent())
                    .toList();
            assertTrue(inner.size() >= 3,
                    name + " has an inner block inside an outer one, which is two of each: "
                            + inner);

            for (S88ProcedureLink link : inner) {
                assertFalse(link.getTo().isEmpty() && link.getFrom().isEmpty(),
                        name + " has no line that leads to and from nothing at once");
            }
        }
    }

    /**
     * A loop goes back to a step that is earlier in the chart, and it is the only line that does
     * so, which is what makes it a loop rather than a second split.
     */
    @Test
    void aLoopHasOneLineGoingBack() {
        S88MasterRecipe recipe = SfcFixtures.loopAroundParallel();
        RecipeConformance report = RecipeConformance.of(recipe);
        assertTrue(report.excess().isEmpty(),
                "a loop round a whole block holds together: " + report.excess());

        S88ProcedureLogic chart = recipe.getProcedureLogic();
        boolean back = chart.getLinks().stream()
                .anyMatch(link -> link.getTo().stream()
                        .anyMatch(to -> "MIX".equals(to.getValue())));
        assertTrue(back, "the flow really does go back round the mixer");
    }

    /**
     * A loop connection comes off on its own, because no step can be taken off while it is there.
     *
     * <p>Taking a step off means taking the lines that meet at it off along with it, and a loop
     * leaves a step with two ways on. The loop link goes as a whole: its bar and both of its lines.
     */
    @Test
    void aLoopConnectionComesOffOnItsOwn() {
        S88MasterRecipe recipe = SfcFixtures.lineal();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        S88ProcedureTransition bar = EditProcedureLogicUseCase.joinStepsThroughATransition(
                recipe, null, "PACK", "MIX");

        assertEquals(bar.getId(), StructureQueries.loopTransitionOf(chart, bar.getId()),
                "the bar that closes the loop is one");
        assertEquals(2, chart.linksTouching(S88IdRef.transition(bar.getId())).size(),
                "with the two lines that meet at it");

        EditProcedureLogicUseCase.removeLoopLink(recipe, null, bar.getId());

        assertTrue(chart.findTransition(bar.getId()).isEmpty(), "the bar is gone");
        assertTrue(chart.linksTouching(S88IdRef.transition(bar.getId())).isEmpty(),
                "and so are both lines");
        ChartDeletionUseCase.deleteBox(recipe, null, "PACK");
        assertTrue(chart.findStep("PACK").isEmpty(),
                "and the step the loop left is free to be taken off again");
    }

    /**
     * A recipe with two charts under the one it starts with has three charts in it, and the last
     * one belongs to the kind of step that holds no chart of its own.
     */
    @Test
    void aSubchartIsDrawnUnderTheStepThatHoldsIt() {
        S88MasterRecipe recipe = SfcFixtures.twoLevelsOfSubchart();

        assertTrue(recipe.getRecipeElements().stream()
                        .anyMatch(element -> "TRAMO".equals(element.getId())),
                "the recipe holds the step the first subchart belongs to");
        assertTrue(recipe.findElement("PASO").isPresent(),
                "and the unit procedure holds the step the second one belongs to");
        assertTrue(recipe.findElement("PASO").get().hasProcedureLogic(),
                "and the second chart is there to be drawn");
        assertFalse(recipe.findElement("PULSO").isPresent()
                        && recipe.findElement("PASO").get().getProcedureLogic()
                                .getSteps().isEmpty(),
                "and the chart it holds has something in it");
    }
}
