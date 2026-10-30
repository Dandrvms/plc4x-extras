/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.batch.builder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.core.CreateRecipeElementUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.UpdateRecipeParameterUseCase;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.junit.jupiter.api.Test;

class PayloadValidatorTest {

    @Test
    void reportsAMissingPlant() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                DemoFixtures.demoRecipe(plant), null, "BATCH-NO-PLANT", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "no frozen plant"));
    }

    @Test
    void reportsAMissingBatchId() {
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request("  "));

        assertNull(result.payload());
        assertTrue(hasError(result, "no id"));
    }

    @Test
    void reportsAStepWithNoEquipmentOfItsClass() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        recipe.findElement("CALENTAR").orElseThrow().setEquipmentClassId("NO_SUCH_CLASS");

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-NO-EQUIPMENT", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "NO_SUCH_CLASS"));
    }

    @Test
    void reportsAConditionThatCannotBeRead() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        transition(recipe, "T_CALENTAR").setCondition("this is not a condition");

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-BAD-CONDITION", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "cannot be interpreted"));
        assertTrue(hasError(result, "T_CALENTAR"));
    }

    @Test
    void reportsAReportTheModuleDoesNotPublish() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        transition(recipe, "T_MANTENER").setCondition("Reports/NO_SUCH#=#1");

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-NO-REPORT", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "NO_SUCH"));
        assertTrue(hasError(result, "not published"));
    }

    @Test
    void reportsALiteralTheReportCannotHold() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        transition(recipe, "T_MANTENER").setCondition("Reports/TEMPERATURA_ACTUAL#>=#hot");

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-BAD-LITERAL", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "cannot be held by report"));
    }

    @Test
    void reportsAnAmbiguousPredecessor() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        S88RecipeElement unit = recipe.findElement("UP_REACTOR").orElseThrow();
        CreateRecipeElementUseCase.forEquipmentClass(recipe, unit, "OTRO",
                S88RecipeElementKind.PHASE, "BOMBA_TIPO");
        EditProcedureLogicUseCase.addStep(recipe, unit, "OTRO", "OTRO");
        EditProcedureLogicUseCase.addLink(recipe, unit, "OTRO_TO_T_MANTENER",
                List.of("OTRO"), List.of("T_MANTENER"), null);

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-AMBIGUOUS", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "ambiguous"));
    }

    @Test
    void reportsAChartWithNoBegin() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        chart.findStep("BEGIN").ifPresent(chart::removeStep);

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-NO-BEGIN", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "no BEGIN"));
    }

    @Test
    void reportsAnUnsoundChart() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        S88RecipeElement unit = recipe.findElement("UP_REACTOR").orElseThrow();
        S88ProcedureLogic chart = unit.getProcedureLogic();
        chart.getLinks().stream()
                .filter(link -> "CALENTAR_TO_T_MANTENER".equals(link.getId()))
                .findFirst()
                .ifPresent(chart::removeLink);

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-UNSOUND", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "T_MANTENER"));
    }

    @Test
    void reportsARecipeThatContainsItself() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        S88RecipeElement unit = recipe.findElement("UP_REACTOR").orElseThrow();
        EditProcedureLogicUseCase.addStep(recipe, unit, "SELF", "UP_REACTOR");

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-CYCLE", Map.of(), null));

        assertNull(result.payload());
        assertTrue(hasError(result, "contains itself"));
    }

    @Test
    void reportsAFollowRuleThatNamesAModuleWithoutTheVariables() {
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "NO_SUCH_SP",
                        1.0, 0.5, 20.0)));
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request(
                DemoFixtures.demoRecipe(DemoFixtures.plant()), DemoFixtures.plant(),
                "BATCH-BAD-PROFILE", Map.of(), profile));

        assertNull(result.payload());
        assertTrue(hasError(result, "does not publish"));
    }

    @Test
    void warnsAboutASetpointTheModuleDoesNotPublish() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = DemoFixtures.demoRecipe(plant);
        S88RecipeElement heat = recipe.findElement("CALENTAR").orElseThrow();
        UpdateRecipeParameterUseCase.execute(recipe, heat, "Parameters/NO_SUCH",
                "1", DataType.REAL, null);

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(
                recipe, plant, "BATCH-SETPOINT-WARNING", Map.of(), null));

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertNotNull(result.payload());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("NO_SUCH")));
    }

    private static org.apache.plc4x.malbec.s88.api.S88ProcedureTransition transition(
            S88MasterRecipe recipe, String transitionId) {
        S88RecipeElement unit = recipe.findElement("UP_REACTOR").orElseThrow();
        return unit.getProcedureLogic().findTransition(transitionId).orElseThrow();
    }

    private static boolean hasError(PrepareResult result, String text) {
        return result.errors().stream().anyMatch(error -> error.contains(text));
    }
}
