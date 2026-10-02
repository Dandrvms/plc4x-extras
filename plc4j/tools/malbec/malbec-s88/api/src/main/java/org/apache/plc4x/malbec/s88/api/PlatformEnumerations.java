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
package org.apache.plc4x.malbec.s88.api;

import java.util.List;

/**
 * The two enumerations the ISA-88 variables are drawn from.
 * <p>
 * They belong to the platform rather than to the plant: every equipment module publishes a
 * {@code STATE} and a {@code COMMAND}, so the values they may take are part of the contract
 * between the batch and the module, not a decision the engineer makes per installation. They are
 * therefore not editable, and the action that manages the other enumerations of the plant leaves
 * these two alone.
 */
public final class PlatformEnumerations {

    /** Name of the enumeration giving the states a module may report. */
    public static final String STATE = "STATE";

    /** Name of the enumeration giving the orders the batch may send to a module. */
    public static final String COMMAND = "COMMAND";

    /**
     * The states a module reports, in the order that fixes their index.
     */
    private static final List<String> STATE_VALUES = List.of(
            "IDLE",
            "RUNNING",
            "PAUSING",
            "PAUSED",
            "HOLDING",
            "HELD",
            "COMPLETE",
            "ABORTING",
            "ABORTED",
            "STOPPING",
            "STOPPED",
            "RESTARTING");

    /**
     * The orders the batch may send, in the order that fixes their index.
     * <p>
     * {@code NONE} comes first and is what a module reads when no order has arrived, which is what
     * it sees for as long as the batch server is away. The rest are deliberate: {@code PAUSE}
     * stops the phase at the next safe point and {@code HOLD} takes it to a safe condition now,
     * and {@code RESUME} is what undo them, so neither is a synonym of the
     * other. {@code RESTART} runs the phase again from its start and {@code RESET} leaves it ready
     * to be started, which are also not the same thing.
     */
    private static final List<String> COMMAND_VALUES = List.of(
            "NONE",
            "START",
            "RESUME",
            "PAUSE",
            "HOLD",
            "ABORT",
            "RESTART",
            "STOP",
            "RESET");

    private PlatformEnumerations() {
        /* This utility class should not be instantiated */
    }

    /**
     * The state enumeration, as the model holds it.
     *
     * @return a fresh enumeration, never {@code null}
     */
    public static S88Enumeration stateValue() {
        return enumeration(STATE, STATE_VALUES);
    }

    /**
     * The command enumeration, as the model holds it.
     *
     * @return a fresh enumeration, never {@code null}
     */
    public static S88Enumeration commandValues() {
        return enumeration(COMMAND, COMMAND_VALUES);
    }

    /**
     * Whether an enumeration is one of the two the platform owns, and so may not be renamed or
     * deleted from the plant.
     *
     * @param name name of the enumeration, may be {@code null}
     * @return {@code true} when the enumeration belongs to the platform
     */
    public static boolean isPlatformEnumeration(String name) {
        return STATE.equals(name) || COMMAND.equals(name);
    }

    /**
     * The values one of the two enumerations holds, in protocol order.
     *
     * @param name name of the enumeration, may be {@code null}
     * @return the values, empty when the name is not one of the two
     */
    public static List<String> valuesOf(String name) {
        if (STATE.equals(name)) {
            return STATE_VALUES;
        }
        if (COMMAND.equals(name)) {
            return COMMAND_VALUES;
        }
        return List.of();
    }

    private static S88Enumeration enumeration(String name, List<String> values) {
        S88Enumeration enumeration = new S88Enumeration(name);
        for (int i = 0; i < values.size(); i++) {
            enumeration.setValue(values.get(i), i);
        }
        return enumeration;
    }
}
