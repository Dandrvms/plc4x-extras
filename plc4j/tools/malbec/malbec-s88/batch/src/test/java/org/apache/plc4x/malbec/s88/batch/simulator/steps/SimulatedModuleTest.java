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
package org.apache.plc4x.malbec.s88.batch.simulator.steps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.batch.simulator.values.VariableStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedModuleTest {

    @Test
    void followRuleMovesToTheSetpointWithoutOvershoot() {
        Fixture fixture = fixture("CALENTADOR_1");
        fixture.store().set("TEMPERATURA_SP", 72.0, 0.0);
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP",
                        10.0, 0.5, 20.0)));
        SimulatedModule module = new SimulatedModule("CALENTADOR_1", fixture.element(),
                profile, fixture.store());

        assertEquals(ModuleState.IDLE, module.state());
        assertTrue(module.command(BatchCommand.START, 0.0).accepted());
        assertEquals(ModuleState.RUNNING, module.state());

        double previous = 20.0;
        int ticks = 0;
        while (module.state() == ModuleState.RUNNING && ticks < 20) {
            ticks++;
            module.tick(1.0, ticks);
            double current = fixture.store().numeric("TEMPERATURA_ACTUAL");
            assertTrue(current >= previous, "temperature went backwards");
            assertTrue(current <= 72.0, "temperature overshot the setpoint");
            previous = current;
        }

        assertEquals(ModuleState.COMPLETE, module.state());
        assertEquals(72.0, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);
        assertTrue(ticks <= 10);
    }

    @Test
    void aFollowRuleStopsWhenTheValueHasPassedTheSetpointGoingUp() {
        Fixture fixture = fixture("CALENTADOR_1");
        fixture.store().set("TEMPERATURA_ACTUAL", 102.0, 0.0);
        fixture.store().set("TEMPERATURA_SP", 100.0, 0.0);
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP",
                        10.0, 0.5, 20.0)));
        SimulatedModule module = new SimulatedModule("CALENTADOR_1", fixture.element(),
                profile, fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.tick(0.1, 0.1);

        assertEquals(ModuleState.COMPLETE, module.state());
        assertEquals(102.0, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9,
                "because a value past the setpoint must not be pulled back to it");
    }

    @Test
    void aFollowRuleStopsWhenTheValueHasPassedTheSetpointGoingDown() {
        Fixture fixture = fixture("CALENTADOR_1");
        fixture.store().set("TEMPERATURA_ACTUAL", 18.0, 0.0);
        fixture.store().set("TEMPERATURA_SP", 20.0, 0.0);
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP",
                        10.0, 0.5, 100.0)));
        SimulatedModule module = new SimulatedModule("CALENTADOR_1", fixture.element(),
                profile, fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.tick(0.1, 0.1);

        assertEquals(ModuleState.COMPLETE, module.state());
        assertEquals(18.0, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9,
                "because a value past the setpoint must not be pulled back to it");
    }

    @Test
    void followRuleSnapsToTheSetpointInsideTheTolerance() {
        Fixture fixture = fixture("CALENTADOR_1");
        fixture.store().set("TEMPERATURA_ACTUAL", 71.5, 0.0);
        fixture.store().set("TEMPERATURA_SP", 72.0, 0.0);
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP",
                        20.0, 0.5, 20.0)));
        SimulatedModule module = new SimulatedModule("CALENTADOR_1", fixture.element(),
                profile, fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.tick(1.0, 1.0);

        assertEquals(72.0, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);
    }

    @Test
    void pauseAndHoldFreezeValuesAndElapsed() {
        Fixture fixture = fixture("CALENTADOR_1");
        fixture.store().set("TEMPERATURA_SP", 100.0, 0.0);
        SimulationProfile profile = SimulationProfile.defaults().withFollowRules(List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP",
                        10.0, 0.5, 20.0)));
        SimulatedModule module = new SimulatedModule("CALENTADOR_1", fixture.element(),
                profile, fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.tick(1.0, 1.0);
        module.tick(1.0, 2.0);
        assertEquals(2.0, module.elapsed(), 1e-9);
        assertEquals(40.0, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);

        module.command(BatchCommand.PAUSE, 2.0);
        assertEquals(ModuleState.PAUSING, module.state());
        for (int tick = 3; tick <= 5; tick++) {
            module.tick(1.0, tick);
        }
        assertEquals(ModuleState.PAUSED, module.state());
        double pausedElapsed = module.elapsed();
        double pausedTemperature = fixture.store().numeric("TEMPERATURA_ACTUAL");
        for (int tick = 6; tick <= 10; tick++) {
            module.tick(1.0, tick);
        }
        assertEquals(pausedElapsed, module.elapsed(), 1e-9);
        assertEquals(pausedTemperature, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);

        module.command(BatchCommand.RESUME, 10.0);
        assertEquals(ModuleState.RUNNING, module.state());
        module.tick(1.0, 11.0);
        assertTrue(module.elapsed() > pausedElapsed);

        double beforeHoldElapsed = module.elapsed();
        double beforeHoldTemperature = fixture.store().numeric("TEMPERATURA_ACTUAL");
        module.command(BatchCommand.HOLD, 11.0);
        for (int tick = 12; tick <= 14; tick++) {
            module.tick(1.0, tick);
        }
        assertEquals(ModuleState.HELD, module.state());
        assertEquals(beforeHoldElapsed, module.elapsed(), 1e-9);
        assertEquals(beforeHoldTemperature, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);

        for (int tick = 15; tick <= 19; tick++) {
            module.tick(1.0, tick);
        }
        assertEquals(beforeHoldElapsed, module.elapsed(), 1e-9);
        assertEquals(beforeHoldTemperature, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);

        module.command(BatchCommand.RESTART, 19.0);
        for (int tick = 20; tick <= 22; tick++) {
            module.tick(1.0, tick);
        }
        assertEquals(ModuleState.RUNNING, module.state());
        assertEquals(beforeHoldElapsed, module.elapsed(), 1e-9);
        assertEquals(beforeHoldTemperature, fixture.store().numeric("TEMPERATURA_ACTUAL"), 1e-9);

        module.tick(1.0, 23.0);
        assertTrue(module.elapsed() > beforeHoldElapsed);
        assertTrue(fixture.store().numeric("TEMPERATURA_ACTUAL") > beforeHoldTemperature);
    }

    @Test
    void durationComesFromTheDurationParameter() {
        Fixture fixture = fixture("BOMBA_1");
        declareDuration(fixture.element(), null);
        fixture.store().define("DURACION", DataType.INTEGER, null, List.of(), 0.0, 0.0);
        fixture.store().set("DURACION", 2.5, 0.0);
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.setActiveStep(2, 2, 0.0);
        assertEquals(2.5, module.durationSeconds(), 1e-9);
        module.tick(1.0, 1.0);
        assertEquals(ModuleState.RUNNING, module.state());
        assertEquals(2, module.stepIndex());
        assertEquals(2, module.stepCount());
        assertEquals(0.4, fixture.store().numeric("PROGRESS_BOMBA_1"), 1e-9);

        module.tick(1.5, 2.5);
        assertEquals(ModuleState.COMPLETE, module.state());
        assertEquals(2, module.stepIndex());
        assertEquals(2.0, fixture.store().numeric("STEP_INDEX_BOMBA_1"), 1e-9);
        assertEquals(2.0, fixture.store().numeric("STEP_COUNT_BOMBA_1"), 1e-9);
        assertEquals(1.0, fixture.store().numeric("PROGRESS_BOMBA_1"), 1e-9);
    }

    @Test
    void aDeclaredZeroDurationCompletesWithoutWaiting() {
        Fixture fixture = fixture("BOMBA_1");
        declareDuration(fixture.element(), null);
        fixture.store().define("DURACION", DataType.INTEGER, null, List.of(), 0.0, 0.0);
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.tick(0.1, 0.1);

        assertEquals(ModuleState.COMPLETE, module.state());
    }

    @Test
    void manualModeWaitsForTheOperatorToCompleteTheStep() {
        Fixture fixture = fixture("BOMBA_1");
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());
        module.setAutomatic(false);

        module.command(BatchCommand.START, 0.0);
        module.tick(1.0, 1.0);
        assertEquals(ModuleState.RUNNING, module.state());

        module.completeNow(1.0);

        assertEquals(ModuleState.COMPLETE, module.state());
    }

    @Test
    void resetClearsTheActiveStep() {
        Fixture fixture = fixture("BOMBA_1");
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());
        module.command(BatchCommand.START, 0.0);
        module.setActiveStep(2, 5, 0.0);

        module.reset(1.0);

        assertEquals(0, module.stepIndex());
        assertEquals(0, module.stepCount());
        assertEquals(0.0, fixture.store().numeric("STEP_INDEX_BOMBA_1"), 1e-9);
        assertEquals(0.0, fixture.store().numeric("STEP_COUNT_BOMBA_1"), 1e-9);
    }

    @Test
    void elapsedTimeIsPublishedInTheUnitOfItsReport() {
        Fixture fixture = fixture("BOMBA_1");
        fixture.store().define("TIEMPO_TRANSCURRIDO", DataType.INTEGER, "MIN", List.of(), 0.0, 0.0);
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());
        module.command(BatchCommand.START, 0.0);

        module.tick(60.0, 60.0);

        assertEquals(1.0, fixture.store().numeric("TIEMPO_TRANSCURRIDO"), 1e-9);
    }

    @Test
    void aDurationInMinutesIsConvertedToSeconds() {
        Fixture fixture = fixture("BOMBA_1");
        declareDuration(fixture.element(), "MIN");
        fixture.store().define("DURACION", DataType.INTEGER, "MIN", List.of(), 1.0, 0.0);
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());

        module.command(BatchCommand.START, 0.0);

        assertEquals(60.0, module.durationSeconds(), 1e-9);
    }

    @Test
    void transientStatesSettleAfterTheirTicks() {
        Fixture fixture = fixture("BOMBA_1");
        SimulationProfile profile = new SimulationProfile(10.0, 3, 0.1, List.of());
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(), profile,
                fixture.store());

        module.command(BatchCommand.START, 0.0);
        module.command(BatchCommand.PAUSE, 0.0);
        assertEquals(ModuleState.PAUSING, module.state());
        module.tick(1.0, 1.0);
        assertEquals(ModuleState.PAUSING, module.state());
        module.tick(1.0, 2.0);
        assertEquals(ModuleState.PAUSING, module.state());
        module.tick(1.0, 3.0);
        assertEquals(ModuleState.PAUSED, module.state());

        module.command(BatchCommand.RESUME, 3.0);
        module.command(BatchCommand.STOP, 3.0);
        assertEquals(ModuleState.STOPPING, module.state());
        module.tick(1.0, 4.0);
        assertEquals(ModuleState.STOPPING, module.state());
        module.tick(1.0, 5.0);
        assertEquals(ModuleState.STOPPING, module.state());
        module.tick(1.0, 6.0);
        assertEquals(ModuleState.STOPPED, module.state());

        module.command(BatchCommand.RESET, 6.0);
        assertEquals(ModuleState.IDLE, module.state());
        module.command(BatchCommand.START, 6.0);
        module.command(BatchCommand.ABORT, 6.0);
        assertEquals(ModuleState.ABORTING, module.state());
        module.tick(1.0, 7.0);
        assertEquals(ModuleState.ABORTING, module.state());
        module.tick(1.0, 8.0);
        assertEquals(ModuleState.ABORTING, module.state());
        module.tick(1.0, 9.0);
        assertEquals(ModuleState.ABORTED, module.state());
    }

    @Test
    void aFailureAbortsTheActiveModule() {
        Fixture fixture = fixture("BOMBA_1");
        SimulatedModule module = new SimulatedModule("BOMBA_1", fixture.element(),
                SimulationProfile.defaults(), fixture.store());
        module.command(BatchCommand.START, 0.0);
        fixture.store().set("FAILURE_BOMBA_1", 1.0, 0.0);

        module.tick(1.0, 1.0);
        assertEquals(ModuleState.ABORTING, module.state());
        module.tick(1.0, 2.0);
        assertEquals(ModuleState.ABORTING, module.state());
        module.tick(1.0, 3.0);
        assertEquals(ModuleState.ABORTED, module.state());
    }

    private static void declareDuration(S88Element element, String unit) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        Object existing = element.getStructuredProperty("Parameters");
        if (existing instanceof Map<?, ?> map) {
            map.forEach((key, value) -> parameters.put(String.valueOf(key), value));
        }
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("Type", "INTEGER");
        if (unit != null) {
            definition.put("Eng_Units/Enum", unit);
        }
        definition.put("Default", "0");
        parameters.put("DURACION", definition);
        element.setProperty("Parameters", parameters);
    }

    private static Fixture fixture(String moduleId) {
        S88PlantSnapshot plant = DemoFixtures.plant();
        S88Element element = plant.findById(moduleId).orElseThrow();
        VariableStore store = VariableStore.of(plant, List.of(moduleId), new ArrayList<>());
        return new Fixture(element, store);
    }

    private record Fixture(S88Element element, VariableStore store) {
    }
}
