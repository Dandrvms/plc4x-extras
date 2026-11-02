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

/**
 * Timing of a simulated session.
 *
 * @param speed             simulated seconds per wall second
 * @param tickInterval      wall time between ticks while the clock runs
 * @param maxRate           shortest wall time between link notifications
 * @param connectionTimeout how long a write waits for its writer
 * @param staleAfter        wall time without data before a value is stale
 * @param clockRunning      whether the clock starts running
 */
public record SessionOptions(double speed, Duration tickInterval, Duration maxRate,
                             Duration connectionTimeout, Duration staleAfter,
                             boolean clockRunning) {

    public SessionOptions {
        if (speed < 0.5 || speed > 600.0) {
            throw new IllegalArgumentException("Speed has to be between 0.5 and 600.");
        }
        tickInterval = positive(tickInterval, "tickInterval");
        maxRate = positive(maxRate, "maxRate");
        connectionTimeout = positive(connectionTimeout, "connectionTimeout");
        staleAfter = positive(staleAfter, "staleAfter");
    }

    public static SessionOptions defaults() {
        return new SessionOptions(1.0, Duration.ofMillis(100), Duration.ofMillis(20),
                Duration.ofSeconds(2), Duration.ofSeconds(2), true);
    }

    public SessionOptions withSpeed(double newSpeed) {
        return new SessionOptions(newSpeed, tickInterval, maxRate, connectionTimeout, staleAfter,
                clockRunning);
    }

    public SessionOptions withTickInterval(Duration interval) {
        return new SessionOptions(speed, interval, maxRate, connectionTimeout, staleAfter,
                clockRunning);
    }

    public SessionOptions withClockRunning(boolean running) {
        return new SessionOptions(speed, tickInterval, maxRate, connectionTimeout, staleAfter,
                running);
    }

    private static Duration positive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return duration;
    }
}
