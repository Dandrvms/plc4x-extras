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
package org.apache.plc4x.malbec.s88.recipes.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommands;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;
import org.apache.plc4x.malbec.s88.recipes.editor.RecipeChartScene;

/** Turns a batch snapshot into the plain data the runtime window draws. */
public final class RuntimeViewModel {

    private final S88MasterRecipe recipe;
    private final S88PlantSnapshot plant;
    private final Map<String, String> moduleOfVariable;

    public RuntimeViewModel(S88MasterRecipe recipe, S88PlantSnapshot plant) {
        this.recipe = recipe;
        this.plant = plant;
        this.moduleOfVariable = modulesOfVariables(plant);
    }

    /** The chart path the simulator uses for the chart of a step. */
    public String chartPathOf(S88RecipeElement owner) {
        return owner == null ? "" : owner.getId();
    }

    /** The deepest chart that has an active step, or the procedure chart when none has one. */
    public String activeChartPath(BatchSnapshot snapshot) {
        StepStatus active = deepestActiveStep(snapshot);
        return active == null || active.chartPath() == null ? "" : active.chartPath();
    }

    /** The active step as it is shown to the operator. */
    public String activeStepLabel(BatchSnapshot snapshot) {
        StepStatus active = deepestActiveStep(snapshot);
        if (active == null) {
            return "No active step";
        }
        String name = active.stepId();
        if (active.stepIndex() > 0 && active.stepCount() > 0) {
            return name + " (" + active.stepIndex() + "/" + active.stepCount() + ")";
        }
        return name;
    }

    private StepStatus deepestActiveStep(BatchSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        StepStatus deepest = null;
        int depth = -1;
        for (StepStatus step : snapshot.activeSteps()) {
            String path = step.chartPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            int candidate = depthOf(path);
            if (candidate > depth) {
                depth = candidate;
                deepest = step;
            }
        }
        return deepest;
    }

    private int depthOf(String path) {
        S88RecipeElement element = recipe == null ? null : recipe.findElement(path).orElse(null);
        int depth = 0;
        while (element != null) {
            depth++;
            element = element.getParent();
        }
        return depth;
    }

    /** What the chart of the given step should show for this snapshot. */
    public ChartState chartState(S88RecipeElement owner, BatchSnapshot snapshot) {
        String path = chartPathOf(owner);
        Map<String, RecipeChartScene.ExecState> states = new LinkedHashMap<>();
        Map<String, String> badges = new LinkedHashMap<>();
        for (String completed : snapshot.completedStepKeys()) {
            String local = localOf(path, completed);
            if (local != null) {
                states.put(local, RecipeChartScene.ExecState.DONE);
            }
        }
        for (StepStatus step : snapshot.activeSteps()) {
            if (!path.equals(step.chartPath() == null ? "" : step.chartPath())) {
                continue;
            }
            RecipeChartScene.ExecState state = stateOf(step.state());
            states.put(step.stepId(), state);
            String badge = badgeOf(step, state);
            if (badge != null) {
                badges.put(step.stepId(), badge);
            }
        }
        Set<String> fired = new LinkedHashSet<>();
        for (String key : snapshot.firedTransitionKeys()) {
            String local = localOf(path, key);
            if (local != null) {
                fired.add(local);
            }
        }
        return new ChartState(states, fired, badges);
    }

    /** The active steps of the batch, in the order the server reported them. */
    public List<StepStatus> activeSteps(BatchSnapshot snapshot) {
        return snapshot.activeSteps();
    }

    /** The commands the observed batch state accepts. */
    public Set<BatchCommand> commands(BatchSnapshot snapshot) {
        return BatchCommands.allowed(snapshot.batchState());
    }

    /** The variables to show, optionally filtered by module. */
    public List<VariableRow> variables(BatchSnapshot snapshot, String moduleFilter) {
        Map<String, VariableRow> rows = new TreeMap<>();
        for (Map.Entry<String, VariableSample> entry : snapshot.variables().entrySet()) {
            String variable = entry.getKey();
            String module = moduleOf(variable);
            if (moduleFilter != null && !moduleFilter.isBlank()
                    && !moduleFilter.equalsIgnoreCase(module)) {
                continue;
            }
            VariableSample sample = entry.getValue();
            rows.put(module + '\u0000' + variable, new VariableRow(module, variable,
                    text(sample.value()), sample.unit(), sample.quality(), sample.sourceTime()));
        }
        return new ArrayList<>(rows.values());
    }

    /** The recipe drawn by this window, which is the control recipe of the run. */
    public S88MasterRecipe recipe() {
        return recipe;
    }

    private String moduleOf(String variable) {
        String direct = moduleOfVariable.get(variable);
        if (direct != null) {
            return direct;
        }
        if (variable.startsWith("STATE_") || variable.startsWith("FAILURE_")
                || variable.startsWith("REQUEST_") || variable.startsWith("STEP_INDEX_")
                || variable.startsWith("STEP_COUNT_")) {
            String suffix = variable.substring(variable.indexOf('_') + 1);
            if (plant != null && plant.findById(suffix).isPresent()) {
                return suffix;
            }
        }
        return "—";
    }

    private static Map<String, String> modulesOfVariables(S88PlantSnapshot plant) {
        Map<String, String> owners = new LinkedHashMap<>();
        if (plant == null || plant.getRoot() == null) {
            return owners;
        }
        collectVariables(plant.getRoot(), owners);
        return owners;
    }

    private static void collectVariables(S88Element element, Map<String, String> owners) {
        if (element == null) {
            return;
        }
        for (String container : List.of(S88PlantModel.REPORTS, S88PlantModel.PARAMETERS)) {
            Map<String, Object> values = element.getStructuredProperty(container);
            if (values == null) {
                continue;
            }
            for (String name : values.keySet()) {
                owners.putIfAbsent(name, element.getId());
            }
        }
        for (S88Element child : element.getChildren()) {
            collectVariables(child, owners);
        }
    }

    private static String localOf(String path, String key) {
        String prefix = path.isEmpty() ? "/" : path + "/";
        return key != null && key.startsWith(prefix) ? key.substring(prefix.length()) : null;
    }

    private static RecipeChartScene.ExecState stateOf(ModuleState state) {
        return switch (state) {
            case RUNNING, PAUSING, HOLDING, RESTARTING -> RecipeChartScene.ExecState.ACTIVE;
            case PAUSED -> RecipeChartScene.ExecState.PAUSED;
            case HELD -> RecipeChartScene.ExecState.HELD;
            case COMPLETE -> RecipeChartScene.ExecState.DONE;
            case ABORTING, ABORTED, STOPPING, STOPPED -> RecipeChartScene.ExecState.FAULT;
            case IDLE -> RecipeChartScene.ExecState.NONE;
        };
    }

    private static String badgeOf(StepStatus step, RecipeChartScene.ExecState state) {
        if (state == RecipeChartScene.ExecState.NONE) {
            return null;
        }
        if (state == RecipeChartScene.ExecState.DONE) {
            return "DONE";
        }
        return step.state().name();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /** What the runtime chart draws for one step. */
    public record ChartState(Map<String, RecipeChartScene.ExecState> states,
                             Set<String> firedTransitions,
                             Map<String, String> badges) {

        public ChartState {
            states = Map.copyOf(states);
            firedTransitions = Set.copyOf(firedTransitions);
            badges = Map.copyOf(badges);
        }
    }

    /** One row of the variable table. */
    public record VariableRow(String module, String variable, String value, String unit,
                              org.apache.plc4x.malbec.s88.batch.link.Quality quality,
                              Instant sourceTime) {
    }
}
