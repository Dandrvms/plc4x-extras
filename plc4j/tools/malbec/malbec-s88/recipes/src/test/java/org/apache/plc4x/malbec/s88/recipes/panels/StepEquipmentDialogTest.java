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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.PlatformEnumerations;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A step of a recipe is a piece of equipment with values on it, and the window that shows them is
 * the only place an author changes them.
 *
 * <p>
 * No window is opened here. What is checked is what the tables say and what the values written back
 * are, because that is the part that can be wrong without anybody seeing a window appear.
 */
class StepEquipmentDialogTest {

    /** A class of heaters and two of them, one with its own variable names. */
    private static S88PlantSnapshot plant() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Parameters", Map.of("TEMPERATURA_SP",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C", "Default", "80",
                        "Min", "50", "Max", "120")));
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
        S88Element heater = new S88Element();
        heater.setId("CALENTAMIENTO_TANQUE_1");
        heater.setLevel(S88Level.EQUIPMENTMODULE);
        heater.setProperty("Parameters", Map.of("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C", "Default", "100")));
        heater.setProperty("Reports", Map.of("TEMPERATURA_ACTUAL_CALENTAMIENTO_TANQUE_1",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C")));

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.addChild(tank, heater);
        model.registerClass(heaters);
        return PlantSnapshotUseCase.of(model);
    }

    /** The same heaters, with a mode of operation drawn from an enumeration rather than a number. */
    private static S88PlantSnapshot plantWithAnEnumeration() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Parameters", Map.of("MODO",
                Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "MODO", "Default", "AUTOMATICO")));

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
        model.registerEnumeration(mode());
        return PlantSnapshotUseCase.of(model);
    }

    private static S88Enumeration mode() {
        S88Enumeration enumeration = new S88Enumeration("MODO");
        enumeration.setValue("AUTOMATICO", 0);
        enumeration.setValue("MANUAL", 1);
        return enumeration;
    }

    /** The editor state a view of that recipe would be built on. */
    private static RecipeEditorModel modelOf(S88MasterRecipe recipe, S88PlantSnapshot plant) {
        try {
            return RecipeEditorModel.open(null, RecipeBytes.holding(recipe), plant);
        } catch (IOException failure) {
            throw new AssertionError("a recipe that was just written could not be read", failure);
        }
    }

    private static S88MasterRecipe recipeBoundToTheClass(S88PlantSnapshot plant) {
        S88MasterRecipe recipe = org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase
                .execute("REC", S88RecipeKind.CLASS);
        S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        BindRecipeStepUseCase.bindToClass(recipe, null, "BOX_" + empty.getId(),
                "CALENTAMIENTO", plant);
        return recipe;
    }

    @Test
    void aStepBoundToAEquipmentShowsThatEquipmentsOwnParameters() {
        onSwingThread(() -> {
            S88PlantSnapshot plant = plant();
            S88MasterRecipe recipe = org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase
                    .execute("REC", S88RecipeKind.INSTANCE);
            S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
            BindRecipeStepUseCase.bindToEquipment(recipe, null, "BOX_" + empty.getId(),
                    "CALENTAMIENTO_TANQUE_1", plant);
            RecipeEditorModel model = modelOf(recipe, plant);

            StepEquipmentDialog dialog = new StepEquipmentDialog(null, model, empty);

            assertEquals(List.of("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1=100"),
                    dialog.valuesShown(),
                    "and the name is the equipment's own, because there is nothing left to resolve"
                            + " for a recipe that names the equipment");
        });
    }

    @Test
    void aStepBoundToAClassShowsTheBaseNamesAndWhatIsAllowed() {
        onSwingThread(() -> {
            S88PlantSnapshot plant = plant();
            S88MasterRecipe recipe = recipeBoundToTheClass(plant);
            S88RecipeElement element = recipe.findElement("CALENTAMIENTO").orElseThrow();
            RecipeEditorModel model = modelOf(recipe, plant);

            StepEquipmentDialog dialog = new StepEquipmentDialog(null, model, element);

            assertEquals(List.of("TEMPERATURA_SP=80"), dialog.valuesShown(),
                    "and the name is the one the class declares, to be resolved against a piece of equipment"
                            + " later");
            assertEquals("50 .. 120", dialog.allowedShown(),
                    "and what the plant will accept is said out, so a value outside it is a value"
                            + " the operator can see is outside it");
        });
    }

    @Test
    void aStepWithNoEquipmentHasNothingToShowAndSaysSo() {
        onSwingThread(() -> {
            S88PlantSnapshot plant = plant();
            S88MasterRecipe recipe = org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase
                    .execute("REC", S88RecipeKind.CLASS);
            S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");

            assertTrue(empty.getEquipmentClassId() == null,
                    "so a step that was just added has no equipment, and that is what the chart"
                            + " marks it with");
        });
    }

    @Test
    void writingAValueLeavesTheTypeAndTheUnitItWasGiven() {
        onSwingThread(() -> {
            S88PlantSnapshot plant = plant();
            S88MasterRecipe recipe = recipeBoundToTheClass(plant);
            S88RecipeElement element = recipe.findElement("CALENTAMIENTO").orElseThrow();
            RecipeEditorModel model = modelOf(recipe, plant);
            StepEquipmentDialog dialog = new StepEquipmentDialog(null, model, element);

            dialog.putValue("TEMPERATURA_SP", "95");
            dialog.saveForTest();
            S88ParameterValue written =
                    element.findParameter("TEMPERATURA_SP").orElseThrow().getFirstValue();

            assertEquals("95", written.getFirstValueString(), "and the value is what was typed");
            assertEquals(DataType.REAL, written.getDataType(),
                    "and the type is still there, because writing a value writes the value whole and a"
                            + " type left out of that write is a type thrown away");
            assertEquals("C", written.getUnitOfMeasure(),
                    "and so is the unit, which is the one thing the author did not type");
        });
    }

    @Test
    void anEnumerationSaysWhichItIsRatherThanWhatUnitItHas() {
        onSwingThread(() -> {
            S88PlantSnapshot plant = plantWithAnEnumeration();
            S88MasterRecipe recipe = recipeBoundToTheClass(plant);
            S88RecipeElement element = recipe.findElement("CALENTAMIENTO").orElseThrow();
            RecipeEditorModel model = modelOf(recipe, plant);
            StepEquipmentDialog dialog = new StepEquipmentDialog(null, model, element);

            assertEquals(List.of("ENUMERATION / MODO"), dialog.typeAndUnitShown(),
                    "because an enumeration has no unit, and showing its name under a column that says"
                            + " Unit would be saying a name is a unit");
        });
    }

    @Test
    void whatTheBatchWritesIsNotOfferedToBeSet() {
        onSwingThread(() -> {
            S88PlantSnapshot plant = plantWithAnOrder();
            S88MasterRecipe recipe = recipeBoundToTheClass(plant);
            S88RecipeElement element = recipe.findElement("CALENTAMIENTO").orElseThrow();
            RecipeEditorModel model = modelOf(recipe, plant);
            StepEquipmentDialog dialog = new StepEquipmentDialog(null, model, element);

            assertEquals(List.of(), dialog.valuesShown(),
                    "because the order a module is given belongs to the run, and offering it here"
                            + " would ask the author for something they do not decide");
            assertTrue(element.findParameter("COMMAND").isPresent(),
                    "while the step still carries it, because the batch is what writes it and it has"
                            + " to find the variable to write");
        });
    }

    /** A class whose one parameter is the order the batch gives, which the editor does not ask for. */
    private static S88PlantSnapshot plantWithAnOrder() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Parameters", Map.of("COMMAND",
                Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "COMMAND", "Default", "NONE")));

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
        model.registerEnumeration(PlatformEnumerations.commandValues());
        return PlantSnapshotUseCase.of(model);
    }

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
        } catch (InterruptedException | java.lang.reflect.InvocationTargetException problem) {
            throw new AssertionError("the check could not be run on the right thread", problem);
        }
        if (failure.get() != null) {
            AssertionError error = new AssertionError("the view failed");
            error.initCause(failure.get());
            throw error;
        }
    }


}