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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchMode;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.PendingDecision;
import org.apache.plc4x.malbec.s88.batch.model.StepStatus;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.recipes.editor.RecipeChartScene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeViewModelTest {

    private static S88MasterRecipe recipe() {
        return CreateMasterRecipeUseCase.execute("DEMO", S88RecipeKind.CLASS);
    }

    private static BatchSnapshot snapshot() {
        StepStatus active = new StepStatus("", "BEGIN", "BEGIN", null,
                ModuleState.RUNNING, 0.45, 0, 0);
        VariableSample temperature = new VariableSample("TEMPERATURA_ACTUAL", 72.0,
                DataType.REAL, "C", Instant.now(), Quality.GOOD, "plc");
        return new BatchSnapshot("BATCH", 1, BatchMode.SIMULATED, ModuleState.RUNNING, true,
                42.0, 1.0, List.of(active), Set.of("/END"), Set.of("/T1"),
                Map.of("TEMPERATURA_ACTUAL", temperature), List.of(), PendingDecision.NONE);
    }

    @Test
    void theChartStateFollowsTheSnapshot() {
        RuntimeViewModel model = new RuntimeViewModel(recipe(), null);

        RuntimeViewModel.ChartState state = model.chartState(null, snapshot());

        assertEquals(RecipeChartScene.ExecState.ACTIVE, state.states().get("BEGIN"));
        assertEquals(RecipeChartScene.ExecState.DONE, state.states().get("END"));
        assertTrue(state.firedTransitions().contains("T1"));
        assertTrue(state.badges().get("BEGIN").contains("45%"));
    }

    @Test
    void theFollowTargetIsTheDeepestActiveChart() {
        RuntimeViewModel model = new RuntimeViewModel(recipe(), null);
        BatchSnapshot snapshot = new BatchSnapshot("BATCH", 1, BatchMode.SIMULATED,
                ModuleState.RUNNING, true, 1.0, 1.0,
                List.of(new StepStatus("", "UP_REACTOR", "UP_REACTOR", null,
                                ModuleState.RUNNING, 0.2, 0, 0),
                        new StepStatus("UP_REACTOR", "CARGAR", "BOMBA_1", "BOMBA_1",
                                ModuleState.RUNNING, 0.6, 2, 5)),
                Set.of(), Set.of(), Map.of(), List.of(), PendingDecision.NONE);

        assertEquals("UP_REACTOR", model.activeChartPath(snapshot),
                "because the phase below the unit procedure is the one to follow");
        assertEquals("CARGAR (2/5)", model.activeStepLabel(snapshot));
    }

    @Test
    void theCommandsAreTheOnesTheObservedStateAccepts() {
        RuntimeViewModel model = new RuntimeViewModel(recipe(), null);

        Set<BatchCommand> commands = model.commands(snapshot());

        assertTrue(commands.contains(BatchCommand.PAUSE));
        assertTrue(commands.contains(BatchCommand.HOLD));
        assertTrue(commands.contains(BatchCommand.ABORT));
        assertFalse(commands.contains(BatchCommand.START));
    }

    @Test
    void theVariablesBecomeTableRows() {
        RuntimeViewModel model = new RuntimeViewModel(recipe(), null);

        List<RuntimeViewModel.VariableRow> rows = model.variables(snapshot(), "");

        assertEquals(1, rows.size());
        assertEquals("TEMPERATURA_ACTUAL", rows.getFirst().variable());
        assertEquals("72.0", rows.getFirst().value());
        assertEquals("C", rows.getFirst().unit());
        assertEquals(Quality.GOOD, rows.getFirst().quality());
    }
}
