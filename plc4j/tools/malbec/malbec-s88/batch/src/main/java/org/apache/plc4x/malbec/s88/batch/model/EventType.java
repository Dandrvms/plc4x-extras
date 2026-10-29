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
 * What happened during a batch run.
 */
public enum EventType {
    BATCH_CREATED,
    BATCH_STARTED,
    COMMAND_ACCEPTED,
    COMMAND_REJECTED,
    STATE_CHANGED,
    STEP_ACTIVATED,
    STEP_COMPLETED,
    TRANSITION_FIRED,
    PARAMETER_DOWNLOADED,
    VALUE_CHANGED,
    FAILURE_INJECTED,
    DECISION_NEEDED,
    DECISION_TAKEN,
    CONNECTION_LOST,
    CONNECTION_RESTORED,
    BATCH_COMPLETED,
    BATCH_ABORTED,
    BATCH_STOPPED,
    BATCH_RESET,
    LOOP_GUARD_TRIPPED
}
