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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.junit.jupiter.api.Test;

class BatchPayloadFactoryTest {

    @Test
    void preparesTheDemoBatch() {
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request("BATCH-1"));

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        BatchPayload payload = result.payload();
        assertNotNull(payload);
        assertEquals("BATCH-1", payload.batchId());
        assertEquals(DemoFixtures.RECIPE_ID, payload.sourceRecipeId());
        assertEquals("1.0", payload.recipeVersion());
        assertEquals("CTL_BATCH-1", payload.control().getId());
        assertEquals("REACTOR_1", payload.unitOfStep().get("/UP_REACTOR"));
        assertEquals("BOMBA_1", payload.moduleOfStep().get("UP_REACTOR/CARGAR"));
        assertEquals("CALENTADOR_1", payload.moduleOfStep().get("UP_REACTOR/CALENTAR"));
        assertEquals("CALENTADOR_1", payload.moduleOfStep().get("UP_REACTOR/MANTENER"));
        assertEquals(SimulationProfile.defaults(), payload.profile());

        PreparedCondition state = payload.conditions().get("UP_REACTOR/T_CALENTAR");
        assertNotNull(state);
        PreparedCondition.Comparison stateComparison = (PreparedCondition.Comparison) state;
        assertEquals("BOMBA_1", stateComparison.moduleId());
        assertEquals("STATE_BOMBA_1", stateComparison.variable());
        assertEquals(DataType.ENUMERATION, stateComparison.dataType());
        assertEquals(S88ConditionOperator.EQUALS, stateComparison.operator());
        assertEquals("COMPLETE", stateComparison.literal());
        assertTrue(stateComparison.enumerationValues().contains("COMPLETE"));

        PreparedCondition temperature = payload.conditions().get("UP_REACTOR/T_MANTENER");
        assertNotNull(temperature);
        PreparedCondition.Comparison temperatureComparison = (PreparedCondition.Comparison) temperature;
        assertEquals("CALENTADOR_1", temperatureComparison.moduleId());
        assertEquals("TEMPERATURA_ACTUAL", temperatureComparison.variable());
        assertEquals(DataType.REAL, temperatureComparison.dataType());
        assertEquals(S88ConditionOperator.GREATER_OR_EQUAL, temperatureComparison.operator());
        assertEquals("72", temperatureComparison.literal());

        PreparedSetpoint heatSetpoint = payload.setpoints().get("UP_REACTOR/CALENTAR").get(0);
        assertEquals("CALENTADOR_1", heatSetpoint.moduleId());
        assertEquals("TEMPERATURA_SP", heatSetpoint.variable());
        assertEquals(DataType.REAL, heatSetpoint.dataType());
        assertEquals("72", heatSetpoint.value());

        PreparedSetpoint holdSetpoint = payload.setpoints().get("UP_REACTOR/MANTENER").get(0);
        assertEquals("CALENTADOR_1", holdSetpoint.moduleId());
        assertEquals("DURACION", holdSetpoint.variable());
        assertEquals(DataType.INTEGER, holdSetpoint.dataType());
        assertEquals("30", holdSetpoint.value());
    }

    @Test
    void usesTheEquipmentTheInstanceRecipeNames() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("INSTANCE_DEMO", S88RecipeKind.INSTANCE);
        S88RecipeElement unit = EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BEGIN",
                "UP_REACTOR", "UP_REACTOR", "T_UP", null);
        unit.addActualEquipmentId("REACTOR_1");
        S88RecipeElement phase = EditProcedureLogicUseCase.insertStepAfter(recipe, unit, "BEGIN",
                "CALENTAR", "CALENTAR", "T_CALENTAR", null);
        phase.addActualEquipmentId("CALENTADOR_1");
        phase.addParameter(S88RecipeParameter.of("TEMPERATURA_SP_CALENTADOR_1", "72",
                DataType.REAL, null));
        unit.getProcedureLogic().findTransition("T_CALENTAR").orElseThrow().setCondition(
                "CALENTADOR_1#TEMPERATURA_ACTUAL_CALENTADOR_1#>=#72");

        S88Element heater = plant.findById("CALENTADOR_1").orElseThrow();
        Map<String, Object> reports = new java.util.LinkedHashMap<>(
                heater.getStructuredProperty(S88PlantModel.REPORTS));
        reports.put("TEMPERATURA_ACTUAL_CALENTADOR_1",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C"));
        heater.setProperty(S88PlantModel.REPORTS, reports);
        Map<String, Object> parameters = new java.util.LinkedHashMap<>(
                heater.getStructuredProperty(S88PlantModel.PARAMETERS));
        parameters.put("TEMPERATURA_SP_CALENTADOR_1",
                Map.of("Type", "REAL", "Eng_Units/Enum", "C"));
        heater.setProperty(S88PlantModel.PARAMETERS, parameters);

        PrepareResult result = BatchPayloadFactory.prepare(new BatchRequest(recipe, plant,
                "BATCH-INSTANCE", Map.of(), null));

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertNotNull(result.payload());
        assertEquals("REACTOR_1", result.payload().unitOfStep().get("/UP_REACTOR"));
        assertEquals("CALENTADOR_1", result.payload().moduleOfStep().get("UP_REACTOR/CALENTAR"));
        PreparedCondition.Comparison condition = (PreparedCondition.Comparison)
                result.payload().conditions().get("UP_REACTOR/T_CALENTAR");
        assertEquals("TEMPERATURA_ACTUAL_CALENTADOR_1", condition.variable());
        PreparedSetpoint setpoint = result.payload().setpoints()
                .get("UP_REACTOR/CALENTAR").get(0);
        assertEquals("TEMPERATURA_SP_CALENTADOR_1", setpoint.variable());
    }

    @Test
    void anOverrideChoosesAnotherModuleOfTheClass() {
        S88PlantSnapshot plant = DemoFixtures.plant();
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request(
                DemoFixtures.demoRecipe(plant), plant,
                "BATCH-OVERRIDE", Map.of("CALENTADOR_TIPO", "CALENTADOR_2"), null));

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertNotNull(result.payload());
        assertEquals("CALENTADOR_2", result.payload().moduleOfStep().get("UP_REACTOR/CALENTAR"));
        assertEquals("CALENTADOR_2", result.payload().moduleOfStep().get("UP_REACTOR/MANTENER"));
    }

    @Test
    void carriesTheSimulationProfile() {
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP", 1.0, 0.5, 20.0)));
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request(
                DemoFixtures.demoRecipe(DemoFixtures.plant()), DemoFixtures.plant(),
                "BATCH-PROFILE", Map.of(), profile));

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertNotNull(result.payload());
        assertEquals(profile, result.payload().profile());
    }
}
