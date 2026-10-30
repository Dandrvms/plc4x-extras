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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;

/**
 * What the caller asks the batch builder to prepare.
 *
 * @param equipmentOverrides step element id or class id mapped to the instance id to use
 */
public record BatchRequest(S88MasterRecipe master, S88PlantSnapshot plant, String batchId,
                           Map<String, String> equipmentOverrides, SimulationProfile profile) {

    public BatchRequest {
        Map<String, String> copy = new LinkedHashMap<>();
        if (equipmentOverrides != null) {
            equipmentOverrides.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
        }
        equipmentOverrides = Collections.unmodifiableMap(copy);
    }
}
