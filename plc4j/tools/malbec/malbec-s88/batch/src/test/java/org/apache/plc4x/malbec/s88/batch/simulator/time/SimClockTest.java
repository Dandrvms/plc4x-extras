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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimClockTest {

    @Test
    void advancesSimulatedAndWallTime() {
        SimClock clock = new SimClock(2.0);

        clock.advance(1.5);
        assertEquals(1.5, clock.now(), 1e-9);

        double advanced = clock.advanceWall(2.0);
        assertEquals(4.0, advanced, 1e-9);
        assertEquals(5.5, clock.now(), 1e-9);

        clock.reset();
        assertEquals(0.0, clock.now(), 1e-9);
    }

    @Test
    void rejectsInvalidSpeedAndBackwardsTime() {
        SimClock clock = new SimClock();

        assertThrows(IllegalArgumentException.class, () -> clock.setSpeed(0.1));
        assertThrows(IllegalArgumentException.class, () -> clock.setSpeed(601.0));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1.0));
        assertThrows(IllegalArgumentException.class, () -> clock.advanceWall(-1.0));
    }
}
