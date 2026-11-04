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

import org.apache.plc4x.malbec.s88.batch.model.ModuleState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeClosePolicyTest {

    @Test
    void anActiveBatchAsksBeforeClosing() {
        assertTrue(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.RUNNING));
        assertTrue(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.PAUSED));
        assertTrue(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.HELD));
        assertTrue(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.ABORTING));
    }

    @Test
    void anIdleOrFinishedBatchClosesWithoutAsking() {
        assertFalse(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.IDLE));
        assertFalse(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.COMPLETE));
        assertFalse(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.ABORTED));
        assertFalse(BatchRuntimeTopComponent.needsCloseConfirmation(ModuleState.STOPPED));
    }
}
