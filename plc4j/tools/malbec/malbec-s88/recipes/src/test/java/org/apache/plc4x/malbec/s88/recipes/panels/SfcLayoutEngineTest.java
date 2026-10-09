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

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the layout puts everything, checked on every chart that has to look right.
 * <p>
 * No window is opened and nothing is drawn: these are the things that are true about the arithmetic,
 * which is where a chart goes wrong long before anyone looks at it.
 */
class SfcLayoutEngineTest {

    private static SfcLayoutEngine.Placement arrange(S88MasterRecipe recipe) {
        return new SfcLayoutEngine(recipe.getProcedureLogic(), shapesOf(recipe),
                labelWidthsOf(recipe)).place();
    }

    /** The engine is told how wide each text is, the same way the drawing tells it. */
    private static Map<String, Integer> labelWidthsOf(S88MasterRecipe recipe) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            Map<String, Integer> widths = new java.util.LinkedHashMap<>();
            recipe.getProcedureLogic().getTransitions().forEach(bar -> widths.put(
                    bar.getId(), RecipeShapes.textWidth(g, bar.getCondition())));
            return widths;
        } finally {
            g.dispose();
        }
    }

    private static Map<String, RecipeShapes.Shape> shapesOf(S88MasterRecipe recipe) {
        Map<String, RecipeShapes.Shape> shapes = new HashMap<>();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        for (var step : chart.getSteps()) {
            shapes.put(step.getId(), recipe.findElement(step.getRecipeElementId())
                    .map(element -> RecipeShapes.shapeOf(element.getKind()))
                    .orElse(RecipeShapes.Shape.BOX));
        }
        for (var bar : chart.getTransitions()) {
            shapes.put(bar.getId(), RecipeShapes.Shape.TRANSITION);
        }
        return shapes;
    }

    private static List<Rectangle> everything(SfcLayoutEngine.Placement at) {
        List<Rectangle> all = new ArrayList<>(at.steps().values());
        all.addAll(at.bars().values());
        return all;
    }

    @Test
    void everyChartFitsInsideTheSizeItSaysItDoes() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            SfcLayoutEngine.Placement at = arrange(recipe);
            for (Map.Entry<String, Rectangle> node : at.steps().entrySet()) {
                assertTrue(at.total().contains(node.getValue()),
                        recipe.getId() + ": step '" + node.getKey() + "' is at " + node.getValue()
                                + ", which is outside " + at.total());
            }
            for (Map.Entry<String, Rectangle> node : at.bars().entrySet()) {
                assertTrue(at.total().contains(node.getValue()),
                        recipe.getId() + ": transition '" + node.getKey() + "' is at "
                                + node.getValue() + ", which is outside " + at.total());
            }
        }
    }

    @Test
    void nothingOnAChartIsDrawnOnTopOfAnythingElse() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            SfcLayoutEngine.Placement at = arrange(recipe);
            List<Rectangle> all = everything(at);
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    Rectangle one = grown(all.get(i));
                    Rectangle two = grown(all.get(j));
                    assertFalse(one.intersects(two),
                            recipe.getId() + ": " + all.get(i) + " and " + all.get(j)
                                    + " are drawn over each other");
                }
            }
        }
    }

    /** Room for the shadow a shape casts, so that two things next to each other count as touching. */
    private static Rectangle grown(Rectangle box) {
        Rectangle room = new Rectangle(box);
        room.grow(SfcMetrics.GAP / 2, 4);
        return room;
    }

    @Test
    void everyLineRunsDownwardsOrIsSaidToGoBackUp() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            SfcLayoutEngine.Placement at = arrange(recipe);
            for (SfcLayoutEngine.Link link : at.links()) {
                if (link.backwards()) {
                    continue;
                }
                assertTrue(link.path().get(link.path().size() - 1).y
                                > link.path().get(0).y,
                        recipe.getId() + ": " + link.id() + " claims to go down and does not");
            }
        }
    }

    @Test
    void aLineDoesNotGoThroughAThingItIsNotJoinedTo() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            SfcLayoutEngine.Placement at = arrange(recipe);
            Map<String, Rectangle> nodes = new HashMap<>(at.steps());
            nodes.putAll(at.bars());
            for (SfcLayoutEngine.Link link : at.links()) {
                List<String> ends = List.of(link.id().split(">"));
                String from = ends.get(ends.size() - 2);
                String to = ends.get(ends.size() - 1);
                for (Map.Entry<String, Rectangle> node : nodes.entrySet()) {
                    if (node.getKey().equals(from) || node.getKey().equals(to)) {
                        continue;
                    }
                    for (int i = 1; i < link.path().size(); i++) {
                        assertFalse(
                                segmentCrosses(link.path().get(i - 1), link.path().get(i),
                                        node.getValue()),
                                recipe.getId() + ": " + link.id() + " goes through '"
                                        + node.getKey() + "' at " + node.getValue());
                    }
                }
            }
        }
    }

    private static boolean segmentCrosses(Point from, Point to, Rectangle box) {
        if (box.contains(from) || box.contains(to)) {
            return false;
        }
        return box.intersects(new Rectangle(
                Math.min(from.x, to.x), Math.min(from.y, to.y),
                Math.max(1, Math.abs(to.x - from.x)), Math.max(1, Math.abs(to.y - from.y))));
    }

    @Test
    void aStepWithOneThingBeforeItIsOnItsLine() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.lineal());

        int beginCentre = centreX(at, "BOX_BEGIN");
        int heatCentre = centreX(at, "BOX_HEAT");
        int mixCentre = centreX(at, "BOX_MIX");

        assertEquals(beginCentre, heatCentre,
                "a step that only one thing leads to sits on that thing's line, so the edge between"
                        + " them is straight");
        assertEquals(beginCentre, mixCentre, "and so does the one after it");
    }

    private static int centreX(SfcLayoutEngine.Placement at, String node) {
        Rectangle box = at.steps().get(node);
        return box == null ? 0 : box.x + box.width / 2;
    }

    @Test
    void aBranchIsCentredOnWhereItLeft() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.parallelDivergence());

        int splitCentre = centreX(at, "BOX_CHARGE");
        int heatCentre = centreX(at, "BOX_HEAT");
        int coolCentre = centreX(at, "BOX_COOL");

        assertEquals(splitCentre, (heatCentre + coolCentre) / 2,
                "two steps leaving one bar sit either side of that bar's line, so the chart reads as"
                        + " one thing splitting rather than two things that happen to be close");
        assertTrue(heatCentre != coolCentre, "and they are actually either side of it");
    }

    @Test
    void aSplitGetsABarAndAnOrdinaryEdgeDoesNot() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.parallelDivergence());
        assertFalse(at.syncBars().isEmpty(),
                "a line with more than one end is a split or a join, and the bar is what says so");

        SfcLayoutEngine.Placement straight = arrange(SfcFixtures.lineal());
        assertTrue(straight.syncBars().isEmpty(),
                "a line with one end at each side is an ordinary edge and gets no bar");
    }

    @Test
    void theBarAcrossTheBranchesSpansThem() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.parallelDivergence());

        Rectangle bar = at.syncBars().values().iterator().next().where();
        Rectangle heat = at.steps().get("BOX_HEAT");
        Rectangle cool = at.steps().get("BOX_COOL");

        assertTrue(bar.x <= Math.min(heat.x + heat.width / 2, cool.x + cool.width / 2),
                "the bar starts before the leftmost branch, or the edge has nothing to join at");
        assertTrue(bar.x + bar.width >= Math.max(heat.x + heat.width / 2, cool.x + cool.width / 2),
                "and ends past the rightmost one, and the bar is " + bar + " for " + heat + " "
                        + cool);
    }

    @Test
    void aLinkThatGoesBackUpIsTakenOutToTheSideAndSaidSo() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.withBackwardsLoop());

        List<SfcLayoutEngine.Link> backwards = at.links().stream()
                .filter(SfcLayoutEngine.Link::backwards).toList();
        assertFalse(backwards.isEmpty(),
                "a recipe that loops round the mixer has a line that goes back up, and it is not"
                        + " among the " + at.links().size() + " lines");
        assertTrue(at.backwards() > 0, "and the chart is said to have one");

        SfcLayoutEngine.Link loop = backwards.get(0);
        int rightmost = 0;
        for (Rectangle box : everything(at)) {
            rightmost = Math.max(rightmost, box.x + box.width);
        }
        final int lane = rightmost;
        assertTrue(loop.path().stream().anyMatch(point -> point.x > lane),
                "the loop is taken out past everything else rather than through it, and its points"
                        + " are " + loop.path());
    }

    /** One bar for a split where one branch is taken, two for one where they all are. */
    @Test
    void aSelectiveBarIsOneLineAndAParallelBarIsTwo() {
        SfcLayoutEngine.Placement parallel = arrange(SfcFixtures.parallelDivergence());
        assertTrue(parallel.syncBars().values().stream().allMatch(
                        SfcLayoutEngine.SyncBar::doubled),
                "both branches run at once, so every bar across them is doubled: "
                        + parallel.syncBars());

        SfcLayoutEngine.Placement selective = arrange(SfcFixtures.selectiveDivergence());
        assertTrue(selective.syncBars().values().stream().noneMatch(
                        SfcLayoutEngine.SyncBar::doubled),
                "only one branch is taken, so no bar is doubled: " + selective.syncBars());
    }

    /** A bar is wide enough for the comparison written beside it, or the text is clipped. */
    @Test
    void aBarIsWideEnoughForItsOwnComparison() {
        S88MasterRecipe recipe = SfcFixtures.selectiveDivergence();
        String condition = recipe.getProcedureLogic().findTransition("T_HEAT_READY")
                .orElseThrow().getCondition();

        SfcLayoutEngine.Placement at = arrange(recipe);

        Rectangle bar = at.bars().get("T_HEAT_READY");
        assertTrue(bar.width >= SfcMetrics.TRANSITION_WIDTH + SfcMetrics.LABEL_GAP
                        + textWidthOf(condition),
                "the bar is " + bar + " and '" + condition + "' needs " + textWidthOf(condition)
                        + ", so the text would be cut off at the right");
    }

    private static int textWidthOf(String text) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            return RecipeShapes.textWidth(g, text);
        } finally {
            g.dispose();
        }
    }

    /** A bar with nothing to wait on is only as wide as it needs to be. */
    @Test
    void aBarThatWaitsOnNothingIsNotMadeWideForText() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.lineal());

        for (Rectangle bar : at.bars().values()) {
            assertEquals(SfcMetrics.transitionWidth(0), bar.width,
                    "a bar with no comparison beside it is just the bar, and its width is " + bar.width);
        }
    }

    /** A link through a bar is one straight run through it, not two that stop short. */
    @Test
    void aLinkPassesThroughTheBarItCrosses() {
        SfcLayoutEngine.Placement at = arrange(SfcFixtures.lineal());

        Rectangle bar = at.bars().get("T_HEAT");
        SfcLayoutEngine.Link arriving = at.links().stream()
                .filter(link -> link.id().endsWith(">BOX_BEGIN>T_HEAT"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the link into the bar is not there"));
        SfcLayoutEngine.Link leaving = at.links().stream()
                .filter(link -> link.id().endsWith(">T_HEAT>BOX_HEAT"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the link out of the bar is not there"));

        int middle = bar.y + SfcMetrics.TRANSITION_HEIGHT / 2;
        assertEquals(middle, arriving.path().get(1).y,
                "the line arrives at the middle of the bar, which is where it is drawn");
        assertEquals(middle, leaving.path().get(0).y,
                "and the line that goes on leaves from the same middle, so the two are one run");
    }

    /**
 * A bar with a comparison beside it is still centred on its line by the bar, not by the whole of it.
 * <p>
 * Centring the node as a whole puts the bar a whole text width to one side of the line, so the line
 * arrives at the text instead of at the bar, and two bars whose comparisons are long push each other
 * out of their branches.
 */
@Test
    void theBarIsOnItsLineAndTheTextGoesBesideIt() {
        S88MasterRecipe recipe = SfcFixtures.selectiveDivergence();
        SfcLayoutEngine.Placement at = arrange(recipe);

        Rectangle bar = at.bars().get("T_HEAT_READY");
        Rectangle child = at.steps().get("BOX_HEAT");

        assertEquals(bar.x + SfcMetrics.TRANSITION_WIDTH / 2, barCentreOfBarOnly(bar),
                "the bar itself is where the line runs");
        assertEquals(child.x + child.width / 2, barCentreOfBarOnly(bar),
                "and that is the line of the branch it belongs to, which is " + bar + " over "
                        + child);
    }

    private static int barCentreOfBarOnly(Rectangle bar) {
        return bar.x + SfcMetrics.TRANSITION_WIDTH / 2;
    }

    /** A bar wide enough for its comparison is not allowed to push its own branch sideways. */
    @Test
    void twoBarsWithLongComparisonsEachStayOverTheirOwnBranch() {
        S88MasterRecipe recipe = SfcFixtures.selectiveDivergence();
        SfcLayoutEngine.Placement at = arrange(recipe);

        Rectangle left = at.bars().get("T_HEAT_READY");
        Rectangle right = at.bars().get("T_COOL_READY");
        Rectangle leftBranch = at.steps().get("BOX_HEAT");
        Rectangle rightBranch = at.steps().get("BOX_COOL");

        assertEquals(leftBranch.x + leftBranch.width / 2, left.x + SfcMetrics.TRANSITION_WIDTH / 2,
                "the left bar is over the left branch, and neither text moved it");
        assertEquals(rightBranch.x + rightBranch.width / 2,
                right.x + SfcMetrics.TRANSITION_WIDTH / 2,
                "and the right bar is over the right branch");
        assertTrue(left.x + left.width <= right.x,
                "and the two of them do not sit on top of each other, which is what a long"
                        + " comparison on both would try to do: " + left + " and " + right);
    }

    @Test
    void aChartWithNothingToComplainAboutSaysSo() {
        for (String name : SfcFixtures.names()) {
            if ("referencias-externas".equals(name)) {
                continue;
            }
            S88MasterRecipe recipe = byName(name);
            assertEquals(List.of(), arrange(recipe).complaints(),
                    name + " is a chart that should hold together");
        }
    }

    /** A line that leaves the chart is said out loud rather than quietly not drawn. */
    @Test
    void aLineThatLeavesTheChartIsSaidAndTheRestIsStillDrawn() {
        S88MasterRecipe recipe = SfcFixtures.withExternalReferences();

        SfcLayoutEngine.Placement at = arrange(recipe);

        assertEquals(2, at.complaints().size(),
                "two lines of that chart leave it, and both are said: " + at.complaints());
        assertTrue(at.complaints().stream()
                        .anyMatch(complaint -> complaint.contains("L2")),
                "and the message names the line, because that is what has to be looked at: "
                        + at.complaints());
        assertFalse(at.links().stream().anyMatch(link -> link.id().contains("L2")),
                "the part of that line that is here has nothing to arrive at, so it is left out");
        assertFalse(at.links().isEmpty(), "and the rest of the chart is still drawn");
    }

    private static S88MasterRecipe byName(String name) {
        return SfcFixtures.all().get(SfcFixtures.names().indexOf(name));
    }

    @Test
    void aChartWithNoStartIsStillArrangedAndSaysWhy() {
        S88MasterRecipe recipe = SfcFixtures.lineal();
        Map<String, RecipeShapes.Shape> shapes = shapesOf(recipe);
        shapes.replaceAll((id, shape) -> shape == RecipeShapes.Shape.START
                ? RecipeShapes.Shape.BOX : shape);

        SfcLayoutEngine.Placement at = new SfcLayoutEngine(recipe.getProcedureLogic(), shapes)
                .place();

        assertFalse(at.complaints().isEmpty(),
                "a chart with no step of kind begin is a chart something is wrong with, and the"
                        + " drawing has to say what");
        assertFalse(at.steps().isEmpty(), "and it is still drawn");
    }

    @Test
    void aLineJoiningTwoStepsIsReportedAndStillDrawn() {
        S88MasterRecipe recipe = SfcFixtures.lineal();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        chart.addLink(new org.apache.plc4x.malbec.s88.api.S88ProcedureLink("L_BAD"));
        var bad = chart.findLink("L_BAD").orElseThrow();
        bad.addFrom(org.apache.plc4x.malbec.s88.api.S88IdRef.step("BOX_HEAT"));
        bad.addTo(org.apache.plc4x.malbec.s88.api.S88IdRef.step("BOX_MIX"));

        SfcLayoutEngine.Placement at = arrange(recipe);

        assertTrue(at.complaints().stream().anyMatch(complaint -> complaint.contains("L_BAD")),
                "a line straight from one step to another is a chart that cannot be read top to"
                        + " bottom, and it says which line: " + at.complaints());
    }
}
