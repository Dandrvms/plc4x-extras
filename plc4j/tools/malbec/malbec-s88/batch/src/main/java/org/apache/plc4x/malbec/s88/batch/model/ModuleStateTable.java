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

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The command transitions of the ISA-88 module state machine.
 */
public final class ModuleStateTable {

    /**
     * One command transition.
     *
     * @param settlesIn the state reached after the transient, {@code null} when {@code to} is used
     *                  directly
     */
    public record Transition(ModuleState from, BatchCommand command, ModuleState to,
                             ModuleState settlesIn) {
    }

    /** The state and command that select a transition. */
    public record StateCommand(ModuleState state, BatchCommand command) {
    }

    /** All command transitions, by state and command. */
    public static final Map<StateCommand, Transition> TABLE = build();

    private ModuleStateTable() {
    }

    /**
     * @return the transition for a state and command, {@code null} when the command is rejected
     */
    public static Transition find(ModuleState state, BatchCommand command) {
        return TABLE.get(new StateCommand(state, command));
    }

    /**
     * @return {@code true} when the state accepts the command
     */
    public static boolean allows(ModuleState state, BatchCommand command) {
        return find(state, command) != null;
    }

    /**
     * The commands a state accepts, in {@link BatchCommand} declaration order.
     *
     * @return an unmodifiable set, empty when no command is accepted
     */
    public static Set<BatchCommand> allowed(ModuleState state) {
        EnumSet<BatchCommand> allowed = EnumSet.noneOf(BatchCommand.class);
        for (BatchCommand command : BatchCommand.values()) {
            if (allows(state, command)) {
                allowed.add(command);
            }
        }
        return Collections.unmodifiableSet(allowed);
    }

    private static Map<StateCommand, Transition> build() {
        Map<StateCommand, Transition> table = new LinkedHashMap<>();
        add(table, ModuleState.IDLE, BatchCommand.START, ModuleState.RUNNING, null);
        add(table, ModuleState.RUNNING, BatchCommand.PAUSE, ModuleState.PAUSING, ModuleState.PAUSED);
        add(table, ModuleState.PAUSED, BatchCommand.RESUME, ModuleState.RUNNING, null);
        add(table, ModuleState.RUNNING, BatchCommand.HOLD, ModuleState.HOLDING, ModuleState.HELD);
        add(table, ModuleState.HELD, BatchCommand.RESTART, ModuleState.RESTARTING, ModuleState.RUNNING);
        add(table, ModuleState.RUNNING, BatchCommand.STOP, ModuleState.STOPPING, ModuleState.STOPPED);
        add(table, ModuleState.COMPLETE, BatchCommand.RESET, ModuleState.IDLE, null);
        add(table, ModuleState.ABORTED, BatchCommand.RESET, ModuleState.IDLE, null);
        add(table, ModuleState.STOPPED, BatchCommand.RESET, ModuleState.IDLE, null);
        for (ModuleState state : List.of(ModuleState.RUNNING, ModuleState.PAUSING, ModuleState.PAUSED,
                ModuleState.HOLDING, ModuleState.HELD, ModuleState.RESTARTING, ModuleState.STOPPING)) {
            add(table, state, BatchCommand.ABORT, ModuleState.ABORTING, ModuleState.ABORTED);
        }
        for (ModuleState state : List.of(ModuleState.RUNNING, ModuleState.PAUSING, ModuleState.PAUSED,
                ModuleState.HOLDING, ModuleState.HELD, ModuleState.RESTARTING)) {
            add(table, state, BatchCommand.STOP, ModuleState.STOPPING, ModuleState.STOPPED);
        }
        return Collections.unmodifiableMap(table);
    }

    private static void add(Map<StateCommand, Transition> table, ModuleState from,
                            BatchCommand command, ModuleState to, ModuleState settlesIn) {
        table.put(new StateCommand(from, command), new Transition(from, command, to, settlesIn));
    }
}
