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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.junit.jupiter.api.Test;

class BatchOutputModelTest {

    @Test
    void filtersEventsByTheKindOfThingThatHappened() {
        BatchOutputModel model = new BatchOutputModel();
        model.add(event(EventType.STEP_ACTIVATED));
        model.add(event(EventType.TRANSITION_FIRED));
        model.add(event(EventType.COMMAND_REJECTED));
        model.add(event(EventType.LOOP_GUARD_TRIPPED));

        assertEquals(4, model.visible().size());

        model.filter(BatchOutputModel.Filter.PHASES);
        assertEquals(List.of(EventType.STEP_ACTIVATED),
                model.visible().stream().map(BatchEvent::type).toList());

        model.filter(BatchOutputModel.Filter.TRANSITIONS);
        assertEquals(List.of(EventType.TRANSITION_FIRED),
                model.visible().stream().map(BatchEvent::type).toList());

        model.filter(BatchOutputModel.Filter.ALARMS);
        assertEquals(List.of(EventType.COMMAND_REJECTED, EventType.LOOP_GUARD_TRIPPED),
                model.visible().stream().map(BatchEvent::type).toList());
    }

    @Test
    void aLoopGuardIsAnAlarmAndAnOrdinaryStepIsNot() {
        assertTrue(BatchOutputModel.alarm(event(EventType.LOOP_GUARD_TRIPPED)));
        assertTrue(BatchOutputModel.alarm(event(EventType.FAILURE_INJECTED)));
        assertFalse(BatchOutputModel.alarm(event(EventType.STEP_ACTIVATED)));
    }

    private static BatchEvent event(EventType type) {
        return new BatchEvent(1, "BATCH", 1, Instant.EPOCH, 1.0, type, "sequence",
                "STEP", null, null, null, "detail");
    }
}
