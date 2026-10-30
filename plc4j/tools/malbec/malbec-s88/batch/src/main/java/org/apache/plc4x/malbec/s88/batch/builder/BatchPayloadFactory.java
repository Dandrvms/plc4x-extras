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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88ControlRecipe;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.apache.plc4x.malbec.s88.core.SetControlRecipeUseCase;

/** Turns a batch request into a payload for the simulated PLC. */
public final class BatchPayloadFactory {

    private BatchPayloadFactory() {
        /* This utility class should not be instantiated */
    }

    public static PrepareResult prepare(BatchRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        PayloadValidator.validateRequest(request, errors);
        if (request == null || request.master() == null || request.plant() == null) {
            return new PrepareResult(null, errors, warnings);
        }

        S88MasterRecipe master = request.master();
        S88PlantSnapshot plant = request.plant();
        String batchId = request.batchId();

        RecipeConformance conformance = RecipeConformance.of(master, plant);
        errors.addAll(conformance.excess());
        errors.addAll(conformance.deficit());
        warnings.addAll(conformance.warnings());

        List<PayloadValidator.ChartRef> charts = PayloadValidator.charts(master, errors);
        PayloadValidator.validateCharts(charts, errors);

        S88ControlRecipe control = null;
        if (batchId != null && !batchId.isBlank()) {
            try {
                control = new SetControlRecipeUseCase().from(master, batchId);
            } catch (IllegalArgumentException cannotSet) {
                errors.add(cannotSet.getMessage());
            }
        }

        EquipmentAllocator.Allocation allocation = EquipmentAllocator.allocate(master, plant,
                request.equipmentOverrides(), charts, errors);
        Map<String, PreparedCondition> conditions = PayloadValidator.prepareConditions(master,
                plant, charts, allocation, errors);
        Map<String, List<PreparedSetpoint>> setpoints = PayloadValidator.prepareSetpoints(master,
                plant, charts, allocation, warnings, errors);

        SimulationProfile profile = request.profile() != null
                ? request.profile()
                : SimulationProfile.defaults();
        PayloadValidator.validateProfile(profile, plant, allocation, errors);

        if (!errors.isEmpty()) {
            return new PrepareResult(null, errors, warnings);
        }
        BatchPayload payload = new BatchPayload(batchId, master.getId(), master.getVersion(),
                Instant.now(), control, plant, allocation.moduleOfStep(), allocation.unitOfStep(),
                conditions, setpoints, profile, warnings);
        return new PrepareResult(payload, errors, warnings);
    }
}
