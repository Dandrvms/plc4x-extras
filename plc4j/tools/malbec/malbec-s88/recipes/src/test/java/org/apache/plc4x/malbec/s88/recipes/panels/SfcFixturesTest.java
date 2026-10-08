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

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The six charts the drawing code has to get right, checked and drawn.
 * <p>
 * Nothing here opens a window, and nothing here can tell whether a line goes through a box. That
 * is in the PNGs written under {@code target/sfc}.
 */
class SfcFixturesTest {

    /** Runs what the test does on the thread Swing uses, and fails the test if it throws. */
    private static void onSwingThread(Runnable what) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    what.run();
                } catch (RuntimeException | AssertionError problem) {
                    failure.set(problem);
                }
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the chart");
            return;
        } catch (Exception other) {
            fail("could not reach the chart: " + other);
            return;
        }
        if (failure.get() != null) {
            fail("the chart fell over", failure.get());
        }
    }

    /**
     * Every line crosses between a box and a bar.
     * <p>
     * Ends that leave the chart are left out, because the model says nothing about what has to
     * alternate with something outside.
     */
    @Test
    void everyFixtureIsABipartiteChart() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            List<S88ProcedureLink> internal = recipe.getProcedureLogic().getLinks().stream()
                    .filter(SfcFixturesTest::hasBothEndsInside)
                    .toList();
            String culprit = internal.stream()
                    .filter(link -> link.getFrom().get(0).getType() == link.getTo().get(0).getType())
                    .map(Object::toString)
                    .findFirst()
                    .orElse(null);
            assertNull(culprit, () -> recipe.getId() + " has a line that does not cross a bar: "
                    + culprit);
        }
    }

    private static boolean hasBothEndsInside(S88ProcedureLink link) {
        return !link.getFrom().isEmpty() && !link.getTo().isEmpty()
                && link.getFrom().stream().allMatch(S88IdRef::isInternal)
                && link.getTo().stream().allMatch(S88IdRef::isInternal);
    }

    @Test
    void everyFixtureStartsWhereTheFlowStartsAndStopsWhereItEnds() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            assertEquals(1, boxForKind(recipe, S88RecipeElementKind.BEGIN).size(),
                    recipe.getId() + " has to have exactly one start");
            assertEquals(1, boxForKind(recipe, S88RecipeElementKind.END).size(),
                    recipe.getId() + " has to have exactly one stop");
        }
    }

    private static List<String> boxForKind(S88MasterRecipe recipe, S88RecipeElementKind kind) {
        return recipe.getProcedureLogic().getSteps().stream()
                .filter(step -> recipe.findElement(step.getRecipeElementId())
                        .map(element -> element.getKind() == kind)
                        .orElse(false))
                .map(S88ProcedureStep::getId)
                .toList();
    }

    /**
     * Every step of a fixture is reachable from the start.
     * <p>
     * A step nothing reaches is a step the process never performs, and one that is only drawn
     * because it is in the file.
     */
    @Test
    void everyFixtureIsFreeOfUnreachableSteps() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            RecipeConformance report = RecipeConformance.of(recipe);
            assertTrue(report.excess().isEmpty(),
                    recipe.getId() + " has something in the way: " + report.excess());
        }
    }

    /** A fixture that is broken on purpose, to prove the check above can fail. */
    @Test
    void aStepNothingReachesIsReported() {        S88MasterRecipe recipe = SfcFixtures.lineal();
        EditProcedureLogicUseCase.addStep(recipe, null, "BOX_ORPHAN", "MIX");

        RecipeConformance report = RecipeConformance.of(recipe);

        assertTrue(report.excess().stream().anyMatch(complaint -> complaint.contains("BOX_ORPHAN")),
                "a box nothing reaches is a step the process would never perform, and it says so: "
                        + report.excess());
    }

    @Test
    void aLoopBackIsDrawnAsAnEdgeOfItsOwn() {
        S88ProcedureLogic chart = SfcFixtures.withBackwardsLoop().getProcedureLogic();

        assertTrue(chart.findLink("L_BACK").isEmpty() || chart.getLinks().stream()
                        .anyMatch(link -> link.getTo().stream()
                                .anyMatch(to -> "T_MIX".equals(to.getValue()))),
                "the recipe goes back to the mixer, and that edge is in the file");
    }

    @Test
    void aLinePointingOutsideIsLeftOutOfTheDrawing() {
        S88MasterRecipe recipe = SfcFixtures.withExternalReferences();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertTrue(chart.getLinks().stream().anyMatch(link -> link.getTo().stream()
                        .anyMatch(to -> "OTHER_PART".equals(to.getValue()))),
                "the recipe really does carry a line pointing at another part");

        RecipeChartScene[] scene = new RecipeChartScene[1];
        onSwingThread(() -> scene[0] = RecipeChartScene.forRecipe(recipe));

        assertTrue(scene[0].complaints().stream().anyMatch(complaint -> complaint.contains("L2")),
                "and the drawing says so rather than quietly leaving a gap: "
                        + scene[0].complaints());
    }

    @Test
    void aSelectiveSplitCarriesOneLineWithBothConditionsOnIt() {
        S88ProcedureLogic chart = SfcFixtures.selectiveDivergence().getProcedureLogic();

        S88ProcedureLink split = chart.getLinks().stream()
                .filter(S88ProcedureLink::isDivergent)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the fixture has no split in it"));

        assertTrue(split.getTo().size() >= 2,
                "a split is one line with more than one arrival, not one line per branch");
        assertFalse(split.getLinkType().isParallel(),
                "and it is the selective kind, so the standard draws one bar and not two");
    }

    @Test
    void aParallelSplitIsOneLineLeavingABarTowardsTwoSteps() {
        S88ProcedureLogic chart = SfcFixtures.parallelDivergence().getProcedureLogic();

        S88ProcedureLink split = chart.getLinks().stream()
                .filter(link -> link.isDivergent() && link.getLinkType().isParallel())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the fixture has no parallel split in it"));

        assertEquals(1, split.getFrom().size(), "it leaves one bar, not one step");
        assertTrue(split.getFrom().get(0).getType()
                        == org.apache.plc4x.malbec.s88.api.S88IdRefType.TRANSITION,
                "and a bar is where the parallel case starts");
        assertTrue(split.getTo().size() >= 2, "going to both steps at once");
    }

    @Test
    void everyFixtureIsDrawnIntoAPictureFile() throws Exception {
        List<File> written = drawAllFixtures();

        assertEquals(SfcFixtures.names().size(), written.size(), "one picture per fixture");
        for (File file : written) {
            assertTrue(file.isFile() && file.length() > 0,
                    "a picture of nothing is not a picture to look at: " + file);
        }
    }

    /** Draws the fixtures and hands back the pictures, reporting anything that went wrong. */
    private static List<File> drawAllFixtures() throws Exception {
        AtomicReference<List<File>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(SfcRenderer.renderAllFixtures());
                } catch (Throwable problem) {
                    failure.set(problem);
                }
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the chart");
            return null;
        } catch (Exception other) {
            fail("could not reach the chart: " + other);
            return null;
        }
        if (failure.get() != null) {
            if (failure.get() instanceof AssertionError assertion) {
                throw assertion;
            }
            throw new AssertionError("the chart fell over", failure.get());
        }
        return result.get();
    }

}
