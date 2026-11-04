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
package org.apache.plc4x.malbec.s88.batch.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUnitsTest {

    @Test
    void minutesAndHoursBecomeSeconds() {
        assertEquals(60.0, TimeUnits.toSeconds(1, "MIN"), 1e-9);
        assertEquals(3600.0, TimeUnits.toSeconds(1, "H"), 1e-9);
        assertEquals(60.0, TimeUnits.toSeconds(60, "s"), 1e-9);
    }

    @Test
    void secondsBecomeTheUnitThePlantUses() {
        assertEquals(1.0, TimeUnits.fromSeconds(60, "MIN"), 1e-9);
        assertEquals(1.0, TimeUnits.fromSeconds(3600, "H"), 1e-9);
    }

    @Test
    void aLiteralIsNormalizedOnlyBetweenTimeUnits() {
        assertEquals("1.0", TimeUnits.normalizeLiteral("60", "s", "MIN"));
        assertEquals("60", TimeUnits.normalizeLiteral("60", "C", "MIN"));
        assertEquals("60", TimeUnits.normalizeLiteral("60", "s", null));
    }
}
