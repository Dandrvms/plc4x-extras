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

import java.util.Arrays;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.PlatformEnumerations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleStateMirrorTest {

    @Test
    void statesMatchPlatformEnumeration() {
        assertEquals(PlatformEnumerations.valuesOf(PlatformEnumerations.STATE),
                Arrays.stream(ModuleState.values()).map(Enum::name).toList());
    }

    @Test
    void commandsMatchPlatformEnumerationWithoutNone() {
        List<String> expected = PlatformEnumerations.valuesOf(PlatformEnumerations.COMMAND).stream()
                .filter(value -> !"NONE".equals(value))
                .toList();

        assertEquals(expected, Arrays.stream(BatchCommand.values()).map(Enum::name).toList());
    }

    @Test
    void kindsFollowTheIsa88Diagram() {
        assertEquals(ModuleState.Kind.INITIAL, ModuleState.IDLE.kind());
        assertEquals(ModuleState.Kind.ACTIVE, ModuleState.RUNNING.kind());
        for (ModuleState state : List.of(ModuleState.PAUSING, ModuleState.HOLDING,
                ModuleState.RESTARTING, ModuleState.ABORTING, ModuleState.STOPPING)) {
            assertEquals(ModuleState.Kind.TRANSIENT, state.kind(), state.name());
        }
        for (ModuleState state : List.of(ModuleState.PAUSED, ModuleState.HELD)) {
            assertEquals(ModuleState.Kind.QUIESCENT, state.kind(), state.name());
        }
        for (ModuleState state : List.of(ModuleState.COMPLETE, ModuleState.ABORTED,
                ModuleState.STOPPED)) {
            assertEquals(ModuleState.Kind.FINAL, state.kind(), state.name());
        }
    }

    @Test
    void onlyIdleAndFinalStatesAreInactive() {
        assertFalse(ModuleState.IDLE.isActive());
        for (ModuleState state : List.of(ModuleState.COMPLETE, ModuleState.ABORTED,
                ModuleState.STOPPED)) {
            assertFalse(state.isActive(), state.name());
        }
        for (ModuleState state : List.of(ModuleState.RUNNING, ModuleState.PAUSING,
                ModuleState.PAUSED, ModuleState.HOLDING, ModuleState.HELD, ModuleState.ABORTING,
                ModuleState.STOPPING, ModuleState.RESTARTING)) {
            assertTrue(state.isActive(), state.name());
        }
    }
}
