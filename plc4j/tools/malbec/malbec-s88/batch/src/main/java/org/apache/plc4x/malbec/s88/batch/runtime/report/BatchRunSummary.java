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
package org.apache.plc4x.malbec.s88.batch.runtime.report;

import java.time.Instant;
import java.util.List;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;

/** Everything the report needs to describe one run. */
public record BatchRunSummary(BatchPayload payload, int run, String outcome,
                              Instant startWallTime, Instant endWallTime,
                              double startSimTime, double endSimTime,
                              List<BatchEvent> events, List<ValuesRecorder.Sample> values) {

    public BatchRunSummary {
        events = events == null ? List.of() : List.copyOf(events);
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** @return a copy with other events, keeping the run metadata */
    public BatchRunSummary withEvents(List<BatchEvent> otherEvents) {
        return new BatchRunSummary(payload, run, outcome, startWallTime, endWallTime,
                startSimTime, endSimTime, otherEvents, values);
    }
}
