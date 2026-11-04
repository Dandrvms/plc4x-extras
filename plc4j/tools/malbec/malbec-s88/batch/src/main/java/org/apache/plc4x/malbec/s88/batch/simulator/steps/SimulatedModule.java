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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.batch.model.TimeUnits;
import org.apache.plc4x.malbec.s88.batch.simulator.values.FollowRuleHeuristic;
import org.apache.plc4x.malbec.s88.batch.simulator.values.ProcessVariable;
import org.apache.plc4x.malbec.s88.batch.simulator.values.VariableStore;
import org.apache.plc4x.malbec.s88.core.BaseNameResolver;

/** One simulated equipment module and the phase it runs. */
public final class SimulatedModule {

    private static final List<String> DURATION_NAMES = List.of("DURACION", "DURATION", "TIEMPO");

    private final String moduleId;
    private final S88Element element;
    private final SimulationProfile profile;
    private final VariableStore store;
    private final ModuleStateMachine machine;
    private final List<FollowRule> followRules;

    private double elapsed;
    private double durationSeconds;
    private boolean durationExplicit;
    private int stepCount;
    private int stepIndex;
    private ModuleStateMachine.CommandOutcome lastCommand;

    public SimulatedModule(String moduleId, S88Element element, SimulationProfile profile,
                           VariableStore store) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("A simulated module needs an id.");
        }
        this.moduleId = moduleId.trim();
        this.element = Objects.requireNonNull(element, "A simulated module needs its plant element.");
        this.profile = Objects.requireNonNull(profile, "A simulated module needs a profile.");
        this.store = Objects.requireNonNull(store, "A simulated module needs a variable store.");
        this.machine = new ModuleStateMachine(profile.transientTicks());
        this.followRules = effectiveRules();
        this.durationSeconds = profile.defaultPhaseSeconds();
        Double declaredCount = store.numeric(PlatformVariable.STEP_COUNT.publishedName(this.moduleId));
        this.stepCount = declaredCount == null ? 0 : Math.max(0, (int) Math.floor(declaredCount));
    }

    public String moduleId() {
        return moduleId;
    }

    public ModuleState state() {
        return machine.state();
    }

    public double elapsed() {
        return elapsed;
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public double progress() {
        if (durationSeconds <= 0) {
            return 1.0;
        }
        return Math.max(0, Math.min(1, elapsed / durationSeconds));
    }

    public int stepCount() {
        return stepCount;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public List<FollowRule> followRules() {
        return followRules;
    }

    public ModuleStateMachine.CommandOutcome lastCommand() {
        return lastCommand;
    }

    public void setStepCount(int stepCount) {
        if (stepCount < 0) {
            throw new IllegalArgumentException("A phase cannot have a negative number of steps.");
        }
        this.stepCount = stepCount;
        updateStepIndex();
    }

    public ModuleStateMachine.CommandOutcome command(BatchCommand command, double simTime) {
        ModuleStateMachine.CommandOutcome outcome = machine.command(command);
        if (outcome.accepted()) {
            if (command == BatchCommand.START) {
                elapsed = 0;
                stepIndex = 0;
                durationSeconds = resolveDuration();
            } else if (command == BatchCommand.RESET) {
                elapsed = 0;
                stepIndex = 0;
                durationSeconds = profile.defaultPhaseSeconds();
                durationExplicit = false;
            }
        }
        lastCommand = outcome;
        publish(simTime);
        return outcome;
    }

    public void tick(double dt, double simTime) {
        if (dt < 0) {
            throw new IllegalArgumentException("A tick cannot move time backwards.");
        }
        readCommand(simTime);
        if (machine.state() == ModuleState.RUNNING) {
            elapsed += dt;
            applyFollowRules(dt, simTime);
            updateStepIndex();
            if (complete()) {
                machine.complete();
            }
        }
        applyFailure();
        machine.tick();
        publish(simTime);
    }

    public void reset(double simTime) {
        machine.reset();
        elapsed = 0;
        stepIndex = 0;
        durationSeconds = profile.defaultPhaseSeconds();
        durationExplicit = false;
        publish(simTime);
    }

    private List<FollowRule> effectiveRules() {
        List<FollowRule> explicit = new ArrayList<>();
        for (FollowRule rule : profile.followRules()) {
            if (rule.moduleId() == null || moduleId.equals(rule.moduleId())) {
                explicit.add(rule);
            }
        }
        if (!explicit.isEmpty()) {
            return List.copyOf(explicit);
        }
        return FollowRuleHeuristic.infer(moduleId, element);
    }

    private void readCommand(double simTime) {
        String name = PlatformVariable.COMMAND.publishedName(moduleId);
        String text = store.text(name);
        if (text == null || text.isBlank() || "NONE".equalsIgnoreCase(text.trim())) {
            return;
        }
        store.set(name, "NONE", simTime);
        BatchCommand command;
        try {
            command = BatchCommand.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            ModuleState state = machine.state();
            lastCommand = new ModuleStateMachine.CommandOutcome(false, state, state,
                    "Unknown command '" + text + "'.");
            return;
        }
        command(command, simTime);
    }

    private void applyFollowRules(double dt, double simTime) {
        for (FollowRule rule : followRules) {
            String report = resolve(S88PlantModel.REPORTS, rule.reportBase());
            String setpoint = resolve(S88PlantModel.PARAMETERS, rule.setpointBase());
            if (report == null || setpoint == null) {
                continue;
            }
            Double target = store.numeric(setpoint);
            if (target == null) {
                continue;
            }
            Double current = store.numeric(report);
            double value = current == null ? rule.initial() : current;
            double delta = target - value;
            double largest = rule.ratePerSecond() * dt;
            double next = Math.abs(delta) <= largest
                    ? target
                    : value + Math.copySign(largest, delta);
            if (Math.abs(target - next) <= rule.tolerance()) {
                next = target;
            }
            if (Double.compare(next, value) != 0) {
                store.set(report, next, simTime);
            }
        }
    }

    private boolean complete() {
        if (followRules.isEmpty()) {
            return elapsed >= durationSeconds;
        }
        for (FollowRule rule : followRules) {
            if (!reached(rule)) {
                return false;
            }
        }
        return !durationExplicit || elapsed >= durationSeconds;
    }

    private boolean reached(FollowRule rule) {
        String report = resolve(S88PlantModel.REPORTS, rule.reportBase());
        String setpoint = resolve(S88PlantModel.PARAMETERS, rule.setpointBase());
        if (report == null || setpoint == null) {
            return false;
        }
        Double value = store.numeric(report);
        Double target = store.numeric(setpoint);
        return value != null && target != null
                && Math.abs(value - target) <= rule.tolerance();
    }

    private double resolveDuration() {
        durationExplicit = false;
        for (String baseName : DURATION_NAMES) {
            String name = resolve(S88PlantModel.PARAMETERS, baseName);
            if (name == null) {
                continue;
            }
            Double value = store.numeric(name);
            if (value != null && value > 0) {
                durationExplicit = true;
                return durationSeconds(name, value);
            }
        }
        return profile.defaultPhaseSeconds();
    }

    private double durationSeconds(String name, double value) {
        ProcessVariable variable = store.get(name);
        return TimeUnits.toSeconds(value, variable == null ? null : variable.unit());
    }

    private String resolve(String container, String baseName) {
        if (element != null && baseName != null) {
            try {
                Optional<String> found = BaseNameResolver.resolveVariable(element, container, baseName);
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (IllegalStateException ambiguous) {
                return null;
            }
        }
        return store.contains(baseName) ? baseName : null;
    }

    private void updateStepIndex() {
        if (stepCount <= 0) {
            stepIndex = 0;
            return;
        }
        stepIndex = (int) Math.floor(progress() * stepCount);
        if (stepIndex > stepCount) {
            stepIndex = stepCount;
        }
    }

    private void applyFailure() {
        Double failure = store.numeric(PlatformVariable.FAILURE.publishedName(moduleId));
        if (failure != null && failure != 0 && machine.state().isActive()
                && machine.state() != ModuleState.ABORTING) {
            machine.fail();
        }
    }

    private void publish(double simTime) {
        store.setIfPresent(PlatformVariable.STATE.publishedName(moduleId),
                machine.state().name(), simTime);
        store.setIfPresent(PlatformVariable.STEP_INDEX.publishedName(moduleId),
                (double) stepIndex, simTime);
        store.setIfPresent(PlatformVariable.STEP_COUNT.publishedName(moduleId),
                (double) stepCount, simTime);
        String elapsedName = resolve(S88PlantModel.REPORTS, "TIEMPO_TRANSCURRIDO");
        if (elapsedName != null) {
            ProcessVariable elapsedVariable = store.get(elapsedName);
            double value = TimeUnits.fromSeconds(elapsed,
                    elapsedVariable == null ? null : elapsedVariable.unit());
            store.setIfPresent(elapsedName, value, simTime);
        }
    }
}
