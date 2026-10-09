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

import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A step of a recipe is a piece of equipment with values given to its parameters, and the two kinds
 * of recipe say that in two different ways.
 */
class BindRecipeStepUseCaseTest {

    /**
     * A plant with one tank, two heaters of one class, and the parameters and reports a heater
     * publishes.
     *
     * <p>
     * The class publishes base names, the way a class of equipment does, and the equipment publishes
     * its own names, which is what makes the difference between the two kinds of recipe visible.
     */
    private static S88PlantSnapshot plant() {
        return PlantSnapshotUseCase.of(modelOf());
    }

    /** The same plant built afresh, so that a test can take it apart without touching another's. */
    private static S88PlantModel modelOf() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Parameters", Map.of("TEMPERATURA_SP", Map.of(
                "Type", "REAL", "Eng_Units/Enum", "C", "Default", "80")));
        heaters.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL", Map.of(
                "Type", "REAL", "Eng_Units/Enum", "C")));

        S88ElementClass tanks = new S88ElementClass();
        tanks.setName("TANQUE");
        tanks.setTargetLevel(S88Level.UNIT);

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element tank = new S88Element();
        tank.setId("TANQUE_1");
        tank.setLevel(S88Level.UNIT);
        tank.setClass(tanks);

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.registerClass(heaters);
        model.registerClass(tanks);
        for (int n = 1; n <= 2; n++) {
            S88Element heater = new S88Element();
            heater.setId("CALENTAMIENTO_TANQUE_" + n);
            heater.setLevel(S88Level.EQUIPMENTMODULE);
            heater.setClass(heaters);
            heater.setProperty("Parameters", Map.of("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_" + n,
                    Map.of("Type", "REAL", "Eng_Units/Enum", "C", "Default", "100")));
            heater.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL_CALENTAMIENTO_TANQUE_" + n,
                    Map.of("Type", "REAL", "Eng_Units/Enum", "C")));
            model.addChild(tank, heater);
        }
        return model;
    }

    /** A recipe with one empty box to bind something to. */
    private static S88MasterRecipe recipeWithAnEmptyBox() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BEGIN", "EMPTY", "BOX_EMPTY",
                "T_EMPTY", null);
        return recipe;
    }

    /** The same heaters, with an order drawn from an enumeration instead of a temperature. */
    private static S88PlantSnapshot plantWithAnOrder() {
        S88PlantModel model = modelOf();
        S88ElementClass heaters = model.findClass("CALENTAMIENTO");
        heaters.setProperty("Parameters", Map.of("COMMAND",
                Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "COMMAND", "Default", "NONE")));
        return PlantSnapshotUseCase.of(model);
    }

    @Test
    void anOrderIsWrittenAsTheEnumerationItIsAndNotAsAUnit() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        S88RecipeElement element = BindRecipeStepUseCase.bindToClass(
                recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plantWithAnOrder());
        S88ParameterValue command =
                element.findParameter("COMMAND").orElseThrow().getFirstValue();

        assertEquals(DataType.ENUMERATION, command.getDataType(), "and the type says what it is");
        assertNull(command.getUnitOfMeasure(),
                "and there is no unit, because the plant keeps the name of the enumeration in the"
                        + " place a unit would go and a name is not a measure");
        assertEquals(List.of("COMMAND"), command.getEnumerationSetIds(),
                "and the name it goes by is kept where a value of that enumeration can be found,"
                        + " rather than where a measure would be");
    }

    @Test
    void aTemperatureStillCarriesItsUnitAsOne() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        S88RecipeElement element = BindRecipeStepUseCase.bindToClass(
                recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plant());
        S88ParameterValue temperature =
                element.findParameter("TEMPERATURA_SP").orElseThrow().getFirstValue();

        assertEquals(DataType.REAL, temperature.getDataType(), "and the type is the one declared");
        assertEquals("C", temperature.getUnitOfMeasure(),
                "and the unit is kept as a unit, because it is one");
    }

    @Test
    void aStepBoundToAClassCarriesNoEquipment() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        S88RecipeElement element = BindRecipeStepUseCase.bindToClass(
                recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plant());

        assertEquals("CALENTAMIENTO", element.getEquipmentClassId(),
                "and the class is what the step names");
        assertNull(element.getEquipmentUid(), "with no equipment, because a class recipe leaves the"
                + " choice of equipment to the batch");
        assertTrue(element.getActualEquipmentIds().isEmpty(), "and none named either");
    }

    @Test
    void aStepBoundToAClassCarriesTheBaseNamesItPublishes() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        S88RecipeElement element = BindRecipeStepUseCase.bindToClass(
                recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plant());

        assertEquals("80", element.findParameter("TEMPERATURA_SP").orElseThrow()
                .getFirstValue().getFirstValueString(),
                "and the value is the one the class declares, to be resolved against a piece of equipment"
                        + " later rather than written as a piece of equipment's own name");
        assertTrue(element.findParameter("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1").isEmpty(),
                "and not the name of one piece of equipment, because a class says nothing about which equipment"
                        + " will be behind it");
    }

    @Test
    void aStepBoundToAClassIsNamedAfterTheClass() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plant());

        assertEquals("CALENTAMIENTO", recipe.findElement("CALENTAMIENTO").orElseThrow().getId(),
                "and the step takes the name of the equipment it stands for");
        assertEquals("CALENTAMIENTO",
                recipe.getProcedureLogic().findStep("CALENTAMIENTO").orElseThrow().getId(),
                "and so does its box, because the box is the step on the chart");
    }

    @Test
    void twoStepsOnTheSameClassAreNumberedRatherThanSharingAName() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();
        EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BOX_EMPTY", "SECOND", "BOX_SECOND",
                "T_SECOND", null);

        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plant());
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_SECOND", "CALENTAMIENTO", plant());

        assertTrue(recipe.findElement("CALENTAMIENTO").isPresent(), "the first keeps the plain name");
        assertEquals("CALENTAMIENTO_1",
                recipe.getProcedureLogic().findStep("CALENTAMIENTO_1").orElseThrow()
                        .getRecipeElementId(),
                "and the second carries a number, because two steps of one class in one recipe"
                        + " cannot both be called the same thing");
        assertTrue(recipe.getProcedureLogic().findStep("BOX_SECOND").isEmpty(),
                "and its box is renamed with it, because the box is the step on the chart and a box"
                        + " left under the old name works on a step that is not there");
    }

    @Test
    void aStepBoundToAEquipmentNamesThatEquipmentAndKeepsItsIdentity() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();
        recipe.setKind(S88RecipeKind.INSTANCE);
        S88PlantSnapshot plant = plant();
        String uid = plant.findById("CALENTAMIENTO_TANQUE_1").orElseThrow().getUid();

        S88RecipeElement element = BindRecipeStepUseCase.bindToEquipment(
                recipe, null, "BOX_EMPTY", "CALENTAMIENTO_TANQUE_1", plant);

        assertEquals(List.of("CALENTAMIENTO_TANQUE_1"), element.getActualEquipmentIds(),
                "and the equipment is what the step names");
        assertEquals(uid, element.getEquipmentUid(),
                "by its identity rather than by its name, so renaming the equipment in the plant"
                        + " leaves this recipe saying the same equipment");
        assertNull(element.getEquipmentClassId(), "and no class, because a piece of equipment is not a class");
        assertEquals("CALENTAMIENTO_TANQUE_1", element.getId(), "and the step is named after it");
    }

    @Test
    void aStepBoundToAEquipmentCarriesThatEquipmentsOwnVariableNames() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();
        recipe.setKind(S88RecipeKind.INSTANCE);

        S88RecipeElement element = BindRecipeStepUseCase.bindToEquipment(
                recipe, null, "BOX_EMPTY", "CALENTAMIENTO_TANQUE_1", plant());

        assertEquals("100", element.findParameter("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1")
                        .orElseThrow().getFirstValue().getFirstValueString(),
                "and there is nothing left to resolve, because the equipment publishes names of its"
                        + " own and the recipe uses those");
        assertTrue(element.findParameter("TEMPERATURA_SP").isEmpty(),
                "and not the base name of the class, which is a question about a piece of equipment this recipe"
                        + " does not name");
    }

    @Test
    void whatAStepCanBeBoundToDependsOnTheKindOfRecipe() {
        S88PlantSnapshot plant = plant();

        assertEquals(List.of("CALENTAMIENTO", "TANQUE"),
                BindRecipeStepUseCase.choices(plant, true),
                "a class recipe chooses a class, and a unit is a class too because a unit is written"
                        + " as the steps of the modules it is made of");
        assertEquals(List.of("CALENTAMIENTO_TANQUE_1", "CALENTAMIENTO_TANQUE_2"),
                BindRecipeStepUseCase.choices(plant, false),
                "and one piece of equipment recipe chooses the equipment, in the order they sit in the plant");
    }

    @Test
    void aStepCannotBeBoundToSomethingThePlantDoesNotHave() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        assertThrows(IllegalArgumentException.class, () -> BindRecipeStepUseCase.bindToClass(
                        recipe, null, "BOX_EMPTY", "NOT_A_CLASS", plant()),
                "because a recipe naming equipment that is not there is a recipe nobody can run");
        assertThrows(IllegalArgumentException.class, () -> BindRecipeStepUseCase.bindToEquipment(
                        recipe, null, "BOX_EMPTY", "NOT_A_MACHINE", plant()),
                "for either kind of recipe");
    }

    @Test
    void aStepCannotBeBoundWithoutAPlantToBindItTo() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_EMPTY", "CALENTAMIENTO", null));

        assertTrue(failure.getMessage().contains("plant"),
                "and it says why, because a step is a piece of equipment and there is none to name");
    }

    @Test
    void theStopOfTheProcessHasNothingToAttach() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BindRecipeStepUseCase.bindToClass(recipe, null, "END", "CALENTAMIENTO", plant()));

        assertTrue(failure.getMessage().contains("stops"),
                "and it says why, because the end of the process is not a piece of equipment");
        assertFalse(recipe.getProcedureLogic().findStep("CALENTAMIENTO").isPresent(),
                "and nothing was renamed");
    }

    /**
     * A recipe is measured against the plant it was written against, and a step naming equipment that
     * plant does not have is a step that cannot run.
     */
    @Test
    void aStepNamingAClassThePlantDoesNotHaveIsReported() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_EMPTY", "CALENTAMIENTO", plant());
        S88PlantSnapshot aNarrowerPlant = plantWithout("CALENTAMIENTO");

        assertFalse(RecipeConformance.of(recipe, aNarrowerPlant).excess().isEmpty(),
                "and it is said out loud rather than left for a run to discover");
        assertTrue(RecipeConformance.of(recipe, plant()).excess().isEmpty(),
                "and against the plant it was written against there is nothing wrong with it: "
                        + RecipeConformance.of(recipe, plant()).excess());
    }

    @Test
    void aStepNamingAEquipmentThatIsNotThereIsReported() {
        S88MasterRecipe recipe = recipeWithAnEmptyBox();
        recipe.setKind(S88RecipeKind.INSTANCE);
        BindRecipeStepUseCase.bindToEquipment(recipe, null, "BOX_EMPTY", "CALENTAMIENTO_TANQUE_1",
                plant());

        assertFalse(RecipeConformance.of(recipe, plantWithout("CALENTAMIENTO_TANQUE_1"))
                        .excess().isEmpty(),
                "and it is reported by identity rather than by name, because renaming a piece of equipment does"
                        + " not take it away and taking it away does not rename it");
    }

    /** The same plant, with one class or equipment left out. */
    private static S88PlantSnapshot plantWithout(String what) {
        S88PlantModel model = modelOf();
        S88Element root = model.getRoot();
        S88Element cell = root.getChildren().get(0);
        S88Element tank = cell.getChildren().get(0);
        for (S88Element child : new java.util.ArrayList<>(tank.getChildren())) {
            if (child.getId().equals(what)) {
                tank.removeChild(child);
            }
        }
        if (model.findClass(what) != null) {
            model.getClasses().values().removeIf(c -> c.getName().equals(what));
        }
        return PlantSnapshotUseCase.of(model);
    }
}