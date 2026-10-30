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

import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.ModuleStateTable;

/** The ISA-88 state machine of one module. */
public final class ModuleStateMachine {

    /** The result of sending a command. */
    public record CommandOutcome(boolean accepted, ModuleState from, ModuleState to, String reason) {
    }

    private final int transientTicks;
    private ModuleState state = ModuleState.IDLE;
    private ModuleState settlesIn;
    private int transientTicksLeft;

    public ModuleStateMachine(int transientTicks) {
        if (transientTicks < 0) {
            throw new IllegalArgumentException("A transient state cannot last less than no ticks.");
        }
        this.transientTicks = transientTicks;
    }

    public ModuleState state() {
        return state;
    }

    public boolean isTransient() {
        return settlesIn != null;
    }

    public ModuleState settlesIn() {
        return settlesIn;
    }

    public CommandOutcome command(BatchCommand command) {
        if (command == null) {
            return reject("No command was given.");
        }
        ModuleStateTable.Transition transition = ModuleStateTable.find(state, command);
        if (transition == null) {
            return reject(command + " is not valid in state " + state + ".");
        }
        ModuleState from = state;
        state = transition.to();
        settlesIn = transition.settlesIn();
        transientTicksLeft = settlesIn == null ? 0 : transientTicks;
        if (settlesIn != null && transientTicks == 0) {
            settle();
        }
        return new CommandOutcome(true, from, state, null);
    }

    public void tick() {
        if (settlesIn == null) {
            return;
        }
        if (transientTicksLeft > 0) {
            transientTicksLeft--;
        }
        if (transientTicksLeft == 0) {
            settle();
        }
    }

    public void fail() {
        if (!state.isActive() || state == ModuleState.ABORTING) {
            return;
        }
        state = ModuleState.ABORTING;
        settlesIn = ModuleState.ABORTED;
        transientTicksLeft = transientTicks;
        if (transientTicks == 0) {
            settle();
        }
    }

    public void complete() {
        state = ModuleState.COMPLETE;
        settlesIn = null;
        transientTicksLeft = 0;
    }

    public void reset() {
        state = ModuleState.IDLE;
        settlesIn = null;
        transientTicksLeft = 0;
    }

    private CommandOutcome reject(String reason) {
        return new CommandOutcome(false, state, state, reason);
    }

    private void settle() {
        if (settlesIn != null) {
            state = settlesIn;
            settlesIn = null;
            transientTicksLeft = 0;
        }
    }
}
