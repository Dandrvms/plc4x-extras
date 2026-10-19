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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.api.PlatformEnumerations;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The window that says what a bar waits on: one comparison, several joined, or a wait.
 */
class TransitionConditionDialogTest {

    /** A plant whose units publish a state and a level, and one unit that does. */
    private static S88PlantSnapshot plant() {
        S88ElementClass tanks = new S88ElementClass();
        tanks.setName("TANQUE_A");
        tanks.setTargetLevel(S88Level.UNIT);
        tanks.setProperty("Reports", Map.of(
                "ESTADO", Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "STATE", "Default", "IDLE"),
                "NIVEL", Map.of("Type", "REAL", "Eng_Units/Enum", "%")));

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element tank = new S88Element();
        tank.setId("TANQUE_1");
        tank.setLevel(S88Level.UNIT);
        tank.setProperty("Reports", Map.of(
                "ESTADO_1", Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "STATE", "Default", "IDLE"),
                "NIVEL_1", Map.of("Type", "REAL", "Eng_Units/Enum", "%")));

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, tank);
        model.registerClass(tanks);
        model.registerEnumeration(PlatformEnumerations.stateValue());
        return PlantSnapshotUseCase.of(model);
    }

    /** A recipe by class whose one step stands for a unit that publishes the reports. */
    private static S88MasterRecipe recipeByClass() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.CLASS);
        S88RecipeElement step = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        BindRecipeStepUseCase.bindToClass(recipe, null, step.getId(), "TANQUE_A", plant());
        return recipe;
    }

    /** A recipe for particular equipment, whose one step works on the unit itself. */
    private static S88MasterRecipe recipeForEquipment() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("REC", S88RecipeKind.INSTANCE);
        S88RecipeElement step = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        BindRecipeStepUseCase.bindToEquipment(recipe, null, step.getId(), "TANQUE_1", plant());
        return recipe;
    }

    private static TransitionConditionDialog dialogOf(S88MasterRecipe recipe, String current) {
        return new TransitionConditionDialog(null, recipe, null, "T1", plant(), current);
    }

    @Test
    void aValueOfAnEnumerationIsOfferedRatherThanOnlyChecked() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeByClass(), null);

            dialog.useReport("ESTADO");

            assertTrue(dialog.valuesOffered().contains("COMPLETE"),
                    "because the members are what the report can hold, so they can be chosen");
        });
    }

    @Test
    void oneConditionIsWrittenTheWayARecipeAlwaysWroteIt() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeByClass(), null);

            dialog.useReport("ESTADO");
            dialog.useOperator("=");
            dialog.useValue("COMPLETE");
            dialog.addCondition();

            assertEquals(List.of("ESTADO = COMPLETE"), dialog.clausesShown());

            dialog.accept();
            assertTrue(dialog.wasAccepted());
            assertEquals("Reports/ESTADO#=#COMPLETE", dialog.conditionText(),
                    "because a recipe by class names a base name and no equipment");
        });
    }

    @Test
    void severalConditionsJoinedByOrAreKeptAsASentence() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeByClass(), null);

            dialog.useReport("ESTADO");
            dialog.useOperator("=");
            dialog.useValue("COMPLETE");
            dialog.addCondition();
            dialog.useValue("IDLE");
            dialog.addCondition();
            dialog.joinWith("or");

            dialog.accept();
            assertEquals("OR([Reports/ESTADO#=#COMPLETE];[Reports/ESTADO#=#IDLE])",
                    dialog.conditionText());
        });
    }

    @Test
    void aWaitJoinsTheConditions() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeByClass(), null);

            dialog.useReport("ESTADO");
            dialog.useOperator("=");
            dialog.useValue("COMPLETE");
            dialog.addCondition();
            dialog.useWaitSeconds(30);
            dialog.addWait();
            dialog.joinWith("and");

            dialog.accept();
            assertEquals("AND([Reports/ESTADO#=#COMPLETE];WAIT(30))", dialog.conditionText());
        });
    }

    @Test
    void aRecipeForEquipmentNamesWhichOneItReads() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeForEquipment(), null);

            dialog.useEquipment("TANQUE_1");
            dialog.useReport("NIVEL_1");
            dialog.useOperator(">");
            dialog.useValue("80");
            dialog.addCondition();

            dialog.accept();
            assertEquals("TANQUE_1#NIVEL_1#>#80", dialog.conditionText());
        });
    }

    @Test
    void aValueTheReportCanNeverHoldIsRefusedWhereItIsWritten() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeForEquipment(), null);

            dialog.useEquipment("TANQUE_1");
            dialog.useReport("NIVEL_1");
            dialog.useOperator(">");
            dialog.useValue("warm");
            dialog.addCondition();

            assertEquals(List.of(), dialog.clausesShown(), "so nothing is added");
            assertTrue(dialog.statusShown().contains("number"),
                    "and it says a number was wanted, while the dialog stays open");
        });
    }

    @Test
    void whatTheBarAlreadyWaitsOnIsKeptWhenTheDialogIsOpenedAndAccepted() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog =
                    dialogOf(recipeByClass(), "OR([Reports/ESTADO#=#COMPLETE];[Reports/ESTADO#=#IDLE])");

            assertEquals(List.of("ESTADO = COMPLETE", "ESTADO = IDLE"), dialog.clausesShown());

            dialog.accept();
            assertEquals("OR([Reports/ESTADO#=#COMPLETE];[Reports/ESTADO#=#IDLE])",
                    dialog.conditionText());
        });
    }

    @Test
    void aBarCanBeGivenNothingToWaitOn() {
        onSwingThread(() -> {
            TransitionConditionDialog dialog = dialogOf(recipeByClass(), "Reports/ESTADO#=#COMPLETE");

            dialog.clear();

            assertTrue(dialog.wasAccepted());
            assertNull(dialog.conditionText());
        });
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
            AssertionError error = new AssertionError("the dialog failed");
            error.initCause(failure.get());
            throw error;
        }
    }
}
