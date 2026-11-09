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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.builder.PayloadValidator;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.PendingDecision;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.simulator.values.ConditionEvaluator;
import org.apache.plc4x.malbec.s88.batch.simulator.values.VariableStore;
import org.apache.plc4x.malbec.s88.core.ChartGraph;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;

/** Moves the tokens of the batch through its charts and starts the phases it reaches. */
public final class SequenceEngine {

    private static final double DEFAULT_MAX_ACTIVATIONS = 1000.0;
    private static final int MAX_LOOP_STEPS = 10000;

    /** Receives the events the sequencer produces. */
    @FunctionalInterface
    public interface EventSink {
        void emit(EventType type, String source, String elementId, String state,
                  String oldValue, String newValue, String details);
    }

    private final BatchPayload payload;
    private final S88MasterRecipe recipe;
    private final VariableStore store;
    private final Map<String, SimulatedModule> modules;
    private final EngineDecisionProvider decisions;
    private final ExitPolicy exitPolicy;
    private final List<String> warnings;
    private final EventSink sink;
    private final double maxActivationsPerStep;
    private final Set<String> warned = new LinkedHashSet<>();
    private final Set<String> announced = new LinkedHashSet<>();

    private ChartRun root;
    private boolean abortRequested;
    private boolean loopTripped;

    public SequenceEngine(BatchPayload payload, S88MasterRecipe recipe, VariableStore store,
                          Map<String, SimulatedModule> modules, EngineDecisionProvider decisions,
                          ExitPolicy exitPolicy, List<String> warnings, EventSink sink) {
        this(payload, recipe, store, modules, decisions, exitPolicy, warnings, sink,
                DEFAULT_MAX_ACTIVATIONS);
    }

    public SequenceEngine(BatchPayload payload, S88MasterRecipe recipe, VariableStore store,
                          Map<String, SimulatedModule> modules, EngineDecisionProvider decisions,
                          ExitPolicy exitPolicy, List<String> warnings, EventSink sink,
                          double maxActivationsPerStep) {
        this.payload = payload;
        this.recipe = recipe;
        this.store = store;
        this.modules = modules;
        this.decisions = decisions;
        this.exitPolicy = exitPolicy == null ? ExitPolicy.COMPLETE_ON_EXIT : exitPolicy;
        this.warnings = warnings;
        this.sink = sink;
        this.maxActivationsPerStep = maxActivationsPerStep <= 0
                ? DEFAULT_MAX_ACTIVATIONS : maxActivationsPerStep;
    }

    public void start() {
        if (root != null) {
            return;
        }
        if (payload == null || payload.control() == null) {
            warnOnce("root", "The batch has no control recipe, so there is nothing to run.");
            return;
        }
        root = new ChartRun("", null, payload.control().getProcedureLogic(), "");
        root.start();
        abortRequested = false;
        loopTripped = false;
    }

    public void reset() {
        root = null;
        abortRequested = false;
        loopTripped = false;
        warned.clear();
        announced.clear();
    }

    public boolean started() {
        return root != null;
    }

    public ChartRun root() {
        return root;
    }

    public boolean abortRequested() {
        return abortRequested;
    }

    public void tick(double simTime, boolean evaluate) {
        if (root == null || !evaluate) {
            return;
        }
        int guard = 0;
        boolean changed = true;
        while (changed) {
            changed = processRun(root, simTime, evaluate && !abortRequested);
            if (changed && ++guard > MAX_LOOP_STEPS) {
                trip(root.key(""), "The chart made more than " + MAX_LOOP_STEPS
                        + " changes in one tick, so the flow may be looping forever.");
                break;
            }
        }
    }

    public List<StepStatus> stepStatuses() {
        List<StepStatus> statuses = new ArrayList<>();
        if (root != null) {
            collectStatuses(root, statuses);
        }
        return List.copyOf(statuses);
    }

    public Set<String> completedStepKeys() {
        Set<String> keys = new LinkedHashSet<>();
        collectCompleted(root, keys);
        return keys;
    }

    public Set<String> firedTransitionKeys() {
        Set<String> keys = new LinkedHashSet<>();
        collectFired(root, keys);
        return keys;
    }

    public PendingDecision pendingDecision() {
        return decisions == null ? PendingDecision.NONE
                : new PendingDecision(decisions.pendingCandidates());
    }

    private boolean processRun(ChartRun run, double simTime, boolean evaluate) {
        boolean changed = activateNewTokens(run, simTime);
        for (ChartRun child : new ArrayList<>(run.children().values())) {
            changed |= processRun(child, simTime, evaluate);
        }
        changed |= updateEnabledSince(run, simTime);
        changed |= completeSteps(run, simTime);
        if (evaluate && !abortRequested) {
            changed |= fireOne(run, simTime);
        }
        return changed;
    }

    private boolean activateNewTokens(ChartRun run, double simTime) {
        boolean changed = false;
        Set<String> stale = new LinkedHashSet<>(run.activeSteps().keySet());
        stale.removeAll(run.marking().active());
        for (String stepId : stale) {
            run.removeActive(stepId);
            changed = true;
        }
        for (String stepId : run.marking().active()) {
            if (run.activeStep(stepId) != null) {
                continue;
            }
            S88ProcedureStep step = run.chart().findStep(stepId).orElse(null);
            if (step == null) {
                warnOnce("step:" + run.key(stepId), "The chart of " + chartLabel(run)
                        + " has a token on '" + stepId + "', but no such step.");
                continue;
            }
            S88RecipeElement element = recipe == null ? null
                    : recipe.findElement(step.getRecipeElementId()).orElse(null);
            int count = run.incrementActivation(stepId);
            if (count > maxActivationsPerStep) {
                trip(run.key(stepId), "Step '" + stepId + "' of " + chartLabel(run)
                        + " was activated more than " + maxActivationsPerStep
                        + " times, so the chart may be looping forever.");
                return changed;
            }
            run.activate(stepId, step, element);
            if (!isTerminal(stepId)) {
                emit(EventType.STEP_ACTIVATED, "sequence", run.key(stepId), "RUNNING",
                        null, stepId, "Step '" + stepId + "' of " + chartLabel(run)
                                + " started.");
            }
            changed = true;
            if (element != null && element.getProcedureLogic() != null
                    && element.getKind() != S88RecipeElementKind.PHASE) {
                startChild(run, stepId, element);
            } else if (element != null && element.getKind() == S88RecipeElementKind.PHASE) {
                startPhase(run, stepId, element, simTime);
            }
        }
        return changed;
    }

    private void startChild(ChartRun run, String stepId, S88RecipeElement element) {
        String childPath = element.getId() == null ? run.key(stepId) : element.getId();
        ChartRun child = new ChartRun(childPath, element, element.getProcedureLogic(),
                run.key(stepId));
        child.start();
        run.putChild(stepId, child);
    }

    private void startPhase(ChartRun run, String stepId, S88RecipeElement element, double simTime) {
        String stepKey = run.key(stepId);
        String moduleId = payload.moduleOfStep().get(stepKey);
        if (moduleId == null) {
            moduleId = payload.unitOfStep().get(stepKey);
        }
        if (moduleId == null) {
            warnOnce("module:" + stepKey, "Step '" + stepId + "' of " + chartLabel(run)
                    + " has no module to run its phase.");
            return;
        }
        SimulatedModule module = modules.get(moduleId);
        if (module == null) {
            warnOnce("module:" + stepKey, "The module '" + moduleId + "' of step '" + stepId
                    + "' is not simulated.");
            return;
        }
        if (module.state() != ModuleState.IDLE && module.state() != ModuleState.COMPLETE
                && module.state() != ModuleState.ABORTED && module.state() != ModuleState.STOPPED) {
            warnOnce("running:" + moduleId, "The module '" + moduleId + "' is already "
                    + module.state() + " when step '" + stepId + "' starts.");
        }
        if (module.state().kind() == ModuleState.Kind.FINAL) {
            module.command(BatchCommand.RESET, simTime);
            module.tick(0, simTime);
        }
        download(stepKey, moduleId, simTime);
        module.command(BatchCommand.START, simTime);
        module.setActiveStep(stepNumber(run, stepId), stepCount(run), simTime);
        ModuleStateMachine.CommandOutcome outcome = module.lastCommand();
        if (outcome != null && !outcome.accepted()) {
            warnOnce("start:" + moduleId, "The module '" + moduleId + "' did not start for step '"
                    + stepId + "': " + outcome.reason());
        }
    }

    private int stepNumber(ChartRun run, String stepId) {
        int number = 0;
        for (S88ProcedureStep step : run.chart().getSteps()) {
            if (isTerminal(step.getId())) {
                continue;
            }
            number++;
            if (stepId.equals(step.getId())) {
                return number;
            }
        }
        return 0;
    }

    private int stepCount(ChartRun run) {
        int count = 0;
        for (S88ProcedureStep step : run.chart().getSteps()) {
            if (!isTerminal(step.getId())) {
                count++;
            }
        }
        return count;
    }

    private void download(String stepKey, String moduleId, double simTime) {
        for (PreparedSetpoint setpoint : payload.setpoints().getOrDefault(stepKey, List.of())) {
            if (!store.contains(setpoint.variable())) {
                warnOnce("setpoint:" + stepKey + ":" + setpoint.variable(),
                        "The module '" + moduleId + "' does not publish '"
                                + setpoint.variable() + "'.");
                continue;
            }
            Object before = store.value(setpoint.variable());
            try {
                store.setIfPresent(setpoint.variable(), setpoint.value(), simTime);
            } catch (IllegalArgumentException badValue) {
                warnOnce("setpoint:" + stepKey + ":" + setpoint.variable(), badValue.getMessage());
                continue;
            }
            emit(EventType.PARAMETER_DOWNLOADED, "module", stepKey, null,
                    text(before), setpoint.value(), "Step '" + stepKey + "' set '"
                            + setpoint.variable() + "'.");
        }
    }

    private boolean updateEnabledSince(ChartRun run, double simTime) {
        boolean changed = false;
        Set<String> enabled = new LinkedHashSet<>(run.graph().enabled(run.marking()));
        for (String transitionId : enabled) {
            if (!run.enabledSince().containsKey(transitionId)) {
                run.putEnabledSince(transitionId, simTime);
                changed = true;
            }
        }
        for (String transitionId : new ArrayList<>(run.enabledSince().keySet())) {
            if (!enabled.contains(transitionId)) {
                run.removeEnabledSince(transitionId);
                changed = true;
            }
        }
        return changed;
    }

    private boolean completeSteps(ChartRun run, double simTime) {
        boolean changed = false;
        for (ChartRun.ActiveStep active : new ArrayList<>(run.activeSteps().values())) {
            if (active.completed()) {
                continue;
            }
            String stepId = active.stepId();
            boolean complete;
            S88RecipeElement element = active.element();
            if (isTerminal(stepId)) {
                complete = true;
            } else if (element == null) {
                warnOnce("step:" + run.key(stepId), "Step '" + stepId + "' of " + chartLabel(run)
                        + " has no recipe element, so it cannot do anything.");
                complete = true;
            } else if (element.getKind() == S88RecipeElementKind.PHASE) {
                SimulatedModule module = moduleFor(run, stepId);
                complete = module != null && module.state() == ModuleState.COMPLETE;
            } else if (element.getProcedureLogic() != null) {
                ChartRun child = run.childFor(stepId);
                complete = child != null && child.isComplete();
            } else if (element.getKind() != null && element.getKind().isTerminal()) {
                complete = true;
            } else {
                complete = true;
            }
            if (complete) {
                run.markCompleted(stepId);
                if (!isTerminal(stepId)) {
                    emit(EventType.STEP_COMPLETED, "sequence", run.key(stepId), "COMPLETE",
                            stepId, null, "Step '" + stepId + "' of " + chartLabel(run)
                                    + " finished.");
                }
                changed = true;
            }
        }
        return changed;
    }

    private boolean fireOne(ChartRun run, double simTime) {
        List<String> ready = readyTransitions(run, simTime);
        if (ready.isEmpty()) {
            return false;
        }
        String decidingStep = null;
        List<String> group = null;
        Map<String, List<String>> byInput = new LinkedHashMap<>();
        for (String transitionId : ready) {
            for (String input : activeInputs(run, transitionId)) {
                byInput.computeIfAbsent(input, key -> new ArrayList<>()).add(transitionId);
            }
        }
        for (Map.Entry<String, List<String>> entry : byInput.entrySet()) {
            if (entry.getValue().size() > 1) {
                decidingStep = entry.getKey();
                group = entry.getValue();
                break;
            }
        }
        String chosen;
        if (group != null) {
            chosen = decisions == null ? group.get(0)
                    : decisions.chooseBranch(decidingStep, group);
            if (chosen == null) {
                announce(decidingStep, group);
                ready.removeAll(group);
                if (ready.isEmpty()) {
                    return false;
                }
                chosen = ready.get(0);
            }
        } else {
            chosen = ready.get(0);
        }
        return fire(run, chosen);
    }

    private void announce(String stepId, List<String> group) {
        String key = stepId + "=" + String.join(",", group);
        if (!announced.add(key)) {
            return;
        }
        emit(EventType.DECISION_NEEDED, "sequence", stepId, null, null,
                String.join(",", group), "Step '" + stepId + "' has more than one way on.");
    }

    private List<String> readyTransitions(ChartRun run, double simTime) {
        List<String> ready = new ArrayList<>();
        for (String transitionId : run.graph().transitions()) {
            S88ProcedureTransition transition = run.chart().findTransition(transitionId)
                    .orElse(null);
            if (transition == null || !inputsDone(run, transitionId)) {
                continue;
            }
            PreparedCondition condition = conditionOf(run, transitionId, transition);
            double since = run.enabledSince(transitionId) == null
                    ? Double.NaN : run.enabledSince(transitionId);
            if (condition == null) {
                if (!transition.isGuarded()) {
                    ready.add(transitionId);
                }
            } else if (ConditionEvaluator.eval(condition, store, simTime, since)) {
                ready.add(transitionId);
            }
        }
        return ready;
    }

    private boolean inputsDone(ChartRun run, String transitionId) {
        List<String> inputs = activeInputs(run, transitionId);
        if (inputs.isEmpty()) {
            return false;
        }
        for (String stepId : inputs) {
            ChartRun.ActiveStep active = run.activeStep(stepId);
            if (active == null || !active.completed()) {
                return false;
            }
        }
        return true;
    }

    private List<String> activeInputs(ChartRun run, String transitionId) {
        List<String> inputs = new ArrayList<>();
        for (String node : run.graph().preds(transitionId)) {
            if (run.graph().isStep(node) && run.marking().has(node)) {
                inputs.add(node);
            }
        }
        return inputs;
    }

    private PreparedCondition conditionOf(ChartRun run, String transitionId,
                                          S88ProcedureTransition transition) {
        String key = PayloadValidator.key(run.chartPath(), transitionId);
        PreparedCondition condition = payload.conditions().get(key);
        if (condition == null) {
            if (transition.isGuarded()) {
                warnOnce("condition:" + key, "The condition of transition '" + transitionId
                        + "' in " + chartLabel(run) + " was not prepared.");
            }
            return null;
        }
        warnUnsupported(condition, key);
        return condition;
    }

    private void warnUnsupported(PreparedCondition condition, String key) {
        if (condition instanceof PreparedCondition.All all) {
            all.operands().forEach(operand -> warnUnsupported(operand, key));
        } else if (condition instanceof PreparedCondition.Any any) {
            any.operands().forEach(operand -> warnUnsupported(operand, key));
        } else if (condition instanceof PreparedCondition.Comparison comparison) {
            DataType type = comparison.dataType();
            S88ConditionOperator operator = comparison.operator();
            if (type != DataType.INTEGER && type != DataType.REAL
                    && operator != S88ConditionOperator.EQUALS
                    && operator != S88ConditionOperator.NOT_EQUALS) {
                warnOnce("comparison:" + key, "Transition condition '" + key + "' compares text with "
                        + operator + "; only EQUALS and NOT_EQUALS are supported for text.");
            }
        }
    }

    private boolean fire(ChartRun run, String transitionId) {
        ChartGraph.Firing firing = null;
        for (ChartGraph.Firing candidate : run.graph().successors(run.marking())) {
            if (candidate.firedTransitions().contains(transitionId)) {
                firing = candidate;
                break;
            }
        }
        if (firing == null) {
            return false;
        }
        List<String> consumed = activeInputs(run, transitionId);
        for (String stepId : consumed) {
            ChartRun.ActiveStep active = run.activeStep(stepId);
            boolean forced = active != null && !active.completed();
            if (forced) {
                run.markCompleted(stepId);
                if (exitPolicy == ExitPolicy.COMPLETE_ON_EXIT && !isTerminal(stepId)) {
                    emit(EventType.STEP_COMPLETED, "sequence", run.key(stepId), "COMPLETE",
                            stepId, null, "Step '" + stepId + "' of " + chartLabel(run)
                                    + " was left before it finished.");
                }
            }
            run.removeActive(stepId);
        }
        run.setMarking(firing.marking());
        for (String fired : firing.firedTransitions()) {
            run.markFired(fired);
            emit(EventType.TRANSITION_FIRED, "sequence", run.key(fired), "FIRED",
                    null, fired, "Transition '" + fired + "' of " + chartLabel(run)
                            + " fired.");
        }
        return true;
    }

    private SimulatedModule moduleFor(ChartRun run, String stepId) {
        String stepKey = run.key(stepId);
        String moduleId = payload.moduleOfStep().get(stepKey);
        if (moduleId == null) {
            moduleId = payload.unitOfStep().get(stepKey);
        }
        return moduleId == null ? null : modules.get(moduleId);
    }

    private void collectStatuses(ChartRun run, List<StepStatus> statuses) {
        for (ChartRun.ActiveStep active : run.activeSteps().values()) {
            String stepId = active.stepId();
            if (isTerminal(stepId)) {
                continue;
            }
            S88RecipeElement element = active.element();
            String stepKey = run.key(stepId);
            ModuleState state;
            double progress;
            String moduleId = null;
            List<StepStatus> childStatuses = new ArrayList<>();
            if (element != null && element.getKind() == S88RecipeElementKind.PHASE) {
                moduleId = payload.moduleOfStep().get(stepKey);
                if (moduleId == null) {
                    moduleId = payload.unitOfStep().get(stepKey);
                }
                SimulatedModule module = moduleId == null ? null : modules.get(moduleId);
                if (module == null) {
                    state = active.completed() ? ModuleState.COMPLETE : ModuleState.RUNNING;
                    progress = active.completed() ? 1.0 : 0.0;
                } else {
                    state = module.state();
                    progress = module.progress();
                }
            } else if (element != null && element.getProcedureLogic() != null) {
                ChartRun child = run.childFor(stepId);
                if (child != null) {
                    collectStatuses(child, childStatuses);
                }
                boolean complete = active.completed() || (child != null && child.isComplete());
                state = complete ? ModuleState.COMPLETE : ModuleState.RUNNING;
                progress = complete ? 1.0 : progressOf(child, childStatuses);
            } else {
                state = active.completed() ? ModuleState.COMPLETE : ModuleState.RUNNING;
                progress = active.completed() ? 1.0 : 0.0;
            }
            statuses.add(new StepStatus(run.chartPath(), stepId, element == null ? null
                    : element.getId(), moduleId, state, progress, stepNumber(run, stepId),
                    stepCount(run)));
            statuses.addAll(childStatuses);
        }
    }

    private static double progressOf(ChartRun child, List<StepStatus> childStatuses) {
        double progress = child == null ? 0.0 : child.progress();
        for (StepStatus status : childStatuses) {
            progress = Math.max(progress, status.progress());
        }
        return Math.max(0.0, Math.min(1.0, progress));
    }

    private void collectCompleted(ChartRun run, Set<String> keys) {
        if (run == null) {
            return;
        }
        keys.addAll(run.completedStepKeys());
        run.children().values().forEach(child -> collectCompleted(child, keys));
    }

    private void collectFired(ChartRun run, Set<String> keys) {
        if (run == null) {
            return;
        }
        keys.addAll(run.firedTransitionKeys());
        run.children().values().forEach(child -> collectFired(child, keys));
    }

    private void trip(String elementId, String message) {
        if (loopTripped) {
            return;
        }
        loopTripped = true;
        abortRequested = true;
        emit(EventType.LOOP_GUARD_TRIPPED, "sequence", elementId, "ABORTING",
                null, "LOOP_GUARD", message);
    }

    private boolean isTerminal(String stepId) {
        return CreateMasterRecipeUseCase.BEGIN_ID.equals(stepId)
                || CreateMasterRecipeUseCase.END_ID.equals(stepId);
    }

    private String chartLabel(ChartRun run) {
        return run.chartPath() == null || run.chartPath().isBlank()
                ? "the recipe" : "'" + run.chartPath() + "'";
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key) && warnings != null) {
            warnings.add(message);
        }
    }

    private void emit(EventType type, String source, String elementId, String state,
                      String oldValue, String newValue, String details) {
        if (sink != null) {
            sink.emit(type, source, elementId, state, oldValue, newValue, details);
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Enum<?> enumeration ? enumeration.name() : value.toString();
    }
}
