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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.link.BatchContract;
import org.apache.plc4x.malbec.s88.batch.link.LinkStatus;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableCatalog;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.link.WriteResult;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchMode;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.PendingDecision;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;

/**
 * A batch client that sees the plant only through a {@link ProcessLink}.
 */
public final class LinkBatchClient implements BatchClient {

    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_PENDING_EVENTS = 10_000;

    private final ProcessLink link;
    private final Object lock = new Object();
    private final List<BatchUpdateListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Long, CompletableFuture<Ack>> acknowledgements = new ConcurrentHashMap<>();
    private final Map<String, AutoCloseable> subscriptions = new LinkedHashMap<>();
    private final Map<String, VariableSample> variables = new LinkedHashMap<>();
    private final ArrayDeque<BatchEvent> pendingEvents = new ArrayDeque<>();
    private final Set<String> downloadedSetpoints = new LinkedHashSet<>();
    private final AtomicLong commandSequence = new AtomicLong();
    private final AtomicReference<Instant> wallStart = new AtomicReference<>(Instant.now());

    private BatchPayload payload;
    private Map<String, VariableCatalog.Definition> catalog = Map.of();
    private Map<String, List<PreparedSetpoint>> setpoints = Map.of();
    private String batchId;
    private String stateChannel;
    private String runChannel;
    private String simTimeChannel;
    private String heartbeatChannel;
    private String activeStepsChannel;
    private String doneStepsChannel;
    private String firedTransitionsChannel;
    private String pendingDecisionChannel;
    private String commandChannel;
    private String commandAckChannel;

    private BatchSnapshot lastSnapshot;
    private ModuleState batchState = ModuleState.IDLE;
    private PendingDecision pendingDecision = PendingDecision.NONE;
    private Set<String> activeKeys = Set.of();
    private Set<String> doneKeys = Set.of();
    private Set<String> firedKeys = Set.of();
    private LinkStatus linkStatus = LinkStatus.DISCONNECTED;
    private boolean connected;
    private boolean linkWasLost;
    private boolean closed;
    private boolean connecting;
    private int run;
    private double simTime;
    private long eventSequence;
    private String lastAck = "";

    public LinkBatchClient(ProcessLink link) {
        this.link = Objects.requireNonNull(link, "link");
    }

    @Override
    public void load(BatchPayload newPayload) {
        Objects.requireNonNull(newPayload, "payload");
        synchronized (lock) {
            if (connecting) {
                throw new IllegalStateException("Disconnect before loading another batch.");
            }
            this.payload = newPayload;
            this.batchId = newPayload.batchId();
            this.catalog = VariableCatalog.of(newPayload);
            this.setpoints = newPayload.setpoints();
            this.stateChannel = BatchContract.state(batchId);
            this.runChannel = BatchContract.run(batchId);
            this.simTimeChannel = BatchContract.simTime(batchId);
            this.heartbeatChannel = BatchContract.heartbeat(batchId);
            this.activeStepsChannel = BatchContract.activeSteps(batchId);
            this.doneStepsChannel = BatchContract.doneSteps(batchId);
            this.firedTransitionsChannel = BatchContract.firedTransitions(batchId);
            this.pendingDecisionChannel = BatchContract.pendingDecision(batchId);
            this.commandChannel = BatchContract.command(batchId);
            this.commandAckChannel = BatchContract.commandAck(batchId);
            this.variables.clear();
            this.pendingEvents.clear();
            this.downloadedSetpoints.clear();
            this.acknowledgements.clear();
            this.batchState = ModuleState.IDLE;
            this.pendingDecision = PendingDecision.NONE;
            this.activeKeys = Set.of();
            this.doneKeys = Set.of();
            this.firedKeys = Set.of();
            this.linkStatus = LinkStatus.DISCONNECTED;
            this.connected = false;
            this.linkWasLost = false;
            this.run = 0;
            this.simTime = 0;
            this.eventSequence = 0;
            this.lastAck = "";
            this.wallStart.set(Instant.now());
            this.lastSnapshot = buildSnapshotLocked();
        }
    }

    @Override
    public void connect() {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("The client is closed.");
            }
            if (payload == null) {
                throw new IllegalStateException("Load a batch before connecting.");
            }
            if (connecting) {
                return;
            }
            connecting = true;
            link.addStatusListener(this::onStatus);
            for (Map.Entry<String, VariableCatalog.Definition> entry : catalog.entrySet()) {
                subscribe(entry.getKey(), entry.getValue().type());
            }
            subscribe(stateChannel, DataType.STRING);
            subscribe(runChannel, DataType.REAL);
            subscribe(simTimeChannel, DataType.REAL);
            subscribe(heartbeatChannel, DataType.REAL);
            subscribe(activeStepsChannel, DataType.STRING);
            subscribe(doneStepsChannel, DataType.STRING);
            subscribe(firedTransitionsChannel, DataType.STRING);
            subscribe(pendingDecisionChannel, DataType.STRING);
            subscribe(commandAckChannel, DataType.STRING);
            try {
                link.connect();
            } finally {
                connecting = false;
            }
        }
    }

    @Override
    public CommandResult sendCommand(BatchCommand command) {
        if (command == null) {
            return rejected("No command was given.");
        }
        ModuleState state = currentState();
        if (!connected) {
            return rejected("sin enlace");
        }
        long sequence = commandSequence.incrementAndGet();
        CompletableFuture<Ack> acknowledgement = new CompletableFuture<>();
        acknowledgements.put(sequence, acknowledgement);
        String text = sequence + ":" + command.name();
        try {
            WriteResult write = link.write(commandChannel, DataType.STRING, text)
                    .get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!write.success()) {
                acknowledgements.remove(sequence);
                return rejected(write.error() == null ? "sin enlace" : write.error());
            }
            Ack ack = acknowledgement.get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return new CommandResult(ack.accepted(), ack.reason(), ack.stateAfter());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            acknowledgements.remove(sequence);
            return rejected("sin respuesta del PLC");
        } catch (ExecutionException | TimeoutException timeout) {
            acknowledgements.remove(sequence);
            return rejected("sin respuesta del PLC");
        } catch (RuntimeException error) {
            acknowledgements.remove(sequence);
            return rejected(messageOf(error));
        }
    }

    @Override
    public void addListener(BatchUpdateListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        BatchSnapshot snapshot = lastSnapshot();
        if (snapshot != null) {
            listener.onSnapshot(snapshot);
        }
    }

    @Override
    public void removeListener(BatchUpdateListener listener) {
        listeners.remove(listener);
    }

    @Override
    public BatchSnapshot lastSnapshot() {
        synchronized (lock) {
            return lastSnapshot;
        }
    }

    @Override
    public void disconnect() {
        List<AutoCloseable> handles;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            connecting = false;
            connected = false;
            linkStatus = LinkStatus.DISCONNECTED;
            handles = List.copyOf(subscriptions.values());
            subscriptions.clear();
            acknowledgements.forEach((sequence, future) ->
                    future.complete(new Ack(false, "sin enlace", batchState)));
            acknowledgements.clear();
        }
        for (AutoCloseable handle : handles) {
            closeQuietly(handle);
        }
        link.close();
    }

    private void subscribe(String name, DataType type) {
        AutoCloseable handle = link.subscribe(name, type, update -> onUpdate(name, type, update));
        subscriptions.put(name, handle);
    }

    private void onStatus(LinkStatus status) {
        BatchSnapshot snapshot = null;
        synchronized (lock) {
            if (closed) {
                return;
            }
            LinkStatus previous = linkStatus;
            linkStatus = status;
            boolean nowConnected = status == LinkStatus.CONNECTED;
            connected = nowConnected;
            boolean dirty = previous != status;
            if (previous == LinkStatus.CONNECTED && !nowConnected) {
                markVariablesStaleLocked();
                if (!linkWasLost) {
                    linkWasLost = true;
                    event(EventType.CONNECTION_LOST, "link", batchId, "DISCONNECTED", "CONNECTED",
                            status.name(), "The link to the plant was lost.");
                    dirty = true;
                }
            } else if (nowConnected && linkWasLost) {
                linkWasLost = false;
                event(EventType.CONNECTION_RESTORED, "link", batchId, "CONNECTED", "DISCONNECTED",
                        "CONNECTED", "The link to the plant was restored.");
                dirty = true;
            }
            if (dirty) {
                snapshot = buildSnapshotLocked();
            }
        }
        if (snapshot != null) {
            notifyListeners(snapshot);
        }
    }

    private void onUpdate(String name, DataType type, VariableUpdate update) {
        BatchSnapshot snapshot = null;
        synchronized (lock) {
            if (closed) {
                return;
            }
            VariableSample previous = variables.get(name);
            Quality quality = update.quality();
            if (!connected && quality == Quality.BAD && previous != null && previous.value() != null) {
                quality = Quality.STALE;
            }
            VariableSample sample = new VariableSample(name, update.value(), type, unitOf(name),
                    update.sourceTime(), quality, "plc");
            variables.put(name, sample);
            boolean dirty = false;
            if (name.equals(heartbeatChannel) || name.equals(simTimeChannel)) {
                dirty = true;
                if (name.equals(simTimeChannel) && update.value() instanceof Number number) {
                    simTime = number.doubleValue();
                }
            }
            if (name.equals(stateChannel)) {
                dirty |= applyBatchState(text(update.value()));
            }
            if (name.equals(runChannel)) {
                dirty |= applyRun(update.value());
            }
            if (name.equals(activeStepsChannel)) {
                dirty |= applyActiveSteps(set(update.value()));
            }
            if (name.equals(doneStepsChannel)) {
                dirty |= applyDoneSteps(set(update.value()));
            }
            if (name.equals(firedTransitionsChannel)) {
                dirty |= applyFiredTransitions(set(update.value()));
            }
            if (name.equals(pendingDecisionChannel)) {
                dirty |= applyPendingDecision(PendingDecision.parse(text(update.value())));
            }
            if (name.equals(commandAckChannel)) {
                dirty |= applyAcknowledgement(text(update.value()));
            }
            if (name.startsWith("STATE_") && previous != null
                    && !Objects.equals(previous.value(), sample.value())) {
                event(EventType.STATE_CHANGED, "module", name.substring("STATE_".length()),
                        text(sample.value()), text(previous.value()), text(sample.value()),
                        "The module changed state.");
                dirty = true;
            }
            if (dirty) {
                snapshot = buildSnapshotLocked();
            }
        }
        if (snapshot != null) {
            notifyListeners(snapshot);
        }
    }

    private boolean applyBatchState(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        ModuleState next;
        try {
            next = ModuleState.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return false;
        }
        if (next == batchState) {
            return false;
        }
        ModuleState old = batchState;
        batchState = next;
        event(EventType.STATE_CHANGED, "batch", batchId, next.name(), old.name(), next.name(),
                "The batch changed state.");
        switch (next) {
            case RUNNING -> {
                if (old == ModuleState.IDLE) {
                    event(EventType.BATCH_STARTED, "batch", batchId, "RUNNING", old.name(),
                            "RUNNING", "The batch started.");
                }
            }
            case COMPLETE -> event(EventType.BATCH_COMPLETED, "batch", batchId, "COMPLETE",
                    old.name(), "COMPLETE", "The batch completed.");
            case ABORTED -> event(EventType.BATCH_ABORTED, "batch", batchId, "ABORTED",
                    old.name(), "ABORTED", "The batch aborted.");
            case STOPPED -> event(EventType.BATCH_STOPPED, "batch", batchId, "STOPPED",
                    old.name(), "STOPPED", "The batch stopped.");
            default -> {
            }
        }
        return true;
    }

    private boolean applyRun(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        int next = number.intValue();
        if (next == run) {
            return false;
        }
        int old = run;
        run = next;
        if (next > old) {
            activeKeys = Set.of();
            doneKeys = Set.of();
            firedKeys = Set.of();
            downloadedSetpoints.clear();
            event(EventType.BATCH_RESET, "batch", batchId, "IDLE", Integer.toString(old),
                    Integer.toString(next), "A new run started.");
            return true;
        }
        return false;
    }

    private boolean applyActiveSteps(Set<String> next) {
        if (next.equals(activeKeys)) {
            return false;
        }
        Set<String> added = new LinkedHashSet<>(next);
        added.removeAll(activeKeys);
        activeKeys = next;
        for (String key : added) {
            event(EventType.STEP_ACTIVATED, "sequence", key, "RUNNING", null, key,
                    "The step was activated.");
            downloadSetpoints(key);
        }
        return true;
    }

    private boolean applyDoneSteps(Set<String> next) {
        if (next.equals(doneKeys)) {
            return false;
        }
        Set<String> added = new LinkedHashSet<>(next);
        added.removeAll(doneKeys);
        doneKeys = next;
        for (String key : added) {
            event(EventType.STEP_COMPLETED, "sequence", key, "COMPLETE", null, key,
                    "The step completed.");
        }
        return true;
    }

    private boolean applyFiredTransitions(Set<String> next) {
        if (next.equals(firedKeys)) {
            return false;
        }
        Set<String> added = new LinkedHashSet<>(next);
        added.removeAll(firedKeys);
        firedKeys = next;
        for (String key : added) {
            event(EventType.TRANSITION_FIRED, "sequence", key, "RUNNING", null, key,
                    "The transition fired.");
        }
        return true;
    }

    private boolean applyPendingDecision(PendingDecision next) {
        if (next.equals(pendingDecision)) {
            return false;
        }
        PendingDecision old = pendingDecision;
        pendingDecision = next;
        if (!next.isEmpty()) {
            event(EventType.DECISION_NEEDED, "sequence", batchId, "RUNNING", old.text(),
                    next.text(), "An operator decision is needed.");
        } else if (!old.isEmpty()) {
            event(EventType.DECISION_TAKEN, "operator", batchId, "RUNNING", old.text(), "",
                    "The operator decision was taken.");
        }
        return true;
    }

    private boolean applyAcknowledgement(String text) {
        if (text == null || text.isBlank() || text.equals(lastAck)) {
            return false;
        }
        lastAck = text;
        int first = text.indexOf(':');
        if (first <= 0) {
            return false;
        }
        long sequence;
        try {
            sequence = Long.parseLong(text.substring(0, first));
        } catch (NumberFormatException notASequence) {
            return false;
        }
        String rest = text.substring(first + 1);
        boolean accepted = rest.startsWith("ACCEPTED");
        String reason = null;
        ModuleState stateAfter = batchState;
        if (accepted) {
            int second = rest.indexOf(':');
            if (second >= 0) {
                String stateText = rest.substring(second + 1);
                try {
                    stateAfter = ModuleState.valueOf(stateText);
                } catch (IllegalArgumentException unknown) {
                    stateAfter = batchState;
                }
            }
        } else if (rest.startsWith("REJECTED:")) {
            reason = rest.substring("REJECTED:".length());
        }
        CompletableFuture<Ack> future = acknowledgements.remove(sequence);
        if (future != null) {
            future.complete(new Ack(accepted, reason, stateAfter));
        }
        event(accepted ? EventType.COMMAND_ACCEPTED : EventType.COMMAND_REJECTED, "operator",
                batchId, stateAfter.name(), null, accepted ? "ACCEPTED" : "REJECTED",
                accepted ? "The command was accepted." : reason);
        return true;
    }

    private void downloadSetpoints(String stepKey) {
        List<PreparedSetpoint> values = setpoints.get(stepKey);
        if (values == null || values.isEmpty() || !downloadedSetpoints.add(stepKey)) {
            return;
        }
        for (PreparedSetpoint setpoint : values) {
            Object value;
            try {
                value = literal(setpoint);
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            link.write(setpoint.variable(), setpoint.dataType(), value)
                    .thenAccept(result -> {
                        if (result.success()) {
                            recordEvent(EventType.PARAMETER_DOWNLOADED, "sequence",
                                    setpoint.moduleId(), null, null, setpoint.value(),
                                    "The setpoint " + setpoint.variable() + " was downloaded.");
                        }
                    });
        }
    }

    private void recordEvent(EventType type, String source, String elementId, String state,
                             String oldValue, String newValue, String details) {
        BatchSnapshot snapshot;
        synchronized (lock) {
            if (closed) {
                return;
            }
            event(type, source, elementId, state, oldValue, newValue, details);
            snapshot = buildSnapshotLocked();
        }
        notifyListeners(snapshot);
    }

    private void event(EventType type, String source, String elementId, String state,
                       String oldValue, String newValue, String details) {
        BatchEvent event = new BatchEvent(eventSequence++, batchId, run, Instant.now(), simTime,
                type, source, elementId, state, oldValue, newValue, details);
        pendingEvents.addLast(event);
        while (pendingEvents.size() > MAX_PENDING_EVENTS) {
            pendingEvents.removeFirst();
        }
    }

    private BatchSnapshot buildSnapshotLocked() {
        List<BatchEvent> events = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        List<StepStatus> steps = new ArrayList<>();
        for (String key : activeKeys) {
            steps.add(stepStatus(key));
        }
        double wallElapsed = Math.max(0,
                Duration.between(wallStart.get(), Instant.now()).toMillis() / 1000.0);
        lastSnapshot = new BatchSnapshot(batchId, run, BatchMode.SIMULATED, batchState,
                connected, simTime, wallElapsed, steps, doneKeys, firedKeys,
                new LinkedHashMap<>(variables), events, pendingDecision);
        return lastSnapshot;
    }

    private StepStatus stepStatus(String key) {
        int slash = key.lastIndexOf('/');
        String stepId = slash < 0 ? key : key.substring(slash + 1);
        String chartPath = slash < 0 ? "" : key.substring(0, slash);
        String moduleId = payload == null ? null : payload.moduleOfStep().get(key);
        if (moduleId == null && payload != null) {
            moduleId = payload.unitOfStep().get(key);
        }
        return new StepStatus(chartPath, stepId, moduleId, moduleId, ModuleState.RUNNING, 0);
    }

    private void markVariablesStaleLocked() {
        List<String> names = new ArrayList<>(variables.keySet());
        for (String name : names) {
            VariableSample sample = variables.get(name);
            if (sample.value() != null) {
                variables.put(name, new VariableSample(sample.name(), sample.value(),
                        sample.type(), sample.unit(), sample.sourceTime(), Quality.STALE,
                        sample.origin()));
            }
        }
    }

    private void notifyListeners(BatchSnapshot snapshot) {
        for (BatchUpdateListener listener : listeners) {
            try {
                listener.onSnapshot(snapshot);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private CommandResult rejected(String reason) {
        ModuleState state = currentState();
        recordEvent(EventType.COMMAND_REJECTED, "operator", batchId, state.name(), null, null,
                reason);
        return new CommandResult(false, reason, state);
    }

    private ModuleState currentState() {
        synchronized (lock) {
            return batchState;
        }
    }

    private String unitOf(String name) {
        VariableCatalog.Definition definition = catalog.get(name);
        return definition == null ? null : definition.unit();
    }

    private static Object literal(PreparedSetpoint setpoint) {
        return switch (setpoint.dataType()) {
            case INTEGER, REAL -> Double.valueOf(setpoint.value());
            case ENUMERATION, STRING -> setpoint.value();
        };
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Set<String> set(Object value) {
        if (value == null) {
            return Set.of();
        }
        String text = value.toString();
        if (text.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String item : text.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String messageOf(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private record Ack(boolean accepted, String reason, ModuleState stateAfter) {
    }
}
