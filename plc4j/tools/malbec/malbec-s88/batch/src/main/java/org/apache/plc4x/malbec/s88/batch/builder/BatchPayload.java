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
package org.apache.plc4x.malbec.s88.batch.builder;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88ControlRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;

/**
 * Everything the simulated PLC needs to run one batch.
 *
 * @param moduleOfStep phase keys mapped to module ids
 * @param unitOfStep   unit procedure keys mapped to unit ids
 * @param conditions   guarded transition keys mapped to prepared conditions
 * @param setpoints    step keys mapped to the parameters to write
 */
public record BatchPayload(String batchId, String sourceRecipeId, String recipeVersion,
                           Instant createdAt, S88ControlRecipe control, S88PlantSnapshot plant,
                           Map<String, String> moduleOfStep, Map<String, String> unitOfStep,
                           Map<String, PreparedCondition> conditions,
                           Map<String, List<PreparedSetpoint>> setpoints,
                           SimulationProfile profile, List<String> warnings) {

    public BatchPayload {
        moduleOfStep = immutableMap(moduleOfStep);
        unitOfStep = immutableMap(unitOfStep);
        conditions = immutableMap(conditions);
        Map<String, List<PreparedSetpoint>> copiedSetpoints = new LinkedHashMap<>();
        if (setpoints != null) {
            setpoints.forEach((key, value) -> copiedSetpoints.put(key, List.copyOf(value)));
        }
        setpoints = Collections.unmodifiableMap(copiedSetpoints);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private static <V> Map<String, V> immutableMap(Map<String, V> source) {
        Map<String, V> copy = new LinkedHashMap<>();
        if (source != null) {
            copy.putAll(source);
        }
        return Collections.unmodifiableMap(copy);
    }
}
