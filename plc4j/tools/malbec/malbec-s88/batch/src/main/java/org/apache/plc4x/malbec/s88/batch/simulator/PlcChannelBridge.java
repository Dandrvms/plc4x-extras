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
package org.apache.plc4x.malbec.s88.batch.simulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.link.BatchContract;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableCatalog;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.simulator.steps.SimulationEngine;
import org.apache.plc4x.malbec.s88.batch.simulator.values.ProcessVariable;

/**
 * Connects the simulated plant to the process channels. It writes the plant values and the batch
 * variables, and queues the writes that come from the other side of the link.
 */
public final class PlcChannelBridge implements AutoCloseable {

    private static final long REFRESH_MILLIS = 500;

    private final SimulationEngine engine;
    private final ProcessLink link;
    private final String batchId;
    private final Map<String, VariableCatalog.Definition> parameters;
    private final Map<String, Object> lastPublished = new LinkedHashMap<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private final Set<String> pendingCommands = new LinkedHashSet<>();
    private final ConcurrentLinkedQueue<Input> inputs = new ConcurrentLinkedQueue<>();

    private volatile String lastCommandAck = "";
    private long heartbeat;
    private long lastRefreshNanos;
    private boolean started;

    public PlcChannelBridge(SimulationEngine engine, ProcessLink link, BatchPayload payload) {
        this.engine = engine;
        this.link = link;
        this.batchId = payload.batchId();
        this.parameters = VariableCatalog.parametersOf(payload);
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        link.connect();
        for (Map.Entry<String, VariableCatalog.Definition> entry : parameters.entrySet()) {
            subscribe(entry.getKey(), entry.getValue().type());
        }
        subscribe(BatchContract.command(batchId), DataType.STRING);
        subscribe(BatchContract.decision(batchId), DataType.STRING);
        publish(engine.snapshot(), true);
    }

    /**
     * Applies the writes received since the last call; it runs on the plant thread.
     *
     * @return {@code true} when at least one write was applied
     */
    public boolean applyInputs() {
        boolean changed = false;
        Input input;
        while ((input = inputs.poll()) != null) {
            changed = true;
            if (BatchContract.command(batchId).equals(input.name())) {
                applyCommand(String.valueOf(input.value()));
            } else if (BatchContract.decision(batchId).equals(input.name())) {
                engine.decide(String.valueOf(input.value()));
            } else {
                try {
                    engine.setVariable(input.name(), input.value());
                } catch (IllegalArgumentException invalid) {
                    // A value the plant cannot hold is refused; the writer already saw the channel accept it.
                }
            }
        }
        return changed;
    }

    /** Writes the values that changed since the last publish, and all of them now and then. */
    public void publish(BatchSnapshot snapshot, boolean force) {
        long now = System.nanoTime();
        boolean refresh = force || now - lastRefreshNanos >= REFRESH_MILLIS * 1_000_000L;
        if (refresh) {
            lastRefreshNanos = now;
        }
        for (ProcessVariable variable : engine.snapshotVariables().values()) {
            write(variable.name(), variable.type(), variable.value(), refresh);
        }
        write(BatchContract.state(batchId), DataType.STRING, snapshot.batchState().name(), true);
        write(BatchContract.run(batchId), DataType.REAL, (double) snapshot.run(), true);
        write(BatchContract.simTime(batchId), DataType.REAL, snapshot.simTime(), true);
        write(BatchContract.heartbeat(batchId), DataType.REAL, (double) ++heartbeat, true);
        write(BatchContract.activeSteps(batchId), DataType.STRING, activeStepKeys(snapshot), true);
        write(BatchContract.doneSteps(batchId), DataType.STRING,
                join(snapshot.completedStepKeys()), true);
        write(BatchContract.firedTransitions(batchId), DataType.STRING,
                join(snapshot.firedTransitionKeys()), true);
        write(BatchContract.pendingDecision(batchId), DataType.STRING,
                snapshot.pendingDecision().text(), true);
        write(BatchContract.commandAck(batchId), DataType.STRING, lastCommandAck, true);
    }

    @Override
    public void close() {
        for (AutoCloseable subscription : subscriptions) {
            closeQuietly(subscription);
        }
        subscriptions.clear();
        link.close();
    }

    private void subscribe(String name, DataType type) {
        subscriptions.add(link.subscribe(name, type, this::enqueue));
    }

    private void enqueue(VariableUpdate update) {
        if (update.quality() == Quality.GOOD && update.value() != null) {
            inputs.add(new Input(update.name(), update.value()));
        }
    }

    private void applyCommand(String text) {
        int colon = text.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String seq = text.substring(0, colon);
        String commandText = text.substring(colon + 1);
        if (!pendingCommands.add(seq)) {
            return;
        }
        CommandResult result;
        try {
            result = engine.command(BatchCommand.valueOf(commandText));
        } catch (IllegalArgumentException unknown) {
            result = new CommandResult(false, "Unknown command '" + commandText + "'. ",
                    engine.batchState());
        }
        lastCommandAck = seq + ":" + (result.accepted() ? "ACCEPTED:" + result.stateAfter()
                : "REJECTED:" + result.reason());
        link.write(BatchContract.commandAck(batchId), DataType.STRING, lastCommandAck);
        if (pendingCommands.size() > 1000) {
            pendingCommands.clear();
            pendingCommands.add(seq);
        }
    }

    private void write(String name, DataType type, Object value, boolean force) {
        if (value == null) {
            return;
        }
        if (!force && java.util.Objects.equals(lastPublished.get(name), value)) {
            return;
        }
        lastPublished.put(name, value);
        link.write(name, type, value);
    }

    private static String activeStepKeys(BatchSnapshot snapshot) {
        Set<String> keys = new LinkedHashSet<>();
        for (StepStatus step : snapshot.activeSteps()) {
            keys.add((step.chartPath() == null ? "" : step.chartPath()) + "/" + step.stepId());
        }
        return join(keys);
    }

    private static String join(Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private record Input(String name, Object value) {
    }
}
