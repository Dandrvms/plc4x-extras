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

import java.util.Collection;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;

/** Derives the batch state from the sequence and its active modules. */
public final class BatchStateDeriver {

    private BatchStateDeriver() {
        /* This utility class should not be instantiated */
    }

    public static ModuleState derive(boolean started, boolean sequenceComplete,
                                     boolean abortRequested, boolean stopRequested,
                                     Collection<ModuleState> moduleStates) {
        if (!started) {
            return ModuleState.IDLE;
        }
        if (sequenceComplete) {
            return ModuleState.COMPLETE;
        }
        if (abortRequested) {
            return anyActive(moduleStates) ? ModuleState.ABORTING : ModuleState.ABORTED;
        }
        if (stopRequested) {
            for (ModuleState state : moduleStates) {
                if (state.isActive() && state != ModuleState.STOPPING
                        && state != ModuleState.STOPPED) {
                    return ModuleState.STOPPING;
                }
            }
            return ModuleState.STOPPED;
        }
        for (ModuleState state : moduleStates) {
            if (state == ModuleState.RUNNING) {
                return ModuleState.RUNNING;
            }
        }
        for (ModuleState state : moduleStates) {
            if (state == ModuleState.RESTARTING) {
                return ModuleState.RESTARTING;
            }
        }
        for (ModuleState state : moduleStates) {
            if (state == ModuleState.PAUSING) {
                return ModuleState.PAUSING;
            }
        }
        for (ModuleState state : moduleStates) {
            if (state == ModuleState.HOLDING) {
                return ModuleState.HOLDING;
            }
        }
        boolean anyPaused = false;
        boolean anyHeld = false;
        for (ModuleState state : moduleStates) {
            if (state == ModuleState.PAUSED) {
                anyPaused = true;
            } else if (state == ModuleState.HELD) {
                anyHeld = true;
            }
        }
        if (anyHeld) {
            return ModuleState.HELD;
        }
        if (anyPaused) {
            return ModuleState.PAUSED;
        }
        return ModuleState.RUNNING;
    }

    private static boolean anyActive(Collection<ModuleState> moduleStates) {
        for (ModuleState state : moduleStates) {
            if (state.isActive()) {
                return true;
            }
        }
        return false;
    }
}
