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

import java.awt.Rectangle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.apache.plc4x.malbec.s88.core.UpdateRecipeParameterUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A box on the chart is a step, and a step with no equipment yet is still a step.
 *
 * <p>
 * Checked here without opening a window: what the box is given to draw and how big it ends up is what
 * an author sees, and both can be wrong without anything else saying so.
 */
class StepBoxOnTheChartTest {

    /** A plant with one class of heaters, with a long name on the equipment behind it. */
    private static S88PlantSnapshot plant() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO_DE_TANQUE");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element tank = new S88Element();
        tank.setId("TANQUE_1");
        tank.setLevel(S88Level.UNIT);
        S88Element heater = new S88Element();
        heater.setId("CALENTAMIENTO_DE_TANQUE_UNO");
        heater.setLevel(S88Level.EQUIPMENTMODULE);

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.addChild(tank, heater);
        model.registerClass(heaters);
        return PlantSnapshotUseCase.of(model);
    }

    private static S88MasterRecipe withAnEmptyStep() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        return recipe;
    }

    @Test
    void aStepWithNoEquipmentSaysNothingAndIsAsBigAsAnyOther() {
        S88MasterRecipe recipe = withAnEmptyStep();
        S88RecipeElement empty = recipe.findElement("STEP").orElseThrow();
        String box = recipe.getProcedureLogic().findStep("BOX_STEP").orElseThrow().getId();

        RecipeChartScene.NodeSpec spec = RecipeChartScene.nodesOf(recipe).get(box);

        assertNull(spec.label(),
                "because its name is one the editor made up so the chart could hold it, and an"
                        + " author reading it would be reading something meant for nobody");
        assertEquals(SfcMetrics.widthOf(RecipeShapes.Shape.BOX), widthOf(box, recipe),
                "and an empty box is the size every other box is, because a box that shrinks to"
                        + " nothing reads as nothing being there rather than as something waiting");
    }

    @Test
    void aStepWithALongNameGrowsToHoldIt() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO_DE_TANQUE", plant);
        String bound = "CALENTAMIENTO_DE_TANQUE";

        int grown = widthOf(bound, recipe);
        RecipeChartScene.NodeSpec spec = RecipeChartScene.nodesOf(recipe).get(bound);

        assertTrue(grown > SfcMetrics.widthOf(RecipeShapes.Shape.BOX),
                "because a name cut off is a name the author cannot read, and "
                        + spec.label() + " is longer than the standard width");
        assertTrue(spec.label().startsWith(bound),
                "and the name is the one the equipment has, rather than the one it was created with");
    }

    /** The same plant, with two values a step of it is worked by. */
    private static S88PlantSnapshot plantWithParameters() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Parameters", Map.of(
                "TEMPERATURA_SP", Map.of("Type", "REAL", "Eng_Units/Enum", "C", "Default", "100"),
                "TIEMPO_SP", Map.of("Type", "REAL", "Eng_Units/Enum", "MIN", "Default", "5"),
                "COMMAND", Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "COMMAND",
                        "Default", "NONE")));

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element tank = new S88Element();
        tank.setId("TANQUE_1");
        tank.setLevel(S88Level.UNIT);
        S88Element heater = new S88Element();
        heater.setId("CALENTAMIENTO_TANQUE_1");
        heater.setLevel(S88Level.EQUIPMENTMODULE);

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.addChild(tank, heater);
        model.registerClass(heaters);
        return PlantSnapshotUseCase.of(model);
    }

    @Test
    void aStepBoundToAClassIsNotWrittenTwice() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO_DE_TANQUE", plant);

        String label = RecipeChartScene.nodesOf(recipe).get("CALENTAMIENTO_DE_TANQUE").label();

        assertEquals(List.of("CALENTAMIENTO_DE_TANQUE"), List.of(label.split("\\R")),
                "because binding names the step after the class, so writing the class under it says"
                        + " the same word twice and reads as two different things");
    }

    @Test
    void aStepSaysTheValuesItIsWorkedByUnderItsName() {
        S88PlantSnapshot plant = plantWithParameters();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO", plant);

        List<String> lines =
                List.of(RecipeChartScene.nodesOf(recipe).get("CALENTAMIENTO").label().split("\\R"));

        assertEquals("CALENTAMIENTO", lines.get(0), "and the name is what the box is known by");
        assertEquals(Set.of("100 C", "5 MIN"), new LinkedHashSet<>(lines.subList(1, lines.size())),
                "because a step that says it turns a heater on and nothing more leaves the engineer"
                        + " opening it to find out how hot, and the chart is what they read");
    }

@Test
    void whatTheBoxIsAllowedToWriteIsWhatItsFixedHeightHolds() {
        S88PlantSnapshot plant = plantWithParameters();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO", plant);
        String label = RecipeChartScene.nodesOf(recipe).get("CALENTAMIENTO").label();
        int lines = label.split("\\R").length;

        assertTrue(heightOfLines(lines) <= SfcMetrics.STEP_HEIGHT,
                "because the box is a fixed height and a line that does not fit is a line written"
                        + " over the border: " + lines + " lines need " + heightOfLines(lines)
                        + " and the box has " + SfcMetrics.STEP_HEIGHT);
        assertTrue(heightOfLines(lines + 1) > SfcMetrics.STEP_HEIGHT,
                lines + " lines need " + heightOfLines(lines) + ", and "
                        + (lines + 1) + " would need " + heightOfLines(lines + 1)
                        + " against a box of " + SfcMetrics.STEP_HEIGHT);
    }

@Test
    void aStepWithMoreValuesThanFitLeavesTheRestToThePointer() {
        S88PlantSnapshot plant = plantWithParameters();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO", plant);
        for (int n = 1; n <= 6; n++) {
            UpdateRecipeParameterUseCase.execute(recipe, recipe.findElement("CALENTAMIENTO")
                    .orElseThrow(), "PARAMETRO_" + n, String.valueOf(n), DataType.REAL, "C");
        }

        RecipeChartScene.NodeSpec spec = RecipeChartScene.nodesOf(recipe).get("CALENTAMIENTO");

        assertEquals(3, spec.label().split("\\R").length,
                "and the box writes only what it has room for, because it is a fixed height");
        assertTrue(spec.tooltip().contains("PARAMETRO_6 = 6 C"), spec.tooltip());
        assertTrue(spec.tooltip().contains("TEMPERATURA_SP = 100 C"), spec.tooltip());
    }

    @Test
    void whatTheBatchWritesWhileTheRecipeRunsIsNotWrittenOnTheBox() {
        S88PlantSnapshot plant = plantWithParameters();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO", plant);

        String label = RecipeChartScene.nodesOf(recipe).get("CALENTAMIENTO").label();

        assertFalse(label.contains("NONE"),
                "because the order a module is given belongs to the run and not to the recipe being"
                        + " written, and a box saying NONE reads as nothing happening");
    }

    @Test
    void theOrderIsStillCarriedOnTheStepSoTheBatchCanUseIt() {
        S88PlantSnapshot plant = plantWithParameters();
        S88MasterRecipe recipe = withAnEmptyStep();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_STEP", "CALENTAMIENTO", plant);

        assertTrue(recipe.findElement("CALENTAMIENTO").orElseThrow()
                        .findParameter("COMMAND").isPresent(),
                "because not drawing it on the box is not dropping it: the batch is what writes it,"
                        + " and it has to find the variable to write");
    }

    /** How tall a block of that many lines of the chart font comes out. */
    private static int heightOfLines(int lines) {
        java.awt.image.BufferedImage scratch =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scratch.createGraphics();
        try {
            return RecipeShapes.textHeight(g, "x\n".repeat(lines - 1) + "x");
        } finally {
            g.dispose();
        }
    }

    /** How wide the chart makes one box of a recipe. */
    private static int widthOf(String boxId, S88MasterRecipe recipe) {
        return boxOf(boxId, recipe).width;
    }

    /** How tall the chart makes one box of a recipe. */
    private static int heightOf(String boxId, S88MasterRecipe recipe) {
        return boxOf(boxId, recipe).height;
    }

    private static Rectangle boxOf(String boxId, S88MasterRecipe recipe) {
        Map<String, RecipeShapes.Shape> shapes = new java.util.LinkedHashMap<>();
        RecipeChartScene.nodesOf(recipe).forEach((id, spec) -> shapes.put(id, spec.shape()));
        var layout = new SfcLayoutEngine(recipe.getProcedureLogic(), shapes, widthsOf(recipe))
                .place();
        Rectangle where = layout.steps().get(boxId);
        return where == null ? new Rectangle(0, 0, -1, -1) : where;
    }

    /**
     * How wide the text of each node is, measured with the same font the chart paints with.
     */
    private static Map<String, Integer> widthsOf(S88MasterRecipe recipe) {
        java.awt.image.BufferedImage scratch =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scratch.createGraphics();
        try {
            Map<String, Integer> widths = new java.util.LinkedHashMap<>();
            RecipeChartScene.nodesOf(recipe).forEach((id, spec) ->
                    widths.put(id, RecipeShapes.textWidth(g, spec.label())));
            return widths;
        } finally {
            g.dispose();
        }
    }

    /**
     * How tall the text of each node is, measured with the same font the chart paints with.
     */
    private static Map<String, Integer> heightsOf(S88MasterRecipe recipe) {
        java.awt.image.BufferedImage scratch =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scratch.createGraphics();
        try {
            Map<String, Integer> heights = new java.util.LinkedHashMap<>();
            RecipeChartScene.nodesOf(recipe).forEach((id, spec) ->
                    heights.put(id, RecipeShapes.textHeight(g, spec.label())));
            return heights;
        } finally {
            g.dispose();
        }
    }
}