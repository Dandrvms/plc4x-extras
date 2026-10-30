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
package org.apache.plc4x.malbec.s88.batch.simulator.time;

/** Simulated time and the factor that turns wall time into simulated time. */
public final class SimClock {

    private static final double MIN_SPEED = 0.5;
    private static final double MAX_SPEED = 600.0;

    private double simTime;
    private double speed;

    public SimClock() {
        this(1.0);
    }

    public SimClock(double speed) {
        setSpeed(speed);
    }

    public double now() {
        return simTime;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            throw new IllegalArgumentException("Speed has to be between " + MIN_SPEED
                    + " and " + MAX_SPEED + ".");
        }
        this.speed = speed;
    }

    public void advance(double seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Simulated time cannot go backwards.");
        }
        simTime += seconds;
    }

    public double advanceWall(double wallSeconds) {
        if (wallSeconds < 0) {
            throw new IllegalArgumentException("Wall time cannot go backwards.");
        }
        double advanced = wallSeconds * speed;
        advance(advanced);
        return advanced;
    }

    public void reset() {
        simTime = 0;
    }
}
