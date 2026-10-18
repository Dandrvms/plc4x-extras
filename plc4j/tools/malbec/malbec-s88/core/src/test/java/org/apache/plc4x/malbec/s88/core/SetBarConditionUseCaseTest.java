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
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.PlatformEnumerations;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bar waits on something, and what it waits on comes off a piece of equipment.
 *
 * <p>
 * The two kinds of recipe name it differently, and this is the place where getting them the wrong way
 * round produces a recipe that reads one piece of equipment in place of another.
 */
class SetBarConditionUseCaseTest {

    /** One class of heaters, and two of them publishing a report of the same shape. */
    private static S88PlantSnapshot plant() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C")));

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element tank = new S88Element();
        tank.setId("TANQUE_1");
        tank.setLevel(S88Level.UNIT);
        tank.setProperty("Reports", Map.of("NIVEL",
                Map.of("Type", "REAL", "Eng_Units/Enum", "%")));

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.registerClass(heaters);
        for (int n = 1; n <= 2; n++) {
            S88Element heater = new S88Element();
            heater.setId("CALENTAMIENTO_" + n);
            heater.setLevel(S88Level.EQUIPMENTMODULE);
            heater.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL_" + n,
                    Map.of("Type", "REAL", "Eng_Units/Enum", "C")));
            model.addChild(tank, heater);
        }
        return PlantSnapshotUseCase.of(model);
    }

    private static S88MasterRecipe recipeOf(S88RecipeKind kind) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", kind);
        Levels.unitProcedure(recipe, "TRAMO", null);
        Levels.operation(recipe, "TRAMO", "PASO");
        S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(recipe,
                Levels.owner(recipe, "PASO"), "BEGIN");
        BindRecipeStepUseCase.bindToClass(recipe, Levels.owner(recipe, "PASO"),
                empty.getId(), "CALENTAMIENTO", plant());
        return recipe;
    }

    @Test
    void aRecipeByClassNamesTheBaseNameAndNoEquipment() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = recipeOf(S88RecipeKind.CLASS);

        SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant, null, "TEMPERATURA_ACTUAL", ">", "80");

        S88ProcedureTransition bar = recipe.getProcedureLogic().findTransition("T1").orElseThrow();
        assertEquals("Reports/TEMPERATURA_ACTUAL#>#80", bar.getCondition(),
                "because there is no equipment to name yet, and the base name is what something"
                        + " will be resolved against later");
        assertNull(bar.expression().getEquipment(),
                "and the text says so, rather than leaving a piece of equipment behind from before");
    }

    @Test
    void aRecipeForParticularEquipmentNamesWhichOneItReads() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = recipeOf(S88RecipeKind.INSTANCE);

        SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant, "CALENTAMIENTO_2",
                "TEMPERATURA_ACTUAL_2", ">", "80");

        S88ProcedureTransition bar = recipe.getProcedureLogic().findTransition("T1").orElseThrow();
        assertEquals("CALENTAMIENTO_2#TEMPERATURA_ACTUAL_2#>#80", bar.getCondition(),
                "because two of them publish a report of the same name and reading the one off the"
                        + " other is a recipe that does the wrong thing");
        assertEquals("CALENTAMIENTO_2", bar.expression().getEquipment(),
                "and the text is taken apart again into the equipment it reads off");
    }

    @Test
    void aReportTheEquipmentDoesNotPublishIsRefused() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = recipeOf(S88RecipeKind.INSTANCE);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant,
                        "CALENTAMIENTO_1", "TEMPERATURA_ACTUAL_2", ">", "80"));

        assertTrue(failure.getMessage().contains("does not publish"),
                "and it says why, because a report that is not there is a bar that never opens");
    }

    @Test
    void theEquipmentOfferedIsOfTheLevelTheChartWorksOn() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = recipeOf(S88RecipeKind.CLASS);

        assertTrue(SetBarConditionUseCase.equipmentWithReports(plant, null).contains("TANQUE_1"),
                "because the steps of the chart of the recipe are unit procedures, so a bar there"
                        + " reads a unit");
        assertTrue(SetBarConditionUseCase.equipmentWithReports(plant, Levels.owner(recipe, "PASO"))
                        .contains("CALENTAMIENTO_1"),
                "and the steps of the chart of an operation are phases, so a bar there reads a"
                        + " module");
        assertTrue(SetBarConditionUseCase.equipmentWithReports(plant, null)
                        .stream().noneMatch("CALENTAMIENTO_1"::equals),
                "and a module is not offered where the bar reads a unit");
    }

    @Test
    void aBaseNameIsOfferedToARecipeByClass() {
        S88PlantSnapshot plant = plant();

        assertTrue(SetBarConditionUseCase.reportsOfTheClass(plant, "CALENTAMIENTO")
                        .contains("TEMPERATURA_ACTUAL"),
                "because a recipe by class reads base names, which are the ones the class declares");
    }

    @Test
    void aLiteralThatIsNotANumberIsRefusedWhereTheReportIsANumber() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = recipeOf(S88RecipeKind.INSTANCE);

        assertThrows(IllegalArgumentException.class,
                () -> SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant,
                        "CALENTAMIENTO_1", "TEMPERATURA_ACTUAL_1", ">", "not a number"),
                "because a temperature that is a word is a temperature the equipment will never have");
    }

    /**
     * A report whose type is an enumeration, which is what every piece of equipment publishes for
     * its state.
     * <p>
     * The property the plant keeps its units in is the same one that names the enumeration when the
     * type says so, so the value this reads back is a name and not a list of anything.
     */
    private static S88PlantSnapshot plantWithAStateReport() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Reports", Map.of("ESTADO", Map.of(
                "Type", "ENUMERATION", "Eng_Units/Enum", "STATE", "Default", "IDLE")));

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
        heater.setId("CALENTAMIENTO_1");
        heater.setLevel(S88Level.EQUIPMENTMODULE);
        heater.setProperty("Reports", Map.of("ESTADO_1", Map.of(
                "Type", "ENUMERATION", "Eng_Units/Enum", "STATE", "Default", "IDLE")));

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.addChild(tank, heater);
        model.registerClass(heaters);
        model.registerEnumeration(PlatformEnumerations.stateValue());
        return PlantSnapshotUseCase.of(model);
    }

    private static S88MasterRecipe recipeWithAStateReport(S88RecipeKind kind) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", kind);
        Levels.unitProcedure(recipe, "TRAMO", null);
        Levels.operation(recipe, "TRAMO", "PASO");
        S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(recipe,
                Levels.owner(recipe, "PASO"), "BEGIN");
        BindRecipeStepUseCase.bindToClass(recipe, Levels.owner(recipe, "PASO"),
                empty.getId(), "CALENTAMIENTO", plantWithAStateReport());
        return recipe;
    }

    @Test
    void aValueOfAnEnumerationIsOneOfItsMembersAndNotItsName() {
        S88PlantSnapshot plant = plantWithAStateReport();
        S88MasterRecipe recipe = recipeWithAStateReport(S88RecipeKind.INSTANCE);

        SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant, "CALENTAMIENTO_1",
                "ESTADO_1", "=", "COMPLETE");

        assertEquals("CALENTAMIENTO_1#ESTADO_1#=#COMPLETE",
                recipe.getProcedureLogic().findTransition("T1").orElseThrow().getCondition(),
                "because the members are what the report can actually hold, and the property that"
                        + " names the enumeration is not a list of them");
    }

    @Test
    void theNameOfTheEnumerationIsNotAValueItTakes() {
        S88PlantSnapshot plant = plantWithAStateReport();
        S88MasterRecipe recipe = recipeWithAStateReport(S88RecipeKind.INSTANCE);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant,
                        "CALENTAMIENTO_1", "ESTADO_1", "=", "STATE"));

        assertTrue(failure.getMessage().contains("COMPLETE"),
                "and it says which values are right instead, because the author is being told"
                        + " 'one of STATE', which is not a value the report can hold");
    }

    @Test
    void theMembersAreOfferedRatherThanOnlyChecked() {
        S88PlantSnapshot plant = plantWithAStateReport();
        S88MasterRecipe recipe = recipeWithAStateReport(S88RecipeKind.INSTANCE);

        List<String> offered = SetBarConditionUseCase.allowedValues(
                recipe, null, "T1", plant, "CALENTAMIENTO_1", "ESTADO_1");

        assertTrue(offered.contains("COMPLETE"), offered.toString());
        assertTrue(offered.contains("IDLE"), offered.toString());
    }

    @Test
    void aReportThatIsNotAnEnumerationOffersNothingToChooseFrom() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = recipeOf(S88RecipeKind.INSTANCE);

        assertEquals(List.of(),
                SetBarConditionUseCase.allowedValues(recipe, null, "T1", plant, "CALENTAMIENTO_1",
                        "TEMPERATURA_ACTUAL_1"),
                "because a number is written rather than chosen, so there is no list to show");
    }

    @Test
    void anEnumerationNamedWithThePrefixTheModelUsesIsStillFound() {
        S88PlantSnapshot plant = plantWithAStateReport();

        assertTrue(SetBarConditionUseCase.membersOf(plant, "ENUM_STATE").contains("COMPLETE"),
                "because the model keeps enumerations under a prefix that a report does not spell"
                        + " and both spellings have to be looked up");
    }

    @Test
    void aBarCanBeGivenNothingToWaitOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);

        EditProcedureLogicUseCase.setCondition(recipe, null, "T1", null);

        assertTrue(recipe.getProcedureLogic().findTransition("T1").orElseThrow().crossesAlways(),
                "and then the flow crosses it as soon as the step before it is done, which is what a"
                        + " bar with nothing to wait on says");
    }

    @Test
    void theEquipmentIsWrittenAsPartOfTheAddressAndReadBackOut() {
        S88VariableAddress address = S88VariableAddress.parse("Reports/STATE");
        var comparison = org.apache.plc4x.malbec.s88.api.S88ConditionExpression.on(
                "CALENTAMIENTO_1", address,
                org.apache.plc4x.malbec.s88.api.S88ConditionOperator.EQUALS, "COMPLETE");

        var readBack = org.apache.plc4x.malbec.s88.api.S88ConditionExpression
                .parse(comparison.toText());

        assertEquals("CALENTAMIENTO_1", readBack.getEquipment());
        assertEquals(address, readBack.getVariable());
        assertEquals(comparison.toText(), readBack.toText(),
                "and the text survives being written and read again, which is what a recipe stored in"
                        + " a file needs");
    }

    @Test
    void aConditionIsShownTheWayAPersonReadsIt() {
        var byEquipment = org.apache.plc4x.malbec.s88.api.S88ConditionExpression.on("CALENTAMIENTO_1",
                S88VariableAddress.parse("Reports/STATE"),
                org.apache.plc4x.malbec.s88.api.S88ConditionOperator.EQUALS, "COMPLETE");
        var byClass = org.apache.plc4x.malbec.s88.api.S88ConditionExpression.of(
                S88VariableAddress.parse("Reports/TEMPERATURA_ACTUAL"),
                org.apache.plc4x.malbec.s88.api.S88ConditionOperator.GREATER, "80");

        assertEquals("CALENTAMIENTO_1.STATE = COMPLETE", byEquipment.toDisplay(),
                "because the raw text names the container and uses the separators a recipe stores,"
                        + " and neither is what the person at the chart reads");
        assertEquals("TEMPERATURA_ACTUAL > 80", byClass.toDisplay(),
                "and with no equipment named there is nothing to put in front of the report");
    }

    /** Two classes of unit, both publishing a report of the same name and of different types. */
    private static S88PlantSnapshot plantWithTwoUnitClasses() {
        S88ElementClass tanksA = new S88ElementClass();
        tanksA.setName("TANQUE_A");
        tanksA.setTargetLevel(S88Level.UNIT);
        tanksA.setProperty("Reports", Map.of("ESTADO", Map.of(
                "Type", "ENUMERATION", "Eng_Units/Enum", "STATE", "Default", "IDLE")));

        S88ElementClass tanksB = new S88ElementClass();
        tanksB.setName("TANQUE_B");
        tanksB.setTargetLevel(S88Level.UNIT);
        tanksB.setProperty("Reports", Map.of("ESTADO", Map.of(
                "Type", "INTEGER", "Eng_Units/Enum", "1")));

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);

        S88PlantModel model = new S88PlantModel(root);
        model.registerClass(tanksA);
        model.registerClass(tanksB);
        model.registerEnumeration(PlatformEnumerations.stateValue());
        return PlantSnapshotUseCase.of(model);
    }

    @Test
    void aReportOfTwoOfTheRecipeClassesIsReadOffTheOneBeforeTheBar() {
        S88PlantSnapshot plant = plantWithTwoUnitClasses();
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        Levels.unitProcedure(recipe, "OTRO", "TANQUE_B");
        S88RecipeElement step = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        BindRecipeStepUseCase.bindToClass(recipe, null, step.getId(), "TANQUE_A", plant);

        SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant, null, "ESTADO", "=", "COMPLETE");

        assertEquals("Reports/ESTADO#=#COMPLETE",
                recipe.getProcedureLogic().findTransition("T1").orElseThrow().getCondition(),
                "because the class of the step before the bar decides which of two reports of the"
                        + " same name is meant, and only that one lets the value be a member of its"
                        + " enumeration rather than a number");
    }

    @Test
    void aReportOnlySomeOtherClassOfTheRecipePublishesIsStillReadable() {
        S88PlantSnapshot plant = plantWithTwoUnitClasses();
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        Levels.unitProcedure(recipe, "OTRO", "TANQUE_B");

        SetBarConditionUseCase.waitUntil(recipe, null, "T1", plant, null, "ESTADO", "=", "7");

        assertEquals("Reports/ESTADO#=#7",
                recipe.getProcedureLogic().findTransition("T1").orElseThrow().getCondition(),
                "because a bar can be pointed at a piece of equipment elsewhere in the recipe on"
                        + " purpose, and there the integer report is the one that is meant");
    }
}