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
package org.apache.plc4x.malbec.s88.batch.simulator.steps;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.ModuleStateTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModuleStateMachineIsa88Test {

    private static final Map<ModuleState, Set<BatchCommand>> COMMANDS = commands();

    @Test
    void acceptsOnlyTheCommandsOfTheFigure() {
        for (ModuleState state : ModuleState.values()) {
            Set<BatchCommand> expected = COMMANDS.getOrDefault(state, Set.of());
            for (BatchCommand command : BatchCommand.values()) {
                assertEquals(expected.contains(command),
                        ModuleStateTable.allows(state, command),
                        state + " / " + command);
            }
        }
        assertEquals(21, ModuleStateTable.TABLE.size());
    }

    @Test
    void commandTransitionsGoWhereTheFigureSays() {
        assertTransition(ModuleState.IDLE, BatchCommand.START, ModuleState.RUNNING, null);
        assertTransition(ModuleState.RUNNING, BatchCommand.PAUSE, ModuleState.PAUSING,
                ModuleState.PAUSED);
        assertTransition(ModuleState.PAUSED, BatchCommand.RESUME, ModuleState.RUNNING, null);
        assertTransition(ModuleState.RUNNING, BatchCommand.HOLD, ModuleState.HOLDING,
                ModuleState.HELD);
        assertTransition(ModuleState.HELD, BatchCommand.RESTART, ModuleState.RESTARTING,
                ModuleState.RUNNING);
        assertTransition(ModuleState.RUNNING, BatchCommand.STOP, ModuleState.STOPPING,
                ModuleState.STOPPED);
        assertTransition(ModuleState.COMPLETE, BatchCommand.RESET, ModuleState.IDLE, null);
        assertTransition(ModuleState.ABORTED, BatchCommand.RESET, ModuleState.IDLE, null);
        assertTransition(ModuleState.STOPPED, BatchCommand.RESET, ModuleState.IDLE, null);
    }

    @Test
    void abortAndStopReachEveryActiveState() {
        for (ModuleState state : List.of(ModuleState.RUNNING, ModuleState.PAUSING,
                ModuleState.PAUSED, ModuleState.HOLDING, ModuleState.HELD,
                ModuleState.RESTARTING, ModuleState.STOPPING)) {
            assertTransition(state, BatchCommand.ABORT, ModuleState.ABORTING,
                    ModuleState.ABORTED);
        }
        for (ModuleState state : List.of(ModuleState.RUNNING, ModuleState.PAUSING,
                ModuleState.PAUSED, ModuleState.HOLDING, ModuleState.HELD,
                ModuleState.RESTARTING)) {
            assertTransition(state, BatchCommand.STOP, ModuleState.STOPPING,
                    ModuleState.STOPPED);
        }
    }

    @Test
    void stateMachineSettlesAfterTheTransient() {
        ModuleStateMachine machine = new ModuleStateMachine(2);

        ModuleStateMachine.CommandOutcome outcome = machine.command(BatchCommand.START);
        assertEquals(ModuleState.RUNNING, machine.state());
        assertEquals(ModuleState.RUNNING, outcome.to());

        outcome = machine.command(BatchCommand.PAUSE);
        assertEquals(ModuleState.PAUSING, machine.state());
        assertEquals(ModuleState.PAUSED, machine.settlesIn());
        machine.tick();
        assertEquals(ModuleState.PAUSING, machine.state());
        machine.tick();
        assertEquals(ModuleState.PAUSED, machine.state());
    }

    private static void assertTransition(ModuleState from, BatchCommand command,
                                         ModuleState to, ModuleState settlesIn) {
        ModuleStateTable.Transition transition = ModuleStateTable.find(from, command);
        assertNotNull(transition, from + " / " + command);
        assertEquals(from, transition.from());
        assertEquals(command, transition.command());
        assertEquals(to, transition.to());
        assertEquals(settlesIn, transition.settlesIn());
        if (settlesIn == null) {
            assertNull(transition.settlesIn());
        }
    }

    private static Map<ModuleState, Set<BatchCommand>> commands() {
        Map<ModuleState, Set<BatchCommand>> commands = new EnumMap<>(ModuleState.class);
        commands.put(ModuleState.IDLE, Set.of(BatchCommand.START));
        commands.put(ModuleState.RUNNING, Set.of(BatchCommand.PAUSE, BatchCommand.HOLD,
                BatchCommand.STOP, BatchCommand.ABORT));
        commands.put(ModuleState.PAUSING, Set.of(BatchCommand.STOP, BatchCommand.ABORT));
        commands.put(ModuleState.PAUSED, Set.of(BatchCommand.RESUME, BatchCommand.STOP,
                BatchCommand.ABORT));
        commands.put(ModuleState.HOLDING, Set.of(BatchCommand.STOP, BatchCommand.ABORT));
        commands.put(ModuleState.HELD, Set.of(BatchCommand.RESTART, BatchCommand.STOP,
                BatchCommand.ABORT));
        commands.put(ModuleState.COMPLETE, Set.of(BatchCommand.RESET));
        commands.put(ModuleState.ABORTING, Set.of());
        commands.put(ModuleState.ABORTED, Set.of(BatchCommand.RESET));
        commands.put(ModuleState.STOPPING, Set.of(BatchCommand.ABORT));
        commands.put(ModuleState.STOPPED, Set.of(BatchCommand.RESET));
        commands.put(ModuleState.RESTARTING, Set.of(BatchCommand.STOP, BatchCommand.ABORT));
        return commands;
    }
}
