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

import java.nio.file.Path;
import java.time.Duration;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayloadFactory;
import org.apache.plc4x.malbec.s88.batch.builder.PrepareResult;
import org.apache.plc4x.malbec.s88.batch.client.SessionOptions;
import org.apache.plc4x.malbec.s88.batch.client.sim.SimulatedSession;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;

/** Runs the demo batch without a user interface and prints the report path. */
public final class DemoMain {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private DemoMain() {
    }

    public static void main(String[] args) throws Exception {
        PrepareResult prepared = BatchPayloadFactory.prepare(DemoFixtures.request("BATCH-DEMO"));
        if (prepared.hasErrors()) {
            throw new IllegalStateException(prepared.errors().toString());
        }
        BatchPayload payload = prepared.payload();
        Path output = Path.of("target", "batch-output");
        try (SimulatedSession session = SimulatedSession.create(payload,
                SessionOptions.defaults().withSpeed(600));
                BatchRuntimeService runtime = new BatchRuntimeService(session.client(), output,
                        payload)) {
            runtime.start();
            await(() -> session.client().lastSnapshot() != null
                    && session.client().lastSnapshot().connected());
            if (!runtime.sendCommand(BatchCommand.START).accepted()) {
                throw new IllegalStateException("The plant rejected START.");
            }
            await(() -> session.client().lastSnapshot().batchState() == ModuleState.COMPLETE);
            System.out.println(runtime.currentRunFolder().reportFile().toAbsolutePath());
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("The demo batch did not finish in time.");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }
}
