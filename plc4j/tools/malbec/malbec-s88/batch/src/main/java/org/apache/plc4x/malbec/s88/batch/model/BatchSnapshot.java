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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable view of a batch at one instant.
 *
 * @param pendingDecision the transitions waiting for an operator, never {@code null}
 */
public record BatchSnapshot(String batchId, int run, BatchMode mode, ModuleState batchState,
                            boolean connected, double simTime, double wallElapsedSeconds,
                            List<StepStatus> activeSteps, Set<String> completedStepKeys,
                            Set<String> firedTransitionKeys, Map<String, VariableSample> variables,
                            List<BatchEvent> newEvents, PendingDecision pendingDecision) {

    public BatchSnapshot {
        activeSteps = List.copyOf(activeSteps);
        completedStepKeys = Set.copyOf(completedStepKeys);
        firedTransitionKeys = Set.copyOf(firedTransitionKeys);
        variables = Map.copyOf(variables);
        newEvents = List.copyOf(newEvents);
        pendingDecision = pendingDecision == null ? PendingDecision.NONE : pendingDecision;
    }
}
