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
package org.apache.plc4x.malbec.s88.recipes.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.simulator.SimulationControl;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableCatalog;
import org.apache.plc4x.malbec.s88.batch.model.BatchMode;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;
import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.apache.plc4x.malbec.s88.batch.model.PendingDecision;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;
import org.junit.jupiter.api.Test;

class SimulationControlPanelTest {

    @Test
    void writesNumericTextAndEnumerationValuesThroughTheControl() {
        onSwingThread(() -> {
            FakeControl control = new FakeControl();
            SimulationControlPanel panel = panel(control);

            assertTrue(panel.applyVariable("TEMPERATURA_ACTUAL", "75.5"));
            assertEquals("TEMPERATURA_ACTUAL", control.name);
            assertEquals(75.5, control.value);

            assertTrue(panel.applyVariable("CANTIDAD", "12"));
            assertEquals(12L, control.value);

            assertTrue(panel.applyVariable("ESTADO", "RUNNING"));
            assertEquals("RUNNING", control.value);


        });
    }

    @Test
    void refusesAValueTheVariableCannotHold() {
        onSwingThread(() -> {
            FakeControl control = new FakeControl();
            SimulationControlPanel panel = panel(control);

            assertFalse(panel.applyVariable("TEMPERATURA_ACTUAL", "warm"));
            assertFalse(panel.applyVariable("CANTIDAD", "1.5"));
            assertFalse(panel.applyVariable("ESTADO", "missing"));


        });
    }

    @Test
    void setpointsAndCommandsAreNotEditableFromTheSimulator() {
        onSwingThread(() -> {
            FakeControl control = new FakeControl();
            SimulationControlPanel panel = panel(control);

            assertFalse(panel.applyVariable("SP_NIVEL_TQ_1", "BAJO"));
            assertFalse(panel.applyVariable("COMMAND_NIVEL_TQ_1", "START"));
            assertNull(control.name);
        });
    }

    private static SimulationControlPanel panel(SimulationControl control) {
        Map<String, VariableCatalog.Definition> definitions = Map.of(
                "TEMPERATURA_ACTUAL",
                new VariableCatalog.Definition("TEMPERATURA_ACTUAL", DataType.REAL, "C", List.of()),
                "CANTIDAD",
                new VariableCatalog.Definition("CANTIDAD", DataType.INTEGER, "KG", List.of()),
                "ESTADO",
                new VariableCatalog.Definition("ESTADO", DataType.ENUMERATION, null,
                        List.of("IDLE", "RUNNING", "COMPLETE")),
                "SP_NIVEL_TQ_1",
                new VariableCatalog.Definition("SP_NIVEL_TQ_1", DataType.ENUMERATION, null,
                        List.of("ALTO", "BAJO")),
                "COMMAND_NIVEL_TQ_1",
                new VariableCatalog.Definition("COMMAND_NIVEL_TQ_1", DataType.ENUMERATION, null,
                        List.of("NONE", "START")));
        return new SimulationControlPanel(control, definitions, List.of(), null, snapshot());
    }

    private static BatchSnapshot snapshot() {
        return new BatchSnapshot("BATCH", 1, BatchMode.SIMULATED, ModuleState.RUNNING, true,
                0.0, 0.0, List.of(), java.util.Set.of(), java.util.Set.of(),
                Map.of("TEMPERATURA_ACTUAL", new VariableSample("TEMPERATURA_ACTUAL", 20.0,
                        DataType.REAL, "C", Instant.EPOCH, Quality.GOOD, "test")),
                List.of(), PendingDecision.NONE);
    }

    private static void onSwingThread(Runnable what) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    what.run();
                } catch (RuntimeException | AssertionError problem) {
                    failure.set(problem);
                }
            });
        } catch (InterruptedException | java.lang.reflect.InvocationTargetException problem) {
            throw new AssertionError("the check could not be run on the right thread", problem);
        }
        if (failure.get() instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure.get() instanceof AssertionError error) {
            throw error;
        }
    }

    private static final class FakeControl implements SimulationControl {

        private String name;
        private Object value;

        @Override
        public void setSpeed(double factor) {
        }

        @Override
        public void play() {
        }

        @Override
        public void pauseClock() {
        }

        @Override
        public void stepTicks(int ticks) {
        }

        @Override
        public boolean stepToNextEvent() {
            return true;
        }

        @Override
        public boolean isClockRunning() {
            return true;
        }

        @Override
        public double speed() {
            return 1.0;
        }

        @Override
        public void setAutomatic(boolean automatic) {
        }

        @Override
        public void completeStep(String moduleId) {
        }

        @Override
        public void setVariable(String publishedName, Object value) {
            this.name = publishedName;
            this.value = value;
        }

        @Override
        public void injectFailure(String moduleId, int code) {
        }

        @Override
        public void clearFailure(String moduleId) {
        }

        @Override
        public void setLinkUp(boolean up) {
        }

        @Override
        public void setDecisionMode(DecisionMode mode) {
        }

        @Override
        public void decide(String transitionId) {
        }
    }
}
