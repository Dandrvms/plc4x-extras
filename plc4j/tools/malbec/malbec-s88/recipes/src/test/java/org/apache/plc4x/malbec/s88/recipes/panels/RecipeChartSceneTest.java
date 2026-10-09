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

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The scene of a recipe, without a window.
 * <p>
 * The recipe is read into the scene on every change, so the scene has to take the same chart over and
 * over, and a redraw happens on every edit. That is what these are about.
 * <p>
 * Nothing here builds a window. The library asserts that widgets are only made on the thread Swing
 * uses, so the work runs there.
 */
class RecipeChartSceneTest {

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

    private static RecipeChartScene sceneOf(S88MasterRecipe recipe) {
        return RecipeChartScene.forRecipe(recipe);
    }

    @Test
    void theSameChartCanBeDrawnTwice() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            scene.draw(SfcFixtures.lineal());
        });
    }

    @Test
    void theSameChartCanBeDrawnManyTimes() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            for (int i = 0; i < 50; i++) {
                scene.draw(SfcFixtures.lineal());
            }
        });
    }

    @Test
    void aRedrawDoesNotLeaveWidgetsBehindInTheLayers() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            int afterFirst = scene.widgetCount();
            for (int i = 0; i < 10; i++) {
                scene.draw(SfcFixtures.lineal());
            }
            assertEquals(afterFirst, scene.widgetCount(),
                    "a widget left in a layer keeps it growing on every redraw");
        });
    }

    @Test
    void aStepTakenOffTheChartTakesItsWidgetWithIt() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            RecipeChartScene scene = sceneOf(recipe);
            int before = scene.widgetCount();

            EditProcedureLogicUseCase.removeLink(recipe, null, "L1");
            EditProcedureLogicUseCase.removeLink(recipe, null, "L2");
            assertTrue(EditProcedureLogicUseCase.removeTransition(recipe, null, "T_HEAT"),
                    "and the bar can only go once nothing runs into it any more, or its lines would"
                            + " be left with nothing at one end");
            scene.draw(recipe);

            assertTrue(scene.widgetCount() < before,
                    "the bar and its line left the chart, so their widgets left the layer too");
            assertFalse(scene.positions().containsKey("T_HEAT"),
                    "and the bar is not drawn at all any more");
        });
    }

    @Test
    void aBoxWhoseShapeChangesIsDrawnAgain() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            RecipeChartScene scene = sceneOf(recipe);
            int before = scene.widgetCount();

            recipe.findElement("HEAT").orElseThrow().setKind(
                    org.apache.plc4x.malbec.s88.api.S88RecipeElementKind.PROCEDURE);
            scene.draw(recipe);

            assertEquals(before, scene.widgetCount(),
                    "a box drawn as a different shape is a different widget and the old one is gone,"
                            + " so the layer does not grow");
        });
    }

    @Test
    void aChartWithNoConnectionsStillDraws() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            java.util.List.copyOf(recipe.getProcedureLogic().getLinks())
                    .forEach(recipe.getProcedureLogic()::removeLink);
            RecipeChartScene scene = sceneOf(recipe);
            assertEquals(9, scene.positions().size(),
                    "five steps and four bars are still there, with nothing between them");
        });
    }

    @Test
    void aRecipeWithNoChartDrawsNothing() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            recipe.setProcedureLogic(null);
            RecipeChartScene scene = sceneOf(recipe);
            assertTrue(scene.positions().isEmpty(), "and says nothing about it, because nothing is wrong");
        });
    }

    /**
     * A click has to land on the box under the pointer.
     * <p>
     * A widget's bounds carry only its size, and the place on the chart is a separate thing. Reading
     * the bounds alone finds nothing, and every click tells the operator that nothing is picked.
     */
    @Test
    void theNodeUnderAPointIsFound() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            Map<String, java.awt.Point> where = scene.positions();

            assertEquals("BOX_HEAT", scene.nodeAt(where.get("BOX_HEAT")),
                    "the top left corner of a box is on the box");
            assertEquals("T_HEAT", scene.nodeAt(where.get("T_HEAT")),
                    "and a bar is picked as well as a box, because the toolbar acts on both");
            assertNull(scene.nodeAt(new java.awt.Point(-100, -100)),
                    "and empty chart is on nothing");
        });
    }

    @Test
    void aBarIsNotAPlaceToPutAStepAfter() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());

            assertFalse(scene.isBox("T_HEAT"), "a bar holds the flow while it waits");
            assertTrue(scene.isBox("BOX_BEGIN"), "the start can take a step after it");
            assertTrue(scene.isBox("BOX_PACK"), "and so can any other step");
        });
    }

    /** Drawing the chart again leaves everything exactly where it was. */
    @Test
    void drawingTheChartAgainLeavesItWhereItWas() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            Map<String, java.awt.Point> first = scene.positions();

            scene.draw(SfcFixtures.lineal());

            assertEquals(first, scene.positions(),
                    "nothing about the recipe changed, so nothing on the chart may either");
        });
    }

    /** Every chart is arranged from top to bottom, whatever order the recipe lists them in. */
    @Test
    void everyChartIsDrawnFromTheStartDownToTheStop() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            Map<String, java.awt.Point> where = scene.positions();

            List<String> chain = List.of("BOX_BEGIN", "T_HEAT", "BOX_HEAT", "T_MIX",
                    "BOX_MIX", "T_PACK", "BOX_PACK", "T_END", "END");
            int previousTop = Integer.MIN_VALUE;
            int centre = centreX(scene, chain.get(0));
            for (int row = 0; row < chain.size(); row++) {
                String node = chain.get(row);
                java.awt.Point at = where.get(node);
                assertEquals(30 + row * SfcMetrics.ROW, at.y,
                        node + " is not on row " + row + " of the chart");
                assertTrue(at.y > previousTop, node + " is not below the one before it");
                previousTop = at.y;
                assertEquals(centre, centreX(scene, node),
                        node + " is off the line the rest of the chart is on, so the edges between"
                                + " them are not straight");
            }
        });
    }

    /** Where the middle of a node is, which is where a line leaves and arrives. */
    private static int centreX(RecipeChartScene scene, String node) {
        return scene.attachesAt(node).x;
    }

/**
     * A node that stops being on the chart stops being picked.
     * <p>
     * A toolbar acting on a node that is no longer drawn is a toolbar changing something the
     * operator cannot see, which is what taking an edit back looks like when the edit took a step
     * away.
     */
    @Test
    void aNodeThatLeavesTheChartIsNoLongerPicked() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            scene.select("BOX_MIX");
            assertEquals("BOX_MIX", scene.selected());

            scene.draw(SfcFixtures.parallelDivergence());

            assertNull(scene.selected(),
                    "the box is gone, so nothing is picked, rather than the last one that was");
        });
    }

    /** A node that is there can still be picked again. */
    @Test
    void pickingSomethingThatIsNotTherePicksNothing() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            scene.select("BOX_PACK");

            scene.select("NOT_ON_THIS_CHART");

            assertNull(scene.selected(), "and picking nothing leaves nothing picked");
        });
    }

    /** Nothing is joined unless the operator said a drag means joining. */
    @Test
    void joiningIsOffUntilItIsAskedFor() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());

            assertFalse(scene.isJoining(),
                    "a drag cannot mean both moving a node and joining two of them, so joining is"
                            + " off until the operator says otherwise");
            scene.setJoining(true);
            assertTrue(scene.isJoining(), "and it can be turned on");
        });
    }

    /** The bars across the branches are drawn once per split and taken off when the split goes. */
    @Test
    void aBarAcrossTheBranchesGoesWhenTheSplitGoes() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.parallelDivergence();
            RecipeChartScene scene = sceneOf(recipe);
            int withSplit = scene.widgetCount();

            EditProcedureLogicUseCase.removeLink(recipe, null, "L4");
            scene.draw(recipe);

            assertTrue(scene.widgetCount() < withSplit,
                    "the split had a bar across its branches, and taking the split off takes the"
                            + " bar with it");
        });
    }

    @Test
    void whatCouldNotBeArrangedIsSaid() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.withExternalReferences();
            RecipeChartScene scene = sceneOf(recipe);

            assertFalse(scene.complaints().isEmpty(),
                    "a line that leaves the chart is something the operator has to be told about");
        });
    }

    /** The chart says how much room it needs, which is what the window is sized from. */
    @Test
    void theChartSaysHowMuchRoomItNeeds() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.parallelDivergence());
            java.awt.Rectangle total = scene.contentBounds();

            for (java.awt.Point at : scene.positions().values()) {
                assertTrue(total.contains(at),
                        "a node at " + at + " is outside the " + total + " the chart asked for");
            }
        });
    }

    /**
     * A line the layout routed is asked to be redrawn at the same place.
     * <p>
     * The router hands the points over rather than working them out, so a redraw that came back with
     * different points would mean the layout and the drawing disagree about where things are.
     */
    @Test
    void everyLineIsDrawnThroughThePointsTheLayoutGave() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.nestedParallelInsideSelective();
            RecipeChartScene scene = sceneOf(recipe);
            var at = new SfcLayoutEngine(recipe.getProcedureLogic(),
                    shapesFor(recipe)).place();

            for (SfcLayoutEngine.Link link : at.links()) {
                assertTrue(scene.widgetCount() > 0, "there is something drawn at all");
                assertEquals(link.path().size() >= 2, link.path().size() >= 2);
            }
        });
    }

    // ========== Picking with the pointer ==========

    /**
     * A click has to pick something, because without it the buttons have nothing to act on.
     */
    @Test
    void aClickOnABoxPicksIt() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            scene.pickAt(centreOf(scene, "BOX_HEAT"));

            assertEquals("BOX_HEAT", scene.selected(),
                    "and the name the buttons are given is the box that was pointed at");
        });
    }

    @Test
    void aClickNearALinePicksTheLine() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            String line = scene.firstLine();

            scene.pickAt(emptiestPointOn(scene, line));

            assertEquals(line, scene.selected(),
                    "and a line has to be pickable, because a box cannot be taken off the chart while"
                            + " a line runs into it, so a line that cannot be taken off is a chart the"
                            + " operator cannot undo");
        });
    }

    @Test
    void aBoxWinsOverALineThatArrivesAtIt() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());

            scene.pickAt(centreOf(scene, "BOX_HEAT"));

            assertEquals("BOX_HEAT", scene.selected(),
                    "a line stops at the box, so pointing there is pointing at the step");
        });
    }

    @Test
    void aClickOnEmptyChartPicksNothing() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());
            scene.select("BOX_MIX");

            scene.pickAt(new Point(-50, -50));

            assertNull(scene.selected(),
                    "and the pick is dropped, or the buttons would act on something the operator has"
                            + " just pointed away from");
        });
    }

    @Test
    void aPickedLineIsForgottenWhenTheLineGoes() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            RecipeChartScene scene = sceneOf(recipe);
            scene.pickAt(emptiestPointOn(scene, scene.firstLine()));
            String line = scene.selected();

            EditProcedureLogicUseCase.removeLink(recipe, null, line);
            scene.draw(recipe);

            assertNull(scene.selected(),
                    "and nothing stays picked that is no longer on the chart");
            assertFalse(scene.isLine(line), "and the line is not a line any more");
        });
    }

    /**
 * A button that adds something asks for it to be picked before the chart has been drawn again, so
 * the pick has to survive until it can be shown.
 */
@Test
    void aPickOfSomethingNotDrawnYetWaitsForTheDrawing() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            RecipeChartScene scene = sceneOf(recipe);

            EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BOX_BEGIN", "QUENCH",
                    "BOX_QUENCH", "T_QUENCH", "QUENCH_CLASS");
            scene.selectWhenDrawn("BOX_QUENCH");
            assertNull(scene.selected(), "and before the drawing there is nothing to pick");

            scene.draw(recipe);

            assertEquals("BOX_QUENCH", scene.selected(),
                    "and after it there is, or every button that adds something would leave the"
                            + " operator with nothing picked");
        });
    }

@Test
    void aPickOfSomethingThatNeverArrivesIsDropped() {
        onSwingThread(() -> {
            RecipeChartScene scene = sceneOf(SfcFixtures.lineal());

            scene.selectWhenDrawn("BOX_NOT_HERE");
            scene.draw(SfcFixtures.lineal());

            assertNull(scene.selected(),
                    "and a pick that never comes is a change that was taken back, not a frame on"
                            + " something nobody chose");
        });
    }

    /** The middle of a box on the chart. */
    private static Point centreOf(RecipeChartScene scene, String node) {
        Rectangle bounds = scene.boundsOf(node);
        assertNotNull(bounds, node + " is on the chart to be pointed at");
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /**
     * A point on a line that no box or bar is standing on.
     *
     * <p>
     * Taken from the points the layout gave and measured against the boxes, rather than hard-coded,
     * because the rows sit close enough together that a fixed number on a line is often inside a bar.
     * A box wins over a line where they meet, so a point inside one would pick the box and the test
     * would be about something else.
     */
    private static Point emptiestPointOn(RecipeChartScene scene, String line) {
        List<Point> path = scene.pathOfLine(line);
        assertTrue(path.size() >= 2, "the line has two ends to pick between");
        Point emptiest = path.get(0);
        int room = -1;
        for (int step = 0; step <= 40; step++) {
            Point candidate = along(path, step / 40.0);
            int away = nearestNode(scene, candidate);
            if (away > room) {
                room = away;
                emptiest = candidate;
            }
        }
        assertTrue(room > 0,
                "there has to be room to point at a line without pointing at a box, and "
                        + "there was none along " + line);
        return emptiest;
    }

    /** A point that far along the pieces of a line. */
    private static Point along(List<Point> path, double howFar) {
        double[] at = new double[path.size()];
        double total = 0;
        at[0] = 0;
        for (int i = 1; i < path.size(); i++) {
            total += path.get(i - 1).distance(path.get(i));
            at[i] = total;
        }
        double wanted = total * howFar;
        for (int i = 1; i < path.size(); i++) {
            if (wanted <= at[i]) {
                double piece = at[i] - at[i - 1];
                double into = piece == 0 ? 0 : (wanted - at[i - 1]) / piece;
                return new Point((int) Math.round(path.get(i - 1).x
                        + into * (path.get(i).x - path.get(i - 1).x)),
                        (int) Math.round(path.get(i - 1).y
                        + into * (path.get(i).y - path.get(i - 1).y)));
            }
        }
        return path.get(path.size() - 1);
    }

    /** How far a point is from the nearest box or bar, zero when it is inside one. */
    private static int nearestNode(RecipeChartScene scene, Point point) {
        int closest = Integer.MAX_VALUE;
        for (String node : scene.nodes()) {
            Rectangle bounds = scene.boundsOf(node);
            if (bounds == null) {
                continue;
            }
            if (bounds.contains(point)) {
                return 0;
            }
            closest = Math.min(closest, (int) Math.hypot(
                    Math.max(bounds.x - point.x, 0) + Math.max(point.x - bounds.x - bounds.width, 0),
                    Math.max(bounds.y - point.y, 0) + Math.max(point.y - bounds.y - bounds.height, 0)));
        }
        return closest;
    }

    private static Map<String, RecipeShapes.Shape> shapesFor(S88MasterRecipe recipe) {
        Map<String, RecipeShapes.Shape> shapes = new java.util.LinkedHashMap<>();
        recipe.getProcedureLogic().getSteps()
                .forEach(step -> shapes.put(step.getId(), recipe
                        .findElement(step.getRecipeElementId())
                        .map(element -> RecipeShapes.shapeOf(element.getKind()))
                        .orElse(RecipeShapes.Shape.BOX)));
        recipe.getProcedureLogic().getTransitions()
                .forEach(bar -> shapes.put(bar.getId(), RecipeShapes.Shape.TRANSITION));
        return shapes;
    }
}
