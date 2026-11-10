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
package org.apache.plc4x.malbec.s88.batch.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayloadFactory;
import org.apache.plc4x.malbec.s88.batch.builder.PrepareResult;
import org.apache.plc4x.malbec.s88.batch.simulator.SessionOptions;
import org.apache.plc4x.malbec.s88.batch.simulator.SimulatedSession;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.runtime.report.BatchEventRecorder;
import org.apache.plc4x.malbec.s88.batch.runtime.report.BatchRunSummary;
import org.apache.plc4x.malbec.s88.batch.runtime.report.ReportWriter;
import org.apache.plc4x.malbec.s88.batch.runtime.report.RunFolder;
import org.apache.plc4x.malbec.s88.core.RecipeFlowAnalyzer;
import org.apache.plc4x.malbec.s88.core.RecipeFlowFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BatchRuntimeServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @TempDir
    Path temporary;

    @Test
    void runWritesItsFourArtifactsAndTheReport() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-RUN");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runtime.start();
            awaitConnected(session);
            assertAccepted(runtime.sendCommand(BatchCommand.START));
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.COMPLETE,
                    "The batch did not complete");

            RunFolder folder = runtime.currentRunFolder();
            assertNotNull(folder);
            assertEquals(1, folder.run());
            assertTrue(Files.isRegularFile(folder.eventsFile()));
            assertTrue(Files.isRegularFile(folder.valuesFile()));
            assertTrue(Files.isRegularFile(folder.routeFile()));
            assertTrue(Files.isRegularFile(folder.reportFile()));
            assertNull(runtime.failure(), () -> String.valueOf(runtime.failure()));

            String report = Files.readString(folder.reportFile(), StandardCharsets.UTF_8);
            assertTrue(report.contains(payload.batchId()), report);
            assertTrue(report.contains(payload.sourceRecipeId()), report);
            assertTrue(report.contains("SIMULATED"), report);
            assertTrue(report.contains("COMPLETE"), report);
            assertTrue(report.contains("Tiempo por fase"), report);

            String route = RecipeFlowFormatter.toText(
                    RecipeFlowAnalyzer.analyze(payload.control()));
            assertEquals(route, Files.readString(folder.routeFile(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void eventsCsvHasOneRowPerEventWithoutDuplicates() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-EVENTS");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runToComplete(session, runtime);

            RunFolder folder = runtime.currentRunFolder();
            List<BatchEvent> events = BatchEventRecorder.read(folder.eventsFile(),
                    payload.batchId(), folder.run());
            assertFalse(events.isEmpty());
            assertEquals(events.size(), events.stream().map(BatchEvent::seq).distinct().count());
            assertEquals(runtime.lastSummary().events().size(), events.size());
        }
    }

    @Test
    void phaseTimesInTheReportMatchTheRecordedEvents() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-PHASES");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runToComplete(session, runtime);

            RunFolder folder = runtime.currentRunFolder();
            List<BatchEvent> events = BatchEventRecorder.read(folder.eventsFile(),
                    payload.batchId(), folder.run());
            BatchEvent activation = firstCompletedActivation(events);
            BatchEvent completion = completionAfter(events, activation);
            String report = Files.readString(folder.reportFile(), StandardCharsets.UTF_8);

            assertTrue(report.contains("data-sim-start=\"" + activation.simTime() + "\""), report);
            assertTrue(report.contains("data-sim-end=\"" + completion.simTime() + "\""), report);
        }
    }

    @Test
    void abortEndsWithAnAbortedReport() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-ABORT");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runtime.start();
            awaitConnected(session);
            assertAccepted(runtime.sendCommand(BatchCommand.START));
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.RUNNING,
                    "The batch did not start");
            assertAccepted(runtime.sendCommand(BatchCommand.ABORT));
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.ABORTED,
                    "The batch did not abort");

            String report = Files.readString(runtime.currentRunFolder().reportFile(),
                    StandardCharsets.UTF_8);
            assertTrue(report.contains("ABORTED"), report);
        }
    }

    @Test
    void closingAStartedRuntimeWritesAnInterruptedReport() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-INTERRUPTED");
        SimulatedSession session = session(payload);
        BatchRuntimeService runtime = runtime(session, payload);
        try {
            runtime.start();
            awaitConnected(session);
            assertAccepted(runtime.sendCommand(BatchCommand.START));
            await(() -> !session.client().lastSnapshot().activeSteps().isEmpty(),
                    "No step became active");

            RunFolder folder = runtime.currentRunFolder();
            runtime.close();

            assertTrue(Files.isRegularFile(folder.reportFile()));
            assertTrue(Files.readString(folder.reportFile(), StandardCharsets.UTF_8)
                    .contains("INTERRUPTED"));
            assertEquals("INTERRUPTED", runtime.lastSummary().outcome());
        } finally {
            session.close();
        }
    }

    @Test
    void resetAndASecondRunUseRunTwo() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-RESET");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runToComplete(session, runtime);
            RunFolder first = runtime.currentRunFolder();
            assertEquals(1, first.run());

            assertAccepted(runtime.sendCommand(BatchCommand.RESET));
            await(() -> session.client().lastSnapshot().run() == 2,
                    "The run did not advance");
            assertAccepted(runtime.sendCommand(BatchCommand.START));
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.COMPLETE,
                    "The second run did not complete");

            RunFolder second = runtime.currentRunFolder();
            assertEquals(2, second.run());
            assertTrue(Files.isRegularFile(first.reportFile()));
            assertTrue(Files.isRegularFile(second.reportFile()));
            assertTrue(Files.readString(second.reportFile(), StandardCharsets.UTF_8)
                    .contains("COMPLETE"));
        }
    }

    @Test
    void theReportIsReproducedFromItsEventsAndRunMetadata() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-REPRODUCE");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runToComplete(session, runtime);

            RunFolder folder = runtime.currentRunFolder();
            BatchRunSummary original = runtime.lastSummary();
            byte[] before = Files.readAllBytes(folder.reportFile());
            List<BatchEvent> events = BatchEventRecorder.read(folder.eventsFile(),
                    payload.batchId(), folder.run());

            ReportWriter.write(folder, original.withEvents(events));

            assertArrayEquals(before, Files.readAllBytes(folder.reportFile()));
        }
    }

    @Test
    void theReportEscapesHtml() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-ESCAPE");
        BatchPayload escaping = new BatchPayload(payload.batchId(), "<Demo>",
                payload.recipeVersion(), payload.createdAt(), payload.control(), payload.plant(),
                payload.moduleOfStep(), payload.unitOfStep(), payload.conditions(),
                payload.setpoints(), payload.profile(), payload.warnings());
        RunFolder folder = RunFolder.create(temporary.resolve("escape"), payload.batchId(), 1);
        BatchRunSummary summary = new BatchRunSummary(escaping, 1, "COMPLETE",
                null, null, 0, 0, List.of(), List.of());

        ReportWriter.write(folder, summary);

        String report = Files.readString(folder.reportFile(), StandardCharsets.UTF_8);
        assertTrue(report.contains("&lt;Demo&gt;"), report);
        assertFalse(report.contains("<Demo>"), report);
    }

    @Test
    void anInjectedFailureAppearsInTheReport() throws IOException {
        BatchPayload payload = payload("BATCH-RUNTIME-FAILURE");
        try (SimulatedSession session = session(payload);
                BatchRuntimeService runtime = runtime(session, payload)) {
            runtime.start();
            awaitConnected(session);
            assertAccepted(runtime.sendCommand(BatchCommand.START));
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.RUNNING,
                    "The batch did not start");

            session.control().injectFailure("BOMBA_1", 7);
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.ABORTED,
                    "The failure did not abort the batch");

            String report = Files.readString(runtime.currentRunFolder().reportFile(),
                    StandardCharsets.UTF_8);
            assertTrue(report.contains("FAILURE_INJECTED"), report);
        }
    }

    private BatchRuntimeService runtime(SimulatedSession session, BatchPayload payload) {
        return new BatchRuntimeService(session.client(), temporary.resolve("output"), payload);
    }

    private static SimulatedSession session(BatchPayload payload) {
        return SimulatedSession.create(payload, SessionOptions.defaults().withSpeed(600));
    }

    private static void runToComplete(SimulatedSession session, BatchRuntimeService runtime)
            throws IOException {
        runtime.start();
        awaitConnected(session);
        assertAccepted(runtime.sendCommand(BatchCommand.START));
        await(() -> session.client().lastSnapshot().batchState() == ModuleState.COMPLETE,
                "The batch did not complete");
        awaitQuiet(runtime);
        await(() -> runtime.lastSummary() != null
                        && runtime.lastSummary().events().size() == runtime.recordedEventCount()
                        && runtime.lastSummary().values().size() == runtime.recordedValueCount(),
                "The report did not settle");
    }

    private static void awaitConnected(SimulatedSession session) {
        await(() -> {
            BatchSnapshot snapshot = session.client().lastSnapshot();
            return snapshot != null && snapshot.connected();
        }, "The link did not connect");
    }

    private static void assertAccepted(CommandResult result) {
        assertTrue(result.accepted(), result.reason());
    }

    private static BatchEvent firstCompletedActivation(List<BatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.STEP_ACTIVATED)
                .filter(activation -> events.stream()
                        .anyMatch(event -> event.type() == EventType.STEP_COMPLETED
                                && event.seq() > activation.seq()
                                && java.util.Objects.equals(event.elementId(),
                                        activation.elementId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No step with a completion"));
    }

    private static void awaitQuiet(BatchRuntimeService runtime) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        int previous = runtime.recordedEventCount();
        int stable = 0;
        while (stable < 3) {
            if (System.nanoTime() >= deadline) {
                fail("The run did not become quiet");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail(interrupted);
            }
            int current = runtime.recordedEventCount();
            if (current == previous) {
                stable++;
            } else {
                previous = current;
                stable = 0;
            }
        }
    }

    private static BatchEvent completionAfter(List<BatchEvent> events, BatchEvent activation) {
        return events.stream()
                .filter(event -> event.type() == EventType.STEP_COMPLETED)
                .filter(event -> java.util.Objects.equals(event.elementId(),
                        activation.elementId()))
                .filter(event -> event.seq() > activation.seq())
                .findFirst()
                .orElseThrow(() -> new AssertionError("The step did not complete"));
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail(message);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail(interrupted);
            }
        }
    }

    private static BatchPayload payload(String batchId) {
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request(batchId));
        assertFalse(result.hasErrors(), () -> result.errors() + "\n" + result.warnings());
        return result.payload();
    }
}
