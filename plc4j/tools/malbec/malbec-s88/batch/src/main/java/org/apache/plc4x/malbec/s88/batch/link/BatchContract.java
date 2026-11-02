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
package org.apache.plc4x.malbec.s88.batch.link;

/**
 * Published names of the batch variables.
 */
public final class BatchContract {

    public static final String STATE = "STATE";
    public static final String RUN = "RUN";
    public static final String SIM_TIME = "SIM_TIME";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String ACTIVE_STEPS = "ACTIVE_STEPS";
    public static final String DONE_STEPS = "DONE_STEPS";
    public static final String FIRED_TRANSITIONS = "FIRED_TRANSITIONS";
    public static final String PENDING_DECISION = "PENDING_DECISION";
    public static final String DECISION = "DECISION";
    public static final String COMMAND = "COMMAND";
    public static final String COMMAND_ACK = "COMMAND_ACK";

    private BatchContract() {
    }

    public static String state(String batchId) {
        return item(STATE, batchId);
    }

    public static String run(String batchId) {
        return item(RUN, batchId);
    }

    public static String simTime(String batchId) {
        return item(SIM_TIME, batchId);
    }

    public static String heartbeat(String batchId) {
        return item(HEARTBEAT, batchId);
    }

    public static String activeSteps(String batchId) {
        return item(ACTIVE_STEPS, batchId);
    }

    public static String doneSteps(String batchId) {
        return item(DONE_STEPS, batchId);
    }

    public static String firedTransitions(String batchId) {
        return item(FIRED_TRANSITIONS, batchId);
    }

    public static String pendingDecision(String batchId) {
        return item(PENDING_DECISION, batchId);
    }

    public static String decision(String batchId) {
        return item(DECISION, batchId);
    }

    public static String command(String batchId) {
        return item(COMMAND, batchId);
    }

    public static String commandAck(String batchId) {
        return item(COMMAND_ACK, batchId);
    }

    public static String item(String item, String batchId) {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Item cannot be empty");
        }
        return "BATCH_" + ChannelNamer.sanitize(item) + "_" + ChannelNamer.sanitize(batchId);
    }
}
