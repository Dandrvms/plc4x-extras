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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout matrix: every fixture and every rule that makes a drawing readable.
 * <p>
 * The checks work on the arithmetic alone. No window is opened and nothing is painted.
 */
class SfcLayoutMatrixTest {

    private static Map<String, Rectangle> allBoxes(SfcLayoutEngine.Placement at) {
        Map<String, Rectangle> boxes = new LinkedHashMap<>(at.steps());
        boxes.putAll(at.bars());
        return boxes;
    }

    @Test
    void everyNodeHasAPosition() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () -> {
            S88ProcedureLogic chart = recipe.getProcedureLogic();
            SfcLayoutEngine.Placement at = SfcFixtures.arranged(recipe);
            for (var step : chart.getSteps()) {
                assertTrue(at.steps().containsKey(step.getId()),
                        recipe.getId() + ": step '" + step.getId() + "' has no position");
            }
            for (var transition : chart.getTransitions()) {
                assertTrue(at.bars().containsKey(transition.getId()),
                        recipe.getId() + ": transition '" + transition.getId() + "' has no position");
            }
        }));
    }

    @Test
    void boxesDoNotOverlap() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () -> {
            List<Map.Entry<String, Rectangle>> boxes =
                    new ArrayList<>(allBoxes(SfcFixtures.arranged(recipe)).entrySet());
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    Rectangle one = boxes.get(i).getValue();
                    Rectangle other = boxes.get(j).getValue();
                    assertFalse(one.intersects(other),
                            recipe.getId() + ": '" + boxes.get(i).getKey() + "' at " + one
                                    + " overlaps '" + boxes.get(j).getKey() + "' at " + other);
                }
            }
        }));
    }

    @Test
    void everySegmentIsOrthogonal() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () -> {
            for (SfcLayoutEngine.Link link : SfcFixtures.arranged(recipe).links()) {
                List<Point> path = link.path();
                for (int i = 1; i < path.size(); i++) {
                    Point before = path.get(i - 1);
                    Point here = path.get(i);
                    assertTrue(before.x == here.x || before.y == here.y,
                            recipe.getId() + ": " + link.id() + " has a diagonal from " + before
                                    + " to " + here + " in " + path);
                }
            }
        }));
    }

    @Test
    void noSegmentCrossesAnotherBox() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () -> {
            SfcLayoutEngine.Placement at = SfcFixtures.arranged(recipe);
            Map<String, Rectangle> boxes = allBoxes(at);
            for (SfcLayoutEngine.Link link : at.links()) {
                List<String> ends = List.of(link.id().split(">"));
                String from = ends.get(ends.size() - 2);
                String to = ends.get(ends.size() - 1);
                for (Map.Entry<String, Rectangle> box : boxes.entrySet()) {
                    if (box.getKey().equals(from) || box.getKey().equals(to)) {
                        continue;
                    }
                    for (int i = 1; i < link.path().size(); i++) {
                        assertFalse(segmentCrosses(link.path().get(i - 1), link.path().get(i),
                                        box.getValue()),
                                recipe.getId() + ": " + link.id() + " crosses '" + box.getKey()
                                        + "' at " + box.getValue() + " in " + link.path());
                    }
                }
            }
        }));
    }

    @Test
    void linesLeaveBelowAndArriveAbove() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () -> {
            SfcLayoutEngine.Placement at = SfcFixtures.arranged(recipe);
            Map<String, Rectangle> boxes = allBoxes(at);
            for (SfcLayoutEngine.Link link : at.links()) {
                if (link.backwards()) {
                    continue;
                }
                List<String> ends = List.of(link.id().split(">"));
                String from = ends.get(ends.size() - 2);
                String to = ends.get(ends.size() - 1);
                Point first = link.path().getFirst();
                Point last = link.path().getLast();
                Rectangle source = boxes.get(from);
                Rectangle target = boxes.get(to);
                assertTrue(source != null && target != null,
                        recipe.getId() + ": " + link.id() + " names an end without a box");
                assertEquals(leavingY(from, source, at), first.y,
                        recipe.getId() + ": " + link.id() + " leaves '" + from
                                + "' at " + first + " instead of below it");
                assertEquals(arrivingY(to, target, at), last.y,
                        recipe.getId() + ": " + link.id() + " arrives at '" + to
                                + "' at " + last + " instead of above it");
            }
        }));
    }

    @Test
    void layoutIsDeterministic() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                assertEquals(SfcFixtures.arranged(recipe), SfcFixtures.arranged(recipe),
                        recipe.getId() + " is drawn differently on a second run")));
    }

    private static int leavingY(String node, Rectangle box, SfcLayoutEngine.Placement at) {
        return at.bars().containsKey(node)
                ? box.y + SfcMetrics.TRANSITION_HEIGHT / 2
                : box.y + box.height;
    }

    private static int arrivingY(String node, Rectangle box, SfcLayoutEngine.Placement at) {
        return at.bars().containsKey(node)
                ? box.y + SfcMetrics.TRANSITION_HEIGHT / 2
                : box.y;
    }

    private static boolean segmentCrosses(Point from, Point to, Rectangle box) {
        if (box.contains(from) || box.contains(to)) {
            return false;
        }
        return box.intersectsLine(from.x + 0.5, from.y + 0.5, to.x + 0.5, to.y + 0.5);
    }
}
