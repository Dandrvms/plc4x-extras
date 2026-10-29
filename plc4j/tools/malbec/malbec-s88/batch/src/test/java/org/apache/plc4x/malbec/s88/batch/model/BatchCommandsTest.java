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

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BatchCommandsTest {

    @Test
    void commandsFollowTheIsa88Table() {
        assertAllowed(ModuleState.IDLE, BatchCommand.START);
        assertAllowed(ModuleState.RUNNING, BatchCommand.PAUSE, BatchCommand.HOLD,
                BatchCommand.ABORT, BatchCommand.STOP);
        assertAllowed(ModuleState.PAUSED, BatchCommand.RESUME, BatchCommand.ABORT,
                BatchCommand.STOP);
        assertAllowed(ModuleState.HELD, BatchCommand.RESTART, BatchCommand.ABORT,
                BatchCommand.STOP);
        assertAllowed(ModuleState.COMPLETE, BatchCommand.RESET);
        assertAllowed(ModuleState.ABORTED, BatchCommand.RESET);
        assertAllowed(ModuleState.STOPPED, BatchCommand.RESET);
        assertAllowed(ModuleState.PAUSING, BatchCommand.ABORT, BatchCommand.STOP);
        assertAllowed(ModuleState.HOLDING, BatchCommand.ABORT, BatchCommand.STOP);
        assertAllowed(ModuleState.RESTARTING, BatchCommand.ABORT, BatchCommand.STOP);
        assertAllowed(ModuleState.STOPPING, BatchCommand.ABORT);
        assertAllowed(ModuleState.ABORTING);
    }

    @Test
    void commandsOutsideTheTableAreRejected() {
        assertFalse(BatchCommands.allows(ModuleState.HELD, BatchCommand.RESUME));
        assertFalse(BatchCommands.allows(ModuleState.PAUSED, BatchCommand.RESTART));
        assertFalse(BatchCommands.allows(ModuleState.RUNNING, BatchCommand.START));
        assertFalse(BatchCommands.allows(ModuleState.RUNNING, BatchCommand.RESET));
        assertFalse(BatchCommands.allows(ModuleState.STOPPING, BatchCommand.STOP));
    }

    private static void assertAllowed(ModuleState state, BatchCommand... expected) {
        assertEquals(Set.of(expected), BatchCommands.allowed(state), state.name());
    }
}
