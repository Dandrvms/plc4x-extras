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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayloadFactory;
import org.apache.plc4x.malbec.s88.batch.builder.BatchRequest;
import org.apache.plc4x.malbec.s88.batch.builder.PrepareResult;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchMode;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void runsTheDemoBatchToCompletion() {
        SimulationEngine engine = engine(DemoFixtures.request("BATCH-RUN"));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tickUntil(engine, ModuleState.COMPLETE, 3000);

        assertEquals(ModuleState.COMPLETE, engine.batchState(), () -> engine.snapshot() + "\n"
                + engine.drainEvents() + "\n" + engine.warnings());
    }

    @Test
    void takesTheBranchAnOperatorChose() {
        SimulationEngine engine = engine(alternative(), DecisionMode.MANUAL, 1000.0);

        assertTrue(engine.command(BatchCommand.START).accepted());
        engine.tick();

        String pending = engine.snapshot().pendingDecision().text();
        assertFalse(pending.isEmpty(), () -> engine.drainEvents() + "\n" + engine.warnings());
        String branch = Arrays.stream(pending.split(","))
                .filter(id -> !"T_UP".equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No branch beside T_UP in " + pending));
        assertTrue(events(engine).stream().anyMatch(e -> e.type() == EventType.DECISION_NEEDED));

        assertTrue(engine.decide(branch));
        tickUntil(engine, ModuleState.COMPLETE, 50);

        assertEquals(ModuleState.COMPLETE, engine.batchState());
        assertTrue(fired(engine, branch));
        assertFalse(fired(engine, "T_UP"));
    }

    @Test
    void runsBothBranchesOfAParallelSplit() {
        SimulationEngine engine = engine(parallel());

        assertTrue(engine.command(BatchCommand.START).accepted());
        tickUntil(engine, ModuleState.COMPLETE, 50);

        assertEquals(ModuleState.COMPLETE, engine.batchState());
        List<BatchEvent> events = events(engine);
        long started = events.stream()
                .filter(e -> e.type() == EventType.STEP_ACTIVATED)
                .filter(e -> !e.elementId().endsWith("BEGIN") && !e.elementId().endsWith("END"))
                .count();
        assertTrue(started >= 2, () -> events.toString());
    }

    @Test
    void tripsTheLoopGuardOnAChartThatNeverStops() {
        SimulationEngine engine = engine(loop(), DecisionMode.AUTO, 5.0);

        assertTrue(engine.command(BatchCommand.START).accepted());
        tickUntil(engine, ModuleState.ABORTED, 50);

        assertEquals(ModuleState.ABORTED, engine.batchState(), () -> engine.snapshot() + "\n"
                + engine.drainEvents() + "\n" + engine.warnings());
        assertTrue(events(engine).stream().anyMatch(e -> e.type() == EventType.LOOP_GUARD_TRIPPED));
    }

    @Test
    void pausesAndResumesTheRun() {
        SimulationEngine engine = engine(DemoFixtures.request("BATCH-PAUSE"));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tick(engine, 5);
        assertTrue(engine.command(BatchCommand.PAUSE).accepted());
        engine.tick();

        assertEquals(ModuleState.PAUSED, engine.batchState());
        assertEquals(ModuleState.PAUSED, engine.moduleStates().get("BOMBA_1"));
        double simTime = engine.simTime();
        double temperature = value(engine, "TEMPERATURA_ACTUAL");
        tick(engine, 2);
        assertEquals(simTime + 0.2, engine.simTime(), 1e-9);
        assertEquals(temperature, value(engine, "TEMPERATURA_ACTUAL"), 1e-9);

        assertTrue(engine.command(BatchCommand.RESUME).accepted());
        engine.tick();
        assertEquals(ModuleState.RUNNING, engine.batchState());
        assertEquals(ModuleState.RUNNING, engine.moduleStates().get("BOMBA_1"));
    }

    @Test
    void holdsAndRestartsTheRun() {
        SimulationEngine engine = engine(DemoFixtures.request("BATCH-HOLD"));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tick(engine, 5);
        assertTrue(engine.command(BatchCommand.HOLD).accepted());
        engine.tick();

        assertEquals(ModuleState.HELD, engine.batchState());
        assertEquals(ModuleState.HELD, engine.moduleStates().get("BOMBA_1"));

        assertTrue(engine.command(BatchCommand.RESTART).accepted());
        engine.tick();
        assertEquals(ModuleState.RUNNING, engine.batchState());
        assertEquals(ModuleState.RUNNING, engine.moduleStates().get("BOMBA_1"));
    }

    @Test
    void abortsStopsAndResetsTheRun() {
        SimulationEngine engine = engine(DemoFixtures.request("BATCH-ABORT"));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tick(engine, 5);
        assertTrue(engine.command(BatchCommand.ABORT).accepted());
        tickUntil(engine, ModuleState.ABORTED, 10);

        assertEquals(ModuleState.ABORTED, engine.batchState());
        assertEquals(ModuleState.ABORTED, engine.moduleStates().get("BOMBA_1"));

        int run = engine.snapshot().run();
        assertTrue(engine.command(BatchCommand.RESET).accepted());
        assertEquals(ModuleState.IDLE, engine.batchState());
        assertEquals(run + 1, engine.snapshot().run());
        assertEquals(20.0, value(engine, "TEMPERATURA_ACTUAL"), 1e-9);
        assertTrue(engine.moduleStates().values().stream()
                .allMatch(state -> state == ModuleState.IDLE));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tick(engine, 5);
        assertTrue(engine.command(BatchCommand.STOP).accepted());
        tickUntil(engine, ModuleState.STOPPED, 10);

        assertEquals(ModuleState.STOPPED, engine.batchState());
        assertEquals(ModuleState.STOPPED, engine.moduleStates().get("BOMBA_1"));
        assertNotEquals(ModuleState.COMPLETE, engine.batchState());
    }

    @Test
    void injectsAndClearsAFailure() {
        SimulationEngine engine = engine(DemoFixtures.request("BATCH-FAILURE"));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tick(engine, 5);
        engine.injectFailure("BOMBA_1", 1);
        tickUntil(engine, ModuleState.ABORTED, 10);

        assertEquals(ModuleState.ABORTED, engine.batchState());
        assertEquals(ModuleState.ABORTED, engine.moduleStates().get("BOMBA_1"));
        assertTrue(events(engine).stream().anyMatch(e -> e.type() == EventType.FAILURE_INJECTED));

        engine.clearFailure("BOMBA_1");
        assertFalse(engine.warnings().contains("The module 'BOMBA_1' failed."));
    }

    @Test
    void reportsTheRunInSnapshotsAndEvents() {
        SimulationEngine engine = engine(DemoFixtures.request("BATCH-SNAPSHOT"));

        assertTrue(engine.command(BatchCommand.START).accepted());
        tick(engine, 3);

        BatchSnapshot snapshot = engine.snapshot();
        assertEquals("BATCH-SNAPSHOT", snapshot.batchId());
        assertEquals(BatchMode.SIMULATED, snapshot.mode());
        assertEquals(DecisionMode.AUTO, engine.decisionMode());
        assertEquals(ModuleState.RUNNING, snapshot.batchState());
        assertEquals(0.3, snapshot.simTime(), 1e-9);
        assertTrue(snapshot.newEvents().isEmpty());
        assertFalse(snapshot.activeSteps().isEmpty());
        assertTrue(snapshot.completedStepKeys().contains("/BEGIN"));

        List<BatchEvent> events = engine.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e.type() == EventType.STEP_ACTIVATED));
        assertTrue(events.stream().anyMatch(e -> e.type() == EventType.TRANSITION_FIRED));
        assertTrue(events.stream().allMatch(e -> e.run() == snapshot.run()));
        assertTrue(engine.drainEvents().isEmpty());
    }

    private static SimulationEngine engine(BatchRequest request) {
        return engine(request, DecisionMode.AUTO, 1000.0);
    }

    private static SimulationEngine engine(BatchRequest request, DecisionMode mode,
                                           double maxActivations) {
        PrepareResult result = BatchPayloadFactory.prepare(request);
        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        BatchPayload payload = result.payload();
        SimulationProfile profile = new SimulationProfile(1.0, 0, 0.1, List.of(
                new FollowRule("CALENTADOR_1", "TEMPERATURA_ACTUAL", "TEMPERATURA_SP",
                        1.0, 0.5, 20.0)));
        return new SimulationEngine(payload, profile, CLOCK, mode, ExitPolicy.COMPLETE_ON_EXIT,
                maxActivations);
    }

    private static BatchRequest alternative() {
        return branchRequest("BATCH-ALT", DemoFixtures.alternativeRecipe(DemoFixtures.plant()));
    }

    private static BatchRequest parallel() {
        return branchRequest("BATCH-PAR", DemoFixtures.parallelRecipe(DemoFixtures.plant()));
    }

    private static BatchRequest loop() {
        return branchRequest("BATCH-LOOP", DemoFixtures.loopRecipe(DemoFixtures.plant()));
    }

    private static BatchRequest branchRequest(String batchId, S88MasterRecipe recipe) {
        S88PlantSnapshot plant = DemoFixtures.plant();
        return DemoFixtures.request(recipe, plant, batchId, Map.of(), null);
    }

    private static void tick(SimulationEngine engine, int times) {
        for (int tick = 0; tick < times; tick++) {
            engine.tick();
        }
    }

    private static void tickUntil(SimulationEngine engine, ModuleState state, int maxTicks) {
        for (int tick = 0; tick < maxTicks && engine.batchState() != state; tick++) {
            engine.tick();
        }
    }

    private static List<BatchEvent> events(SimulationEngine engine) {
        return engine.drainEvents();
    }

    private static boolean fired(SimulationEngine engine, String transitionId) {
        return engine.snapshot().firedTransitionKeys().stream()
                .anyMatch(key -> key.equals(transitionId) || key.endsWith("/" + transitionId));
    }

    private static double value(SimulationEngine engine, String name) {
        Object value = engine.snapshot().variables().get(name).value();
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }
}
