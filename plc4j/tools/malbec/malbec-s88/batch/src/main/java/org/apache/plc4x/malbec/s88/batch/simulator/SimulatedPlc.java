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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.client.SessionOptions;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.batch.simulator.steps.ExitPolicy;
import org.apache.plc4x.malbec.s88.batch.simulator.steps.SimulationEngine;

/**
 * The simulated programmable controller. One thread owns the engine and the bridge; every other
 * caller asks that thread to act.
 */
public final class SimulatedPlc implements AutoCloseable {

    private final SimulationEngine engine;
    private final PlcChannelBridge bridge;
    private final ScheduledExecutorService executor;
    private final Duration tickInterval;
    private final long stepPauseMillis;

    private volatile double speed;
    private volatile boolean clockRunning;
    private volatile boolean closed;
    private volatile RuntimeException failure;

    public SimulatedPlc(BatchPayload payload, SimulationProfile profile, ProcessLink link,
                        SessionOptions options, DecisionMode decisionMode, ExitPolicy exitPolicy) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(link, "link");
        this.engine = new SimulationEngine(payload, profile, java.time.Clock.systemUTC(),
                decisionMode, exitPolicy);
        this.bridge = new PlcChannelBridge(engine, link, payload);
        this.tickInterval = options.tickInterval();
        this.speed = options.speed();
        this.clockRunning = options.clockRunning();
        this.stepPauseMillis = Math.max(1, options.maxRate().toMillis() + 5);
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "malbec-sim");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        execute(() -> {
            bridge.start();
            long period = tickInterval.toMillis();
            executor.scheduleAtFixedRate(this::tickSafely, period, period, TimeUnit.MILLISECONDS);
        });
    }

    public void setSpeed(double factor) {
        if (factor < 0.5 || factor > 600.0) {
            throw new IllegalArgumentException("Speed has to be between 0.5 and 600.");
        }
        speed = factor;
    }

    public void play() {
        clockRunning = true;
    }

    public void pauseClock() {
        clockRunning = false;
    }

    public boolean isClockRunning() {
        return clockRunning;
    }

    public double speed() {
        return speed;
    }

    public void stepTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("The tick count cannot be negative.");
        }
        clockRunning = false;
        runAndWait(() -> {
            for (int tick = 0; tick < ticks; tick++) {
                bridge.applyInputs();
                engine.tick(engine.tickSeconds());
                bridge.publish(engine.snapshot(), false);
                sleepQuietly(stepPauseMillis);
            }
        });
    }

    public void stepToNextEvent() {
        clockRunning = false;
        runAndWait(() -> {
            String before = fingerprint(engine.snapshot());
            for (int tick = 0; tick < 10_000; tick++) {
                bridge.applyInputs();
                engine.tick(engine.tickSeconds());
                BatchSnapshot snapshot = engine.snapshot();
                bridge.publish(snapshot, false);
                if (!before.equals(fingerprint(snapshot))) {
                    return;
                }
            }
        });
    }

    public void setVariable(String publishedName, Object value) {
        execute(() -> {
            engine.setVariable(publishedName, value);
            bridge.publish(engine.snapshot(), false);
        });
    }

    public void injectFailure(String moduleId, int code) {
        execute(() -> {
            engine.injectFailure(moduleId, code);
            bridge.publish(engine.snapshot(), false);
        });
    }

    public void clearFailure(String moduleId) {
        execute(() -> {
            engine.clearFailure(moduleId);
            bridge.publish(engine.snapshot(), false);
        });
    }

    public void setDecisionMode(DecisionMode mode) {
        execute(() -> engine.setDecisionMode(mode));
    }

    public void decide(String transitionId) {
        execute(() -> engine.decide(transitionId));
    }

    public RuntimeException failure() {
        return failure;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        bridge.close();
    }

    private void tickSafely() {
        if (closed) {
            return;
        }
        try {
            boolean input = bridge.applyInputs();
            boolean ticked = false;
            if (clockRunning) {
                engine.tick(engine.tickSeconds() * speed);
                ticked = true;
            }
            if (ticked || input) {
                bridge.publish(engine.snapshot(), false);
            } else {
                bridge.publishKeepAlive(engine.snapshot());
            }
        } catch (RuntimeException error) {
            failure = error;
            clockRunning = false;
        }
    }

    private void execute(Runnable action) {
        if (closed) {
            throw new IllegalStateException("The simulated plant is closed.");
        }
        executor.execute(() -> {
            if (!closed) {
                action.run();
            }
        });
    }

    private void runAndWait(Runnable action) {
        if (closed) {
            throw new IllegalStateException("The simulated plant is closed.");
        }
        try {
            ExecutorService caller = executor;
            caller.submit(action).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stepping the plant.", interrupted);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (TimeoutException timeout) {
            throw new IllegalStateException("The simulated plant did not finish the step.", timeout);
        }
    }

    private static String fingerprint(BatchSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append(snapshot.batchState()).append('|').append(snapshot.pendingDecision().text())
                .append('|').append(snapshot.activeSteps()).append('|')
                .append(snapshot.completedStepKeys()).append('|')
                .append(snapshot.firedTransitionKeys()).append('|');
        snapshot.variables().forEach((name, sample) -> {
            if (name.startsWith("STATE_") || name.startsWith("FAILURE_")) {
                text.append(name).append('=').append(sample.value()).append(';');
            }
        });
        return text.toString();
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
