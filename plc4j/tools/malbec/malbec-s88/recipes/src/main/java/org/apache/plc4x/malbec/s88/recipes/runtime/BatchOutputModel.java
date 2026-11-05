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

import java.util.ArrayList;
import java.util.List;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.EventType;

/** Keeps and filters the events the output window shows. */
final class BatchOutputModel {

    enum Filter {
        ALL,
        COMMANDS,
        STATES,
        PHASES,
        TRANSITIONS,
        ALARMS
    }

    private final List<BatchEvent> events = new ArrayList<>();
    private Filter filter = Filter.ALL;

    void add(BatchEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    void addAll(List<BatchEvent> more) {
        if (more == null) {
            return;
        }
        for (BatchEvent event : more) {
            add(event);
        }
    }

    void clear() {
        events.clear();
    }

    void filter(Filter next) {
        filter = next == null ? Filter.ALL : next;
    }

    Filter filter() {
        return filter;
    }

    List<BatchEvent> visible() {
        List<BatchEvent> shown = new ArrayList<>();
        for (BatchEvent event : events) {
            if (matches(filter, event)) {
                shown.add(event);
            }
        }
        return shown;
    }

    static boolean matches(Filter filter, BatchEvent event) {
        if (event == null || filter == null || filter == Filter.ALL) {
            return event != null;
        }
        return switch (filter) {
            case ALL -> true;
            case COMMANDS -> event.type() == EventType.COMMAND_ACCEPTED
                    || event.type() == EventType.COMMAND_REJECTED;
            case STATES -> event.type() == EventType.BATCH_CREATED
                    || event.type() == EventType.BATCH_STARTED
                    || event.type() == EventType.STATE_CHANGED
                    || event.type() == EventType.BATCH_COMPLETED
                    || event.type() == EventType.BATCH_ABORTED
                    || event.type() == EventType.BATCH_STOPPED
                    || event.type() == EventType.BATCH_RESET;
            case PHASES -> event.type() == EventType.STEP_ACTIVATED
                    || event.type() == EventType.STEP_COMPLETED
                    || event.type() == EventType.PARAMETER_DOWNLOADED;
            case TRANSITIONS -> event.type() == EventType.TRANSITION_FIRED
                    || event.type() == EventType.DECISION_NEEDED
                    || event.type() == EventType.DECISION_TAKEN;
            case ALARMS -> alarm(event);
        };
    }

    static boolean alarm(BatchEvent event) {
        return event != null && (event.type() == EventType.COMMAND_REJECTED
                || event.type() == EventType.FAILURE_INJECTED
                || event.type() == EventType.CONNECTION_LOST
                || event.type() == EventType.LOOP_GUARD_TRIPPED
                || event.type() == EventType.BATCH_ABORTED
                || event.type() == EventType.BATCH_STOPPED);
    }
}
