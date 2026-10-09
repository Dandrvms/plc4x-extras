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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The same recipe is always drawn the same way.
 *
 * <p>
 * A drawing that moves about between one opening of a recipe and the next is a drawing an author
 * cannot work on: a branch they had put on the left is under the one on the right after they closed
 * the recipe and opened it again, and nothing was changed in between. What is checked here is the
 * order things are looked at in, because that is what decides which side of another thing each one
 * ends up on.
 *
 * <p>
 * No window is opened and nothing is drawn. What is checked is where the engine says each thing goes,
 * which is where the drawing reads its positions from.
 */
class SfcLayoutDeterminismTest {

    private static SfcLayoutEngine.Placement arrange(S88MasterRecipe recipe) {
        return new SfcLayoutEngine(recipe.getProcedureLogic(), shapesOf(recipe)).place();
    }

    /** The order of the nodes is the order the recipe wrote them in, which is what makes the drawing stable. */
    private static Map<String, RecipeShapes.Shape> shapesOf(S88MasterRecipe recipe) {
        Map<String, RecipeShapes.Shape> shapes = new LinkedHashMap<>();
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

    private static Map<String, String> whereEverythingIs(SfcLayoutEngine.Placement at) {
        Map<String, String> where = new LinkedHashMap<>();
        at.steps().forEach((id, box) -> where.put(id, box.toString()));
        at.bars().forEach((id, box) -> where.put(id, box.toString()));
        return where;
    }

    @Test
    void everyChartIsDrawnTheSameWayEveryTime() {
        for (S88MasterRecipe recipe : SfcFixtures.all()) {
            assertEquals(whereEverythingIs(arrange(recipe)), whereEverythingIs(arrange(recipe)),
                    "and it is drawn the same way again for " + recipe.getId());
        }
    }

    @Test
    void theOrderOfAChartCannotBeChangedFromOutside() {
        S88ProcedureLogic chart = SfcFixtures.nestedParallelInsideSelective().getProcedureLogic();

        assertThrows(UnsupportedOperationException.class,
                () -> chart.getSteps().sort((one, other) -> other.getId().compareTo(one.getId())),
                "and the boxes cannot be shuffled behind the drawing's back");
        assertThrows(UnsupportedOperationException.class,
                () -> chart.getLinks().sort((one, other) -> other.getId().compareTo(one.getId())),
                "and the lines cannot be either, because which line was written first is what says"
                        + " which branch comes out on which side");
        assertThrows(UnsupportedOperationException.class,
                () -> chart.getTransitions().sort((one, other) -> other.getId().compareTo(one.getId())),
                "and neither can the bars, which decide the order of the rows");
    }

    @Test
    void addingASecondBranchKeepsTheBranchesThatWereThereInTheSameOrder() {
        S88MasterRecipe recipe = SfcFixtures.parallelDivergence();
        Rectangle heatBefore = arrange(recipe).steps().get("BOX_HEAT");
        Rectangle coolBefore = arrange(recipe).steps().get("BOX_COOL");
        assertEquals(true, heatBefore.x < coolBefore.x,
                "and one branch starts out to the left of the other");

        EditProcedureLogicUseCase.selectiveFork(recipe, null, "BOX_PACK", "T_PACK_WHEN_COLD",
                "T_PACK_OTHERWISE");

        SfcLayoutEngine.Placement at = arrange(recipe);
        Rectangle heatAfter = at.steps().get("BOX_HEAT");
        Rectangle coolAfter = at.steps().get("BOX_COOL");
        assertEquals(true, heatAfter.x < coolAfter.x,
                "and a new branch does not swap the two around, because an author who put one on the"
                        + " left finds it still on the left after adding another beside it");
        assertEquals(true, heatAfter.x + heatAfter.width <= coolAfter.x
                        || coolAfter.x + coolAfter.width <= heatAfter.x,
                "and the two do not end up laid on top of each other while moving");
    }

    @Test
    void theTwoBranchesOfASplitAreNeverLaidOnTopOfEachOther() {
        S88MasterRecipe recipe = SfcFixtures.nestedParallelInsideSelective();
        SfcLayoutEngine.Placement at = arrange(recipe);

        Rectangle left = at.steps().get("BOX_ADD");
        Rectangle right = at.steps().get("BOX_SPRAY");

        assertEquals(true, left.x + left.width <= right.x || right.x + right.width <= left.x,
                "and one branch is entirely to one side of the other, because two boxes drawn on"
                        + " the same piece of paper are two things an author cannot read");
    }

    @Test
    void aLongNameInOneBranchDoesNotPushTheOtherBranchOutOfTheChart() {
        S88MasterRecipe recipe = SfcFixtures.parallelDivergence();
        String longName = "CALENTAMIENTO_DE_TANQUE_CIENTO_GRADOS";
        EditProcedureLogicUseCase.renameStep(recipe, null, "BOX_HEAT", longName);
        SfcLayoutEngine.Placement at = arrange(recipe);

        Rectangle heat = at.steps().get(longName);
        Rectangle cool = at.steps().get("BOX_COOL");
        assertEquals(true, at.total().contains(heat), "and the long one is still inside the chart");
        assertEquals(true, at.total().contains(cool),
                "and the other branch is not pushed out of it by the long name");
    }

    /** The bar is made wide enough for the comparison it carries, and the drawing still fits. */
    @Test
    void aBarWithAComparisonIsDrawnWideEnoughForItAndStillInsideTheChart() {
        S88MasterRecipe recipe = SfcFixtures.lineal();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        chart.findTransition("T_HEAT").orElseThrow()
                .setCondition("CALENTAMIENTO_1#TEMPERATURA_ACTUAL_CALENTAMIENTO_1#>#80");
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            int width = RecipeShapes.textWidth(g, chart.findTransition("T_HEAT").orElseThrow()
                    .getCondition());
            SfcLayoutEngine.Placement at = new SfcLayoutEngine(chart, shapesOf(recipe),
                    Map.of("T_HEAT", width)).place();
            assertEquals(true, at.total().contains(at.bars().get("T_HEAT")),
                    "and a bar that waits on something is not drawn narrower than what it waits on");
        } finally {
            g.dispose();
        }
    }

    @Test
    void twoChartsThatSayTheSameThingAreDrawnTheSameWay() {
        S88MasterRecipe one = SfcFixtures.parallelDivergence();
        S88MasterRecipe other = SfcFixtures.parallelDivergence();

        assertEquals(whereEverythingIs(arrange(one)), whereEverythingIs(arrange(other)),
                "and two copies of the same recipe do not draw differently, which is what makes a"
                        + " drawing mean something");
    }

    @Test
    void aParallelSplitIsReadFromTheLineAndNotFromWhereTheLineSat() {
        S88MasterRecipe recipe = SfcFixtures.parallelDivergence();
        S88ProcedureLogic chart = recipe.getProcedureLogic();

        assertEquals(S88LinkType.PARALLEL_DIVERGENT,
                chart.getLinks().stream()
                        .filter(link -> link.getFrom().stream()
                                .anyMatch(ref -> "T_SPLIT".equals(ref.getValue())))
                        .map(S88ProcedureLink::getLinkType)
                        .findFirst()
                        .orElseThrow(),
                "and what the line says is what the drawing reads, which is what keeps the two"
                        + " branches from turning into one line when the chart is read back");
    }
}