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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayloadFactory;
import org.apache.plc4x.malbec.s88.batch.builder.BatchRequest;
import org.apache.plc4x.malbec.s88.batch.builder.PrepareResult;
import org.apache.plc4x.malbec.s88.batch.client.BatchClient;
import org.apache.plc4x.malbec.s88.batch.client.BatchUpdateListener;
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
import org.apache.plc4x.malbec.s88.batch.runtime.report.ValuesRecorder;

/**
 * Runs one batch through a client and records its events, values and report.
 */
public final class BatchRuntimeService implements AutoCloseable {

    private final BatchClient client;
    private final Path outputRoot;
    private final Object lock = new Object();
    private final BatchUpdateListener listener = this::onSnapshot;

    private BatchPayload payload;
    private RunFolder folder;
    private BatchEventRecorder events;
    private ValuesRecorder values;
    private BatchRunSummary lastSummary;
    private RuntimeException failure;
    private int currentRun;
    private Instant startWallTime;
    private Instant endWallTime;
    private double startSimTime;
    private double endSimTime;
    private ModuleState batchState = ModuleState.IDLE;
    private boolean started;
    private boolean closed;

    public BatchRuntimeService(BatchClient client, Path outputRoot) {
        this(client, outputRoot, null);
    }

    /**
     * @param loadedPayload payload the client already loaded, or {@code null} to load one later
     */
    public BatchRuntimeService(BatchClient client, Path outputRoot, BatchPayload loadedPayload) {
        this.client = Objects.requireNonNull(client, "client");
        this.outputRoot = Objects.requireNonNull(outputRoot, "outputRoot");
        this.payload = loadedPayload;
    }

    /** Prepares the request and loads its payload into the client. */
    public PrepareResult load(BatchRequest request) {
        synchronized (lock) {
            ensureOpen();
            if (started) {
                throw new IllegalStateException("A started runtime cannot load another batch.");
            }
            if (payload != null) {
                throw new IllegalStateException("The runtime already has a batch.");
            }
            PrepareResult result = BatchPayloadFactory.prepare(request);
            if (!result.hasErrors()) {
                payload = result.payload();
                client.load(payload);
            }
            return result;
        }
    }

    /** Starts recording and connects the client. */
    public void start() throws IOException {
        synchronized (lock) {
            ensureOpen();
            if (started) {
                return;
            }
            if (payload == null) {
                throw new IllegalStateException("Load a batch before starting the runtime.");
            }
            started = true;
            BatchSnapshot snapshot = client.lastSnapshot();
            int run = snapshot != null && snapshot.run() > 0 ? snapshot.run() : 1;
            ensureRunLocked(run);
            if (snapshot != null) {
                startSimTime = snapshot.simTime();
                batchState = snapshot.batchState();
            }
            client.addListener(listener);
            client.connect();
        }
    }

    public CommandResult sendCommand(BatchCommand command) {
        return client.sendCommand(command);
    }

    public BatchSnapshot lastSnapshot() {
        return client.lastSnapshot();
    }

    public void addListener(BatchUpdateListener updateListener) {
        client.addListener(updateListener);
    }

    public void removeListener(BatchUpdateListener updateListener) {
        client.removeListener(updateListener);
    }

    public RunFolder currentRunFolder() {
        synchronized (lock) {
            return folder;
        }
    }

    public BatchRunSummary lastSummary() {
        synchronized (lock) {
            return lastSummary;
        }
    }

    /** @return how many events have been written for the current run */
    public int recordedEventCount() {
        synchronized (lock) {
            return events == null ? 0 : events.events().size();
        }
    }

    /** @return how many value samples have been written for the current run */
    public int recordedValueCount() {
        synchronized (lock) {
            return values == null ? 0 : values.samples().size();
        }
    }

    public RuntimeException failure() {
        synchronized (lock) {
            return failure;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            started = false;
            try {
                if (folder != null && lastSummary == null) {
                    finalizeRunLocked("INTERRUPTED");
                } else if (folder != null) {
                    refreshRunLocked(lastSummary.outcome());
                }
            } catch (IOException error) {
                failure = new UncheckedIOException(error);
            }
            closeRecordersLocked();
            folder = null;
        }
        client.removeListener(listener);
        client.disconnect();
    }

    private void onSnapshot(BatchSnapshot snapshot) {
        synchronized (lock) {
            if (!started || closed || snapshot == null) {
                return;
            }
            try {
                int snapshotRun = snapshot.run() > 0 ? snapshot.run() : Math.max(currentRun, 1);
                if (snapshotRun < currentRun) {
                    return;
                }
                for (BatchEvent event : snapshot.newEvents()) {
                    int eventRun = event.run() > 0 ? event.run() : snapshotRun;
                    if (eventRun < currentRun) {
                        continue;
                    }
                    ensureRunLocked(eventRun);
                    events.record(event);
                    if (event.type() == EventType.BATCH_STARTED) {
                        startWallTime = event.wallTime();
                        startSimTime = event.simTime();
                    }
                }
                ensureRunLocked(snapshotRun);
                values.record(snapshot);
                endWallTime = Instant.now();
                endSimTime = snapshot.simTime();
                batchState = snapshot.batchState();
                if (batchState.kind() == ModuleState.Kind.FINAL) {
                    if (lastSummary == null) {
                        finalizeRunLocked(batchState.name());
                    } else {
                        refreshRunLocked(batchState.name());
                    }
                }
            } catch (IOException error) {
                failure = new UncheckedIOException(error);
            }
        }
    }

    private void ensureRunLocked(int run) throws IOException {
        if (folder != null && currentRun == run) {
            return;
        }
        if (folder != null && run < currentRun) {
            return;
        }
        if (folder != null) {
            finalizeRunLocked("INTERRUPTED");
            closeRecordersLocked();
        }
        folder = RunFolder.create(outputRoot, payload.batchId(), run);
        currentRun = run;
        lastSummary = null;
        events = new BatchEventRecorder(folder);
        values = new ValuesRecorder(folder, payload, null);
        ReportWriter.writeRoute(folder, payload);
        startWallTime = Instant.now();
        endWallTime = startWallTime;
        startSimTime = 0;
        endSimTime = 0;
        batchState = ModuleState.IDLE;
    }

    private void finalizeRunLocked(String outcome) throws IOException {
        if (folder == null || lastSummary != null) {
            return;
        }
        writeSummaryLocked(outcome);
    }

    private void refreshRunLocked(String outcome) throws IOException {
        if (folder == null || lastSummary == null) {
            return;
        }
        if (lastSummary.events().size() == events.events().size()
                && lastSummary.values().size() == values.samples().size()) {
            return;
        }
        writeSummaryLocked(outcome);
    }

    private void writeSummaryLocked(String outcome) throws IOException {
        BatchRunSummary summary = new BatchRunSummary(payload, currentRun, outcome,
                startWallTime, endWallTime, startSimTime, endSimTime,
                events.events(), values.samples());
        ReportWriter.write(folder, summary);
        lastSummary = summary;
    }

    private void closeRecordersLocked() {
        if (events != null) {
            try {
                events.close();
            } catch (IOException ignored) {
            }
            events = null;
        }
        if (values != null) {
            try {
                values.close();
            } catch (IOException ignored) {
            }
            values = null;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("The runtime is closed.");
        }
    }
}
