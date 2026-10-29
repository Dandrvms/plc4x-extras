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

/**
 * The states of the ISA-88 module state machine, in the platform state order.
 */
public enum ModuleState {
    IDLE(Kind.INITIAL),
    RUNNING(Kind.ACTIVE),
    PAUSING(Kind.TRANSIENT),
    PAUSED(Kind.QUIESCENT),
    HOLDING(Kind.TRANSIENT),
    HELD(Kind.QUIESCENT),
    COMPLETE(Kind.FINAL),
    ABORTING(Kind.TRANSIENT),
    ABORTED(Kind.FINAL),
    STOPPING(Kind.TRANSIENT),
    STOPPED(Kind.FINAL),
    RESTARTING(Kind.TRANSIENT);

    /** How a state behaves in the ISA-88 diagram. */
    public enum Kind {
        INITIAL,
        ACTIVE,
        TRANSIENT,
        QUIESCENT,
        FINAL
    }

    private final Kind kind;

    ModuleState(Kind kind) {
        this.kind = kind;
    }

    /**
     * @return the role of this state in the diagram
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return {@code true} when the state accepts {@code STOP} or {@code ABORT}
     */
    public boolean isActive() {
        return this != IDLE && kind != Kind.FINAL;
    }
}
