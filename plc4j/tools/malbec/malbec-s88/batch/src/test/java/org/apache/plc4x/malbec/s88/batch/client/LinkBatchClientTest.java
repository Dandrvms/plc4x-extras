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
package org.apache.plc4x.malbec.s88.batch.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayloadFactory;
import org.apache.plc4x.malbec.s88.batch.builder.PrepareResult;
import org.apache.plc4x.malbec.s88.batch.client.sim.SimulatedSession;
import org.apache.plc4x.malbec.s88.batch.link.BatchContract;
import org.apache.plc4x.malbec.s88.batch.link.LinkStatus;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.link.WriteResult;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.simulator.SimulatedPlc;
import org.apache.plc4x.malbec.s88.batch.simulator.steps.ExitPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LinkBatchClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void runsTheDemoBatchOnlyThroughTheBatchClient() {
        BatchPayload payload = payload("BATCH-LINK-RUN");
        List<BatchEvent> events = new CopyOnWriteArrayList<>();
        try (SimulatedSession session = SimulatedSession.create(payload,
                SessionOptions.defaults().withSpeed(600))) {
            BatchClient client = session.client();
            client.addListener(snapshot -> events.addAll(snapshot.newEvents()));
            await(() -> client.lastSnapshot() != null && client.lastSnapshot().connected(),
                    "The link did not connect");

            CommandResult start = client.sendCommand(BatchCommand.START);
            assertTrue(start.accepted(), start.reason());
            await(() -> client.lastSnapshot().batchState() == ModuleState.COMPLETE,
                    "The batch did not complete");

            assertTrue(hasEvent(events, EventType.BATCH_STARTED), events.toString());
            assertTrue(hasEvent(events, EventType.STEP_ACTIVATED), events.toString());
            assertTrue(hasEvent(events, EventType.STEP_COMPLETED), events.toString());
            assertTrue(hasEvent(events, EventType.PARAMETER_DOWNLOADED), events.toString());
            assertTrue(hasEvent(events, EventType.BATCH_COMPLETED), events.toString());
        }
    }

    @Test
    void reportsThePlantReasonWhenItRejectsACommand() {
        BatchPayload payload = payload("BATCH-LINK-REJECT");
        try (SimulatedSession session = SimulatedSession.create(payload,
                SessionOptions.defaults().withSpeed(600))) {
            BatchClient client = session.client();
            await(() -> client.lastSnapshot() != null && client.lastSnapshot().connected(),
                    "The link did not connect");

            CommandResult result = client.sendCommand(BatchCommand.PAUSE);
            assertFalse(result.accepted());
            assertNotNull(result.reason());
            assertTrue(result.reason().contains("PAUSE"), result.reason());
            assertEquals(ModuleState.IDLE, client.lastSnapshot().batchState());
        }
    }

    @Test
    void linkLossMarksTheValuesStaleAndRefusesCommands() {
        BatchPayload payload = payload("BATCH-LINK-LOSS");
        try (SimulatedSession session = SimulatedSession.create(payload,
                SessionOptions.defaults().withSpeed(600))) {
            BatchClient client = session.client();
            List<BatchEvent> events = new CopyOnWriteArrayList<>();
            client.addListener(snapshot -> events.addAll(snapshot.newEvents()));
            await(() -> client.lastSnapshot() != null && client.lastSnapshot().connected(),
                    () -> describe(client));

            session.control().setLinkUp(false);
            await(() -> !client.lastSnapshot().connected(), "The link stayed connected");
            await(() -> client.lastSnapshot().variables().values().stream()
                    .anyMatch(sample -> sample.quality() == Quality.STALE),
                    "No value was marked stale");
            assertFalse(client.sendCommand(BatchCommand.START).accepted());
            assertEquals("sin enlace", client.sendCommand(BatchCommand.START).reason());

            session.control().setLinkUp(true);
            await(() -> client.lastSnapshot().connected(), "The link did not come back");
            await(() -> hasEvent(events, EventType.CONNECTION_RESTORED), events.toString());
        }
    }

    @Test
    void pausingTheClockKeepsTheLinkAlive() {
        BatchPayload payload = payload("BATCH-LINK-PAUSED");
        try (SimulatedSession session = SimulatedSession.create(payload,
                SessionOptions.defaults().withClockRunning(false))) {
            BatchClient client = session.client();
            AtomicInteger updates = new AtomicInteger();
            client.addListener(snapshot -> updates.incrementAndGet());
            await(() -> client.lastSnapshot() != null && client.lastSnapshot().connected(),
                    "The link did not connect");
            int before = updates.get();

            session.control().pauseClock();

            await(() -> updates.get() > before + 2,
                    "The paused plant stopped sending its heartbeat");
        }
    }

    @Test
    void simulationControlWritesAVariableIntoThePlant() {
        BatchPayload payload = payload("BATCH-LINK-SET");
        try (SimulatedSession session = SimulatedSession.create(payload,
                SessionOptions.defaults().withSpeed(600))) {
            BatchClient client = session.client();
            await(() -> client.lastSnapshot() != null && client.lastSnapshot().connected(),
                    "The link did not connect");

            session.control().setVariable("TEMPERATURA_ACTUAL", 99.0);

            await(() -> {
                org.apache.plc4x.malbec.s88.batch.model.VariableSample sample =
                        client.lastSnapshot().variables().get("TEMPERATURA_ACTUAL");
                return sample != null && sample.value() instanceof Number number
                        && number.doubleValue() == 99.0;
            }, "The injected value did not reach the link");
        }
    }

    @Test
    void stepTicksProducesExactlyTheRequestedHeartbeats() {
        BatchPayload payload = payload("BATCH-LINK-STEP");
        FakeProcessLink link = new FakeProcessLink();
        SimulatedPlc plc = new SimulatedPlc(payload, payload.profile(),
                link, SessionOptions.defaults().withClockRunning(false),
                DecisionMode.AUTO, ExitPolicy.COMPLETE_ON_EXIT);
        try {
            plc.start();
            await(() -> link.writes.contains(BatchContract.heartbeat(payload.batchId())),
                    "The plant did not publish its first heartbeat");
            link.writes.clear();

            plc.stepTicks(3);

            long heartbeats = link.writes.stream()
                    .filter(BatchContract.heartbeat(payload.batchId())::equals)
                    .count();
            assertEquals(3, heartbeats);
        } finally {
            plc.close();
        }
    }

    @Test
    void theSameClientWorksOverAnotherProcessLink() {
        BatchPayload payload = payload("BATCH-LINK-FAKE");
        FakeProcessLink link = new FakeProcessLink();
        LinkBatchClient client = new LinkBatchClient(link);
        client.load(payload);
        List<BatchSnapshot> snapshots = new CopyOnWriteArrayList<>();
        client.addListener(snapshots::add);
        client.connect();

        await(() -> client.lastSnapshot() != null && client.lastSnapshot().connected(),
                "The fake link did not connect");
        link.emit(BatchContract.state(payload.batchId()), "RUNNING");
        link.emit(BatchContract.run(payload.batchId()), 1.0);
        link.emit(BatchContract.simTime(payload.batchId()), 2.5);
        link.emit(BatchContract.activeSteps(payload.batchId()), "UP_REACTOR/CALENTAR");
        link.emit("STEP_INDEX_CALENTADOR_1", 2.0);
        link.emit("STEP_COUNT_CALENTADOR_1", 5.0);
        link.emit("PROGRESS_CALENTADOR_1", 0.6);
        link.emit(BatchContract.heartbeat(payload.batchId()), 1.0);

        await(() -> client.lastSnapshot().batchState() == ModuleState.RUNNING,
                "The snapshot did not follow the fake link");
        assertTrue(client.lastSnapshot().activeSteps().stream()
                .anyMatch(step -> "CALENTAR".equals(step.stepId())
                        && step.stepIndex() == 2 && step.stepCount() == 5
                        && Math.abs(step.progress() - 0.6) < 1e-9));
        assertTrue(hasSnapshotEvent(snapshots, EventType.STEP_ACTIVATED));
        client.disconnect();
    }

    private static BatchPayload payload(String batchId) {
        PrepareResult result = BatchPayloadFactory.prepare(DemoFixtures.request(batchId));
        assertFalse(result.hasErrors(), () -> result.errors() + "\n" + result.warnings());
        return result.payload();
    }

    private static boolean hasEvent(List<BatchEvent> events, EventType type) {
        return events.stream().anyMatch(event -> event.type() == type);
    }

    private static boolean hasSnapshotEvent(List<BatchSnapshot> snapshots, EventType type) {
        return snapshots.stream().flatMap(snapshot -> snapshot.newEvents().stream())
                .anyMatch(event -> event.type() == type);
    }

    private static void await(BooleanSupplier condition, String message) {
        await(condition, () -> message);
    }

    private static void await(BooleanSupplier condition, Supplier<String> message) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail(message.get());
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail(interrupted);
            }
        }
    }

    private static String describe(BatchClient client) {
        BatchSnapshot snapshot = client.lastSnapshot();
        if (snapshot == null) {
            return "The link did not connect: no snapshot";
        }
        StringBuilder text = new StringBuilder("The link did not connect. Bad variables:");
        snapshot.variables().forEach((name, sample) -> {
            if (sample.quality() != Quality.GOOD) {
                text.append(' ').append(name).append('=').append(sample.value())
                        .append('/').append(sample.quality());
            }
        });
        return text.append(" total=").append(snapshot.variables().size()).toString();
    }

    private static final class FakeProcessLink implements ProcessLink {

        private final Map<String, List<Consumer<VariableUpdate>>> subscribers = new ConcurrentHashMap<>();
        private final List<Consumer<LinkStatus>> statusListeners = new CopyOnWriteArrayList<>();
        private final List<String> writes = new CopyOnWriteArrayList<>();
        private volatile LinkStatus status = LinkStatus.DISCONNECTED;

        @Override
        public void connect() {
            status = LinkStatus.CONNECTED;
            statusListeners.forEach(listener -> listener.accept(status));
        }

        @Override
        public LinkStatus status() {
            return status;
        }

        @Override
        public void addStatusListener(Consumer<LinkStatus> listener) {
            statusListeners.add(listener);
            listener.accept(status);
        }

        @Override
        public AutoCloseable subscribe(String name, DataType type, Consumer<VariableUpdate> listener) {
            subscribers.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>()).add(listener);
            return () -> subscribers.getOrDefault(name, List.of()).remove(listener);
        }

        @Override
        public void read(String name, DataType type, Consumer<VariableUpdate> once) {
            subscribe(name, type, once);
        }

        @Override
        public CompletableFuture<WriteResult> write(String name, DataType type, Object value) {
            writes.add(name);
            return CompletableFuture.completedFuture(WriteResult.ok(name));
        }

        @Override
        public void close() {
            status = LinkStatus.DISCONNECTED;
            statusListeners.forEach(listener -> listener.accept(status));
        }

        private void emit(String name, Object value) {
            for (Consumer<VariableUpdate> listener : subscribers.getOrDefault(name, List.of())) {
                listener.accept(VariableUpdate.good(name, value, Instant.now()));
            }
        }
    }
}
