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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.core.SimulationDecisionProvider;

/** Chooses an alternative branch automatically or waits for an operator. */
public final class EngineDecisionProvider implements SimulationDecisionProvider {

    private final DecisionMode mode;
    private final Map<String, List<String>> pending = new LinkedHashMap<>();
    private final Map<String, String> decided = new LinkedHashMap<>();

    public EngineDecisionProvider(DecisionMode mode) {
        this.mode = mode == null ? DecisionMode.AUTO : mode;
    }

    public DecisionMode mode() {
        return mode;
    }

    @Override
    public String chooseBranch(String stepId, List<String> enabledTransitions) {
        if (enabledTransitions == null || enabledTransitions.isEmpty()) {
            return null;
        }
        List<String> candidates = List.copyOf(enabledTransitions);
        if (mode == DecisionMode.AUTO) {
            decided.remove(stepId);
            pending.remove(stepId);
            return candidates.get(0);
        }
        String chosen = decided.get(stepId);
        if (chosen != null && candidates.contains(chosen)) {
            decided.remove(stepId);
            pending.remove(stepId);
            return chosen;
        }
        if (chosen != null && !candidates.contains(chosen)) {
            decided.remove(stepId);
        }
        pending.put(stepId, candidates);
        return null;
    }

    /** Records the transition an operator chose for the pending decision. */
    public boolean decide(String transitionId) {
        if (transitionId == null || transitionId.isBlank()) {
            return false;
        }
        for (Map.Entry<String, List<String>> entry : pending.entrySet()) {
            if (entry.getValue().contains(transitionId)) {
                decided.put(entry.getKey(), transitionId);
                pending.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    /** The candidate transitions still waiting for an operator, joined by commas. */
    public String pendingDecision() {
        List<String> candidates = new ArrayList<>();
        for (List<String> group : pending.values()) {
            for (String candidate : group) {
                if (!candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates.isEmpty() ? null : String.join(",", candidates);
    }

    public boolean isPending(String stepId) {
        return pending.containsKey(stepId);
    }

    public void clear() {
        pending.clear();
        decided.clear();
    }

    @Override
    public boolean evaluate(String transitionId) {
        return true;
    }
}
