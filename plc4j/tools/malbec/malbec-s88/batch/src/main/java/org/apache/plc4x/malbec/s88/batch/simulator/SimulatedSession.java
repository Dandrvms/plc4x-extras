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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.client.BatchClient;
import org.apache.plc4x.malbec.s88.batch.client.LinkBatchClient;


import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.gpclient.GpClientEndpoint;
import org.apache.plc4x.malbec.s88.batch.link.gpclient.GpClientLinkConfig;
import org.apache.plc4x.malbec.s88.batch.link.gpclient.GpClientProcessLink;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;

import org.apache.plc4x.malbec.s88.batch.simulator.steps.ExitPolicy;

/**
 * A simulated plant and a batch client joined by a real process link.
 */
public final class SimulatedSession implements AutoCloseable {

    private final GpClientEndpoint endpoint;
    private final GpClientLinkConfig linkConfig;
    private final ExecutorService linkExecutor;
    private final FaultInjectingProcessLink clientLink;
    private final SimulatedPlc plc;
    private final LinkBatchClient batchClient;
    private final SimulationControl control;

    private SimulatedSession(BatchPayload payload, SessionOptions options) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(options, "options");
        this.linkExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "malbec-link");
            thread.setDaemon(true);
            return thread;
        });
        this.linkConfig = new GpClientLinkConfig(GpClientLinkConfig.DEFAULT_PREFIX, linkExecutor,
                options.maxRate(), options.connectionTimeout(), options.staleAfter(),
                payload.batchId());
        this.endpoint = new GpClientEndpoint(linkConfig);
        GpClientProcessLink clientDelegate = endpoint.newLink();
        this.clientLink = new FaultInjectingProcessLink(clientDelegate);
        ProcessLink plcLink = endpoint.newLink();
        SimulationProfile profile = payload.profile() == null
                ? SimulationProfile.defaults() : payload.profile();
        this.plc = new SimulatedPlc(payload, profile, plcLink, options, DecisionMode.AUTO,
                ExitPolicy.COMPLETE_ON_EXIT);
        this.batchClient = new LinkBatchClient(clientLink);
        this.batchClient.load(payload);
        this.control = new Control();
        batchClient.connect();
        plc.start();
    }

    public static SimulatedSession create(BatchPayload payload) {
        return create(payload, SessionOptions.defaults());
    }

    public static SimulatedSession create(BatchPayload payload, SessionOptions options) {
        return new SimulatedSession(payload, options);
    }

    public BatchClient client() {
        return batchClient;
    }

    public SimulationControl control() {
        return control;
    }

    public BatchSnapshot snapshot() {
        return plc.snapshot();
    }

    public void addSnapshotListener(Consumer<BatchSnapshot> listener) {
        plc.addSnapshotListener(listener);
    }

    public void removeSnapshotListener(Consumer<BatchSnapshot> listener) {
        plc.removeSnapshotListener(listener);
    }

    @Override
    public void close() {
        batchClient.disconnect();
        plc.close();
        linkConfig.close();
        endpoint.close();
        linkExecutor.shutdownNow();
        try {
            linkExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Control implements SimulationControl {

        @Override
        public void setSpeed(double factor) {
            plc.setSpeed(factor);
        }

        @Override
        public void play() {
            plc.play();
        }

        @Override
        public void pauseClock() {
            plc.pauseClock();
        }

        @Override
        public void stepTicks(int ticks) {
            plc.stepTicks(ticks);
        }

        @Override
        public boolean stepToNextEvent() {
            return plc.stepToNextEvent();
        }

        @Override
        public boolean isClockRunning() {
            return plc.isClockRunning();
        }

        @Override
        public double speed() {
            return plc.speed();
        }

        @Override
        public void setAutomatic(boolean automatic) {
            plc.setAutomatic(automatic);
        }

        @Override
        public void completeStep(String moduleId) {
            plc.completePhase(moduleId);
        }

        @Override
        public void setVariable(String publishedName, Object value) {
            plc.setVariable(publishedName, value);
        }

        @Override
        public void injectFailure(String moduleId, int code) {
            plc.injectFailure(moduleId, code);
        }

        @Override
        public void clearFailure(String moduleId) {
            plc.clearFailure(moduleId);
        }

        @Override
        public void setLinkUp(boolean up) {
            clientLink.setUp(up);
        }

        @Override
        public void setDecisionMode(DecisionMode mode) {
            plc.setDecisionMode(mode);
        }

        @Override
        public void decide(String transitionId) {
            plc.decide(transitionId);
        }
    }
}
