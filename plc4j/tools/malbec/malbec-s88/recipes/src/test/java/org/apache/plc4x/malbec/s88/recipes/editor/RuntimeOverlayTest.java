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
package org.apache.plc4x.malbec.s88.recipes.editor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeOverlayTest {

    @Test
    void theOverlayMarksActiveDoneAndFiredNodesWithoutChangingTheLayout() {
        onSwingThread(() -> {
            S88MasterRecipe recipe = SfcFixtures.lineal();
            RecipeChartScene editor = RecipeChartScene.forRecipe(recipe);
            RecipeChartScene runtime = RecipeChartScene.forRecipe(recipe);

            runtime.setReadOnly(true);
            runtime.setExecution(Map.of("BEGIN", RecipeChartScene.ExecState.DONE,
                    "MIX", RecipeChartScene.ExecState.ACTIVE),
                    Set.of("T_MIX"), false);

            assertEquals(editor.positions(), runtime.positions(),
                    "the run is drawn where the editor drew it");
            assertEquals(RecipeChartScene.ExecState.DONE, runtime.executionOf("BEGIN"));
            assertEquals(RecipeChartScene.ExecState.ACTIVE, runtime.executionOf("MIX"));
            assertTrue(runtime.wasFired("T_MIX"));
            assertFalse(runtime.isStale());
        });
    }

    @Test
    void staleIsCarriedToTheDrawing() {
        onSwingThread(() -> {
            RecipeChartScene runtime = RecipeChartScene.forRecipe(SfcFixtures.lineal());

            runtime.setExecution(Map.of(), Set.of(), true);

            assertTrue(runtime.isStale());
        });
    }

    private static void onSwingThread(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    action.run();
                } catch (Throwable problem) {
                    failure.set(problem);
                }
            });
        } catch (InterruptedException | java.lang.reflect.InvocationTargetException problem) {
            throw new AssertionError("the chart check could not run on the Event Dispatch Thread",
                    problem);
        }
        if (failure.get() instanceof AssertionError assertion) {
            throw assertion;
        }
        if (failure.get() != null) {
            throw new AssertionError("the chart check failed", failure.get());
        }
    }
}
