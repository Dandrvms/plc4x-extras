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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.PlatformVariables;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommands;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchMode;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.PendingDecision;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;
import org.apache.plc4x.malbec.s88.batch.simulator.time.SimClock;
import org.apache.plc4x.malbec.s88.batch.simulator.values.ProcessVariable;
import org.apache.plc4x.malbec.s88.batch.simulator.values.VariableStore;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;

/**
 * The simulated batch server: it ticks the plant, moves the sequence and answers operator commands.
 */
public final class SimulationEngine {

    private static final String SIMULATOR = "simulator";
    private static final String OPERATOR = "operator";
    private static final int MAX_EVENTS = 10_000;

    private final Clock clock;
    private final double maxActivationsPerStep;
    private final SimClock simClock = new SimClock();
    private final List<String> warnings = new ArrayList<>();
    private final List<BatchEvent> events = new ArrayList<>();
    private final Set<String> reportedFailures = new LinkedHashSet<>();

    private BatchPayload payload;
    private SimulationProfile profile;
    private ExitPolicy exitPolicy;
    private VariableStore store;
    private Map<String, SimulatedModule> modules = new LinkedHashMap<>();
    private EngineDecisionProvider decisions;
    private SequenceEngine sequence;
    private int run;
    private long seq;
    private double simTime;
    private Instant wallStart;
    private ModuleState batchState = ModuleState.IDLE;
    private boolean started;
    private boolean sequenceComplete;
    private boolean abortRequested;
    private boolean stopRequested;
    private boolean loaded;

    public SimulationEngine(BatchPayload payload, SimulationProfile profile) {
        this(payload, profile, Clock.systemUTC(), DecisionMode.AUTO,
                ExitPolicy.COMPLETE_ON_EXIT);
    }

    public SimulationEngine(BatchPayload payload, SimulationProfile profile, Clock clock) {
        this(payload, profile, clock, DecisionMode.AUTO, ExitPolicy.COMPLETE_ON_EXIT);
    }

    public SimulationEngine(BatchPayload payload, SimulationProfile profile, Clock clock,
                            DecisionMode decisionMode) {
        this(payload, profile, clock, decisionMode, ExitPolicy.COMPLETE_ON_EXIT);
    }

    public SimulationEngine(BatchPayload payload, SimulationProfile profile, Clock clock,
                            DecisionMode decisionMode, ExitPolicy exitPolicy) {
        this(payload, profile, clock, decisionMode, exitPolicy, 1000.0);
    }

    public SimulationEngine(BatchPayload payload, SimulationProfile profile, Clock clock,
                            DecisionMode decisionMode, ExitPolicy exitPolicy,
                            double maxActivationsPerStep) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxActivationsPerStep = maxActivationsPerStep;
        this.payload = payload;
        this.profile = profile != null ? profile
                : (payload == null || payload.profile() == null
                        ? SimulationProfile.defaults() : payload.profile());
        load(exitPolicy, decisionMode);
    }

    /** Replaces the recipe and plant while keeping the same clock and run number. */
    public void reload(BatchPayload newPayload) {
        if (newPayload == null) {
            throw new IllegalArgumentException("A reload needs a batch payload.");
        }
        BatchPayload old = payload;
        int currentRun = run;
        payload = newPayload;
        profile = newPayload.profile() != null ? newPayload.profile()
                : SimulationProfile.defaults();
        load(exitPolicy == null ? ExitPolicy.COMPLETE_ON_EXIT : exitPolicy,
                decisions == null ? DecisionMode.AUTO : decisions.mode());
        run = currentRun;
        if (old != null && old.batchId() != null) {
            emit(EventType.BATCH_CREATED, "simulator", newPayload.batchId(), "IDLE",
                    old.batchId(), newPayload.batchId(), "The batch was reloaded.");
        }
    }

    public void tick() {
        tick(profile.tickSeconds());
    }

    /** Advances the plant by exactly {@code dt} simulated seconds. */
    public void tick(double dt) {
        ensureLoaded();
        if (dt < 0) {
            throw new IllegalArgumentException("A tick cannot go backwards.");
        }
        simClock.advance(dt);
        simTime = simClock.now();
        Map<String, ModuleState> before = moduleStates();
        for (SimulatedModule module : modules.values()) {
            module.tick(started ? dt : 0, simTime);
        }
        detectExternalFailures();
        emitModuleStateChanges(before);
        ModuleState beforeSequence = BatchStateDeriver.derive(started, sequenceComplete,
                abortRequested, stopRequested, before.values());
        sequence.tick(simTime, beforeSequence == ModuleState.RUNNING);
        if (sequence.abortRequested() && !abortRequested) {
            abortRequested = true;
            decisions.clear();
            sendToActiveModules(BatchCommand.ABORT, simTime);
        }
        sequenceComplete = sequence.started() && sequence.root() != null
                && sequence.root().isComplete();
        ModuleState after = BatchStateDeriver.derive(started, sequenceComplete, abortRequested,
                stopRequested, moduleStates().values());
        transitionTo(after);
    }

    public CommandResult command(BatchCommand command) {
        ensureLoaded();
        if (command == null) {
            return reject("No command was given.");
        }
        boolean allowed = command == BatchCommand.RESET
                ? true
                : BatchCommands.allows(batchState, command);
        if (!allowed) {
            return reject(command + " is not valid in state " + batchState + ".");
        }
        CommandResult result;
        switch (command) {
            case START -> {
                if (started) {
                    return reject("The batch has already started.");
                }
                started = true;
                sequenceComplete = false;
                abortRequested = false;
                stopRequested = false;
                sequence.start();
                transitionTo(ModuleState.RUNNING);
                emit(EventType.BATCH_STARTED, "batch", payload.batchId(), "RUNNING",
                        null, null, "The batch started.");
                result = new CommandResult(true, null, batchState);
            }
            case RESUME -> {
                if (!started) {
                    return reject("The batch has not started.");
                }
                sendToActiveModules(BatchCommand.RESUME, simTime);
                transitionTo(ModuleState.RUNNING);
                result = new CommandResult(true, null, batchState);
            }
            case PAUSE -> {
                if (!started) {
                    return reject("The batch has not started.");
                }
                sendToActiveModules(BatchCommand.PAUSE, simTime);
                transitionTo(ModuleState.PAUSING);
                result = new CommandResult(true, null, batchState);
            }
            case HOLD -> {
                if (!started) {
                    return reject("The batch has not started.");
                }
                sendToActiveModules(BatchCommand.HOLD, simTime);
                transitionTo(ModuleState.HOLDING);
                result = new CommandResult(true, null, batchState);
            }
            case ABORT -> {
                if (!started) {
                    return reject("The batch has not started.");
                }
                abortRequested = true;
                stopRequested = false;
                decisions.clear();
                sendToActiveModules(BatchCommand.ABORT, simTime);
                transitionTo(ModuleState.ABORTING);
                result = new CommandResult(true, null, batchState);
            }
            case STOP -> {
                if (!started) {
                    return reject("The batch has not started.");
                }
                stopRequested = true;
                decisions.clear();
                sendToActiveModules(BatchCommand.STOP, simTime);
                transitionTo(ModuleState.STOPPING);
                result = new CommandResult(true, null, batchState);
            }
            case RESTART -> {
                if (!started) {
                    return reject("The batch has not started.");
                }
                sendToActiveModules(BatchCommand.RESTART, simTime);
                transitionTo(ModuleState.RESTARTING);
                result = new CommandResult(true, null, batchState);
            }
            case RESET -> {
                resetRun();
                result = new CommandResult(true, null, batchState);
            }
            default -> {
                return reject(command + " is not a command this simulator understands.");
            }
        }
        emit(EventType.COMMAND_ACCEPTED, OPERATOR, payload.batchId(), batchState.name(),
                null, command.name(), "The command " + command + " was accepted.");
        return result;
    }

    public boolean decide(String transitionId) {
        ensureLoaded();
        if (!decisions.decide(transitionId)) {
            return false;
        }
        emit(EventType.DECISION_TAKEN, OPERATOR, transitionId, null,
                null, transitionId, "The operator chose transition '" + transitionId + "'.");
        return true;
    }

    public void injectFailure(String moduleId, int code) {
        ensureLoaded();
        if (moduleId == null || !modules.containsKey(moduleId)) {
            throw new IllegalArgumentException("The plant has no simulated module '" + moduleId + "'.");
        }
        if (code == 0) {
            clearFailure(moduleId);
            return;
        }
        store.set(PlatformVariable.FAILURE.publishedName(moduleId), (double) code, simTime);
        reportedFailures.add(moduleId);
        emit(EventType.FAILURE_INJECTED, "module", moduleId, null, "0",
                Integer.toString(code), "Failure " + code + " was injected into '" + moduleId + "'.");
    }

    public void clearFailure(String moduleId) {
        ensureLoaded();
        if (moduleId == null || !modules.containsKey(moduleId)) {
            throw new IllegalArgumentException("The plant has no simulated module '" + moduleId + "'.");
        }
        Double before = store.numeric(PlatformVariable.FAILURE.publishedName(moduleId));
        store.set(PlatformVariable.FAILURE.publishedName(moduleId), 0.0, simTime);
        reportedFailures.remove(moduleId);
        emit(EventType.VALUE_CHANGED, "module", moduleId, null, number(before), "0",
                "Failure cleared on '" + moduleId + "'.");
    }

    public BatchSnapshot snapshot() {
        ensureLoaded();
        return new BatchSnapshot(payload.batchId(), run, BatchMode.SIMULATED, batchState, true,
                simTime, wallElapsedSeconds(), sequence.stepStatuses(), sequence.completedStepKeys(),
                sequence.firedTransitionKeys(), variables(), List.of(), sequence.pendingDecision());
    }

    /** Writes a value into the simulated plant. */
    public void setVariable(String publishedName, Object value) {
        ensureLoaded();
        store.set(publishedName, value, simTime);
    }

    public double tickSeconds() {
        return profile.tickSeconds();
    }

    /** The variables of the plant, as the bridge publishes them. */
    public Map<String, ProcessVariable> snapshotVariables() {
        ensureLoaded();
        return store.variables();
    }

    public void setDecisionMode(DecisionMode mode) {
        ensureLoaded();
        decisions.setMode(mode);
    }

    public List<BatchEvent> drainEvents() {
        List<BatchEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public double simTime() {
        return simTime;
    }

    public ModuleState batchState() {
        return batchState;
    }

    public Map<String, ModuleState> moduleStates() {
        Map<String, ModuleState> states = new LinkedHashMap<>();
        modules.forEach((id, module) -> states.put(id, module.state()));
        return states;
    }

    public DecisionMode decisionMode() {
        return decisions == null ? DecisionMode.AUTO : decisions.mode();
    }

    private void load(ExitPolicy exitPolicy, DecisionMode decisionMode) {
        if (payload == null) {
            throw new IllegalArgumentException("A simulation needs a batch payload.");
        }
        warnings.clear();
        warnings.addAll(payload.warnings());
        this.exitPolicy = exitPolicy == null ? ExitPolicy.COMPLETE_ON_EXIT : exitPolicy;
        if (payload.control() == null) {
            throw new IllegalArgumentException("A simulation needs a control recipe.");
        }
        Set<String> moduleIds = new LinkedHashSet<>(payload.moduleOfStep().values());
        for (String moduleId : profile.followRules().stream()
                .map(rule -> rule.moduleId()).toList()) {
            if (moduleId != null && !moduleId.isBlank()) {
                moduleIds.add(moduleId);
            }
        }
        if (moduleIds.isEmpty()) {
            for (S88Element module : PlatformVariables.modulesOf(payload.plant().getRoot())) {
                moduleIds.add(module.getId());
            }
        }
        store = VariableStore.of(payload.plant(), moduleIds, warnings);
        modules = new LinkedHashMap<>();
        for (String moduleId : moduleIds) {
            S88Element element = payload.plant().findById(moduleId).orElse(null);
            if (element != null && element.getLevel() == S88Level.EQUIPMENTMODULE) {
                modules.put(moduleId, new SimulatedModule(moduleId, element, profile, store));
            }
        }
        decisions = new EngineDecisionProvider(decisionMode);
        sequence = new SequenceEngine(payload, payload.control(), store, modules, decisions,
                exitPolicy, warnings, this::emit, maxActivationsPerStep);
        events.clear();
        reportedFailures.clear();
        seq = 0;
        simTime = 0;
        simClock.reset();
        wallStart = clock.instant();
        run = 1;
        batchState = ModuleState.IDLE;
        started = false;
        sequenceComplete = false;
        abortRequested = false;
        stopRequested = false;
        loaded = true;
    }

    private void resetRun() {
        ModuleState old = batchState;
        run++;
        seq = 0;
        simTime = 0;
        simClock.reset();
        wallStart = clock.instant();
        sequence.reset();
        store.reset(simTime);
        for (SimulatedModule module : modules.values()) {
            module.reset(simTime);
        }
        decisions.clear();
        reportedFailures.clear();
        started = false;
        sequenceComplete = false;
        abortRequested = false;
        stopRequested = false;
        batchState = ModuleState.IDLE;
        if (old != ModuleState.IDLE) {
            emit(EventType.STATE_CHANGED, "batch", payload.batchId(), "IDLE", old.name(),
                    "IDLE", "The batch was reset.");
        }
        emit(EventType.BATCH_RESET, "batch", payload.batchId(), "IDLE", old.name(), "IDLE",
                "The batch returned to idle.");
    }

    private void sendToActiveModules(BatchCommand command, double simTime) {
        for (SimulatedModule module : modules.values()) {
            if (module.state().isActive()) {
                store.setIfPresent(PlatformVariable.COMMAND.publishedName(module.moduleId()),
                        command.name(), simTime);
            }
        }
    }

    private void detectExternalFailures() {
        boolean failed = false;
        for (SimulatedModule module : modules.values()) {
            Double failure = store.numeric(PlatformVariable.FAILURE.publishedName(
                    module.moduleId()));
            if (failure == null || failure == 0) {
                reportedFailures.remove(module.moduleId());
                continue;
            }
            failed = true;
            if (reportedFailures.add(module.moduleId())) {
                emit(EventType.FAILURE_INJECTED, "module", module.moduleId(), null, "0",
                        number(failure), "Failure " + number(failure) + " was injected into '"
                                + module.moduleId() + "'.");
            }
        }
        if (failed && !abortRequested) {
            abortRequested = true;
            decisions.clear();
            sendToActiveModules(BatchCommand.ABORT, simTime);
        }
    }

    private void emitModuleStateChanges(Map<String, ModuleState> before) {
        Map<String, ModuleState> after = moduleStates();
        for (Map.Entry<String, ModuleState> entry : after.entrySet()) {
            ModuleState old = before.get(entry.getKey());
            if (old != entry.getValue()) {
                emit(EventType.STATE_CHANGED, "module", entry.getKey(), entry.getValue().name(),
                        old == null ? null : old.name(), entry.getValue().name(),
                        "Module '" + entry.getKey() + "' changed state.");
            }
        }
    }

    private void transitionTo(ModuleState next) {
        if (next == null || next == batchState) {
            return;
        }
        ModuleState old = batchState;
        batchState = next;
        emit(EventType.STATE_CHANGED, "batch", payload.batchId(), next.name(), old.name(),
                next.name(), "The batch changed state.");
        switch (next) {
            case COMPLETE -> emit(EventType.BATCH_COMPLETED, "batch", payload.batchId(),
                    "COMPLETE", old.name(), "COMPLETE", "The batch completed.");
            case ABORTED -> emit(EventType.BATCH_ABORTED, "batch", payload.batchId(),
                    "ABORTED", old.name(), "ABORTED", "The batch aborted.");
            case STOPPED -> emit(EventType.BATCH_STOPPED, "batch", payload.batchId(),
                    "STOPPED", old.name(), "STOPPED", "The batch stopped.");
            default -> {
            }
        }
    }

    private CommandResult reject(String reason) {
        emit(EventType.COMMAND_REJECTED, OPERATOR, payload.batchId(), batchState.name(), null,
                null, reason);
        return new CommandResult(false, reason, batchState);
    }

    private Map<String, VariableSample> variables() {
        Instant sourceTime = clock.instant();
        Map<String, VariableSample> samples = new LinkedHashMap<>();
        for (ProcessVariable variable : store.variables().values()) {
            samples.put(variable.name(), new VariableSample(variable.name(), variable.value(),
                    variable.type(), variable.unit(), sourceTime, Quality.GOOD, SIMULATOR));
        }
        return samples;
    }

    private double wallElapsedSeconds() {
        if (wallStart == null) {
            return 0;
        }
        double seconds = Duration.between(wallStart, clock.instant()).toMillis() / 1000.0;
        return Math.max(0, seconds);
    }

    private void emit(EventType type, String source, String elementId, String state,
                      String oldValue, String newValue, String details) {
        events.add(new BatchEvent(seq++, payload == null ? null : payload.batchId(), run,
                clock.instant(), simTime, type, source, elementId, state, oldValue, newValue,
                details));
        while (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
    }

    private void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("The simulation engine has not been loaded.");
        }
    }

    private static String number(Double value) {
        return value == null ? null : value.toString();
    }
}
