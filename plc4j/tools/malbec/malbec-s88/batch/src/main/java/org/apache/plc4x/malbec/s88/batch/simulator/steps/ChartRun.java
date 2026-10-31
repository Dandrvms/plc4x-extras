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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.ChartGraph;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.Marking;

/** One chart of a batch that is running, with its marking and child charts. */
public final class ChartRun {

    private final String chartPath;
    private final S88RecipeElement owner;
    private final S88ProcedureLogic chart;
    private final ChartGraph graph;
    private final String parentKey;
    private final Map<String, ActiveStep> activeSteps = new LinkedHashMap<>();
    private final Set<String> completedStepKeys = new LinkedHashSet<>();
    private final Set<String> firedTransitionKeys = new LinkedHashSet<>();
    private final Map<String, ChartRun> children = new LinkedHashMap<>();
    private final Map<String, Double> enabledSince = new LinkedHashMap<>();
    private final Map<String, Integer> activations = new LinkedHashMap<>();
    private Marking marking = Marking.none();

    public ChartRun(String chartPath, S88RecipeElement owner, S88ProcedureLogic chart,
                    String parentKey) {
        this.chartPath = chartPath == null ? "" : chartPath;
        this.owner = owner;
        this.chart = chart;
        this.graph = ChartGraph.of(chart);
        this.parentKey = parentKey;
    }

    public void start() {
        marking = graph.initialMarking();
    }

    public String chartPath() {
        return chartPath;
    }

    public S88RecipeElement owner() {
        return owner;
    }

    public S88ProcedureLogic chart() {
        return chart;
    }

    public ChartGraph graph() {
        return graph;
    }

    public String parentKey() {
        return parentKey;
    }

    public Marking marking() {
        return marking;
    }

    public void setMarking(Marking marking) {
        this.marking = marking == null ? Marking.none() : marking;
    }

    public Map<String, ActiveStep> activeSteps() {
        return Collections.unmodifiableMap(activeSteps);
    }

    public ActiveStep activeStep(String stepId) {
        return activeSteps.get(stepId);
    }

    public ActiveStep activate(String stepId, S88ProcedureStep step, S88RecipeElement element) {
        ActiveStep active = new ActiveStep(stepId, step, element);
        activeSteps.put(stepId, active);
        return active;
    }

    public ActiveStep removeActive(String stepId) {
        return activeSteps.remove(stepId);
    }

    public ActiveStep markCompleted(String stepId) {
        ActiveStep active = activeSteps.get(stepId);
        if (active == null || active.completed()) {
            return active;
        }
        active.complete();
        completedStepKeys.add(key(stepId));
        return active;
    }

    public Set<String> completedStepKeys() {
        return Collections.unmodifiableSet(completedStepKeys);
    }

    public Set<String> firedTransitionKeys() {
        return Collections.unmodifiableSet(firedTransitionKeys);
    }

    public void markFired(String transitionId) {
        firedTransitionKeys.add(key(transitionId));
    }

    public Map<String, ChartRun> children() {
        return Collections.unmodifiableMap(children);
    }

    public ChartRun childFor(String stepId) {
        return children.get(stepId);
    }

    public void putChild(String stepId, ChartRun child) {
        children.put(stepId, child);
    }

    public Map<String, Double> enabledSince() {
        return Collections.unmodifiableMap(enabledSince);
    }

    public Double enabledSince(String transitionId) {
        return enabledSince.get(transitionId);
    }

    public void putEnabledSince(String transitionId, double simTime) {
        enabledSince.put(transitionId, simTime);
    }

    public void removeEnabledSince(String transitionId) {
        enabledSince.remove(transitionId);
    }

    public int activationCount(String stepId) {
        return activations.getOrDefault(stepId, 0);
    }

    public int incrementActivation(String stepId) {
        int count = activationCount(stepId) + 1;
        activations.put(stepId, count);
        return count;
    }

    public boolean isComplete() {
        if (!marking.has(CreateMasterRecipeUseCase.END_ID)) {
            return false;
        }
        for (ActiveStep active : activeSteps.values()) {
            if (!isTerminal(active.stepId()) && !active.completed()) {
                return false;
            }
        }
        return true;
    }

    public double progress() {
        long total = chart.getSteps().stream().filter(step -> !isTerminal(step.getId())).count();
        if (total == 0) {
            return 1.0;
        }
        long done = completedStepKeys.stream().filter(stepKey -> !isTerminal(stepId(stepKey))).count();
        return Math.max(0, Math.min(1, done / (double) total));
    }

    public String key(String nodeId) {
        return (chartPath.isEmpty() ? "" : chartPath) + "/" + nodeId;
    }

    private String stepId(String stepKey) {
        int slash = stepKey.lastIndexOf('/');
        return slash < 0 ? stepKey : stepKey.substring(slash + 1);
    }

    private boolean isTerminal(String stepId) {
        return CreateMasterRecipeUseCase.BEGIN_ID.equals(stepId)
                || CreateMasterRecipeUseCase.END_ID.equals(stepId);
    }

    /** One step holding a token, and whether the step has finished its work. */
    public static final class ActiveStep {

        private final String stepId;
        private final S88ProcedureStep step;
        private final S88RecipeElement element;
        private boolean completed;

        private ActiveStep(String stepId, S88ProcedureStep step, S88RecipeElement element) {
            this.stepId = stepId;
            this.step = step;
            this.element = element;
        }

        public String stepId() {
            return stepId;
        }

        public S88ProcedureStep step() {
            return step;
        }

        public S88RecipeElement element() {
            return element;
        }

        public boolean completed() {
            return completed;
        }

        private void complete() {
            completed = true;
        }
    }
}
