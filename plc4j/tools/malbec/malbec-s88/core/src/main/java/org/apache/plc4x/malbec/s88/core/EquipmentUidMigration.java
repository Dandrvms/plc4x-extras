/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;

/**
 * Repairs recipes saved while a plant snapshot was copying elements with fresh identities.
 */
public final class EquipmentUidMigration {

    private EquipmentUidMigration() {
    }

    /**
     * Replaces a stale equipment uid with the uid of the equipment named by the step.
     *
     * @return {@code true} when a uid was repaired
     */
    public static boolean repair(S88MasterRecipe recipe, S88PlantSnapshot plant) {
        return repair(recipe, plant == null ? null : plant.getPlant());
    }

    /**
     * Replaces a stale equipment uid with the uid of the equipment named by the step.
     *
     * @return {@code true} when a uid was repaired
     */
    public static boolean repair(S88MasterRecipe recipe, S88PlantModel plant) {
        if (recipe == null || plant == null || recipe.addressesByClass()) {
            return false;
        }
        boolean changed = false;
        for (S88RecipeElement element : recipe.getAllElements()) {
            if (element.getActualEquipmentIds().isEmpty()) {
                continue;
            }
            String uid = element.getEquipmentUid();
            if (uid != null && plant.findByUid(uid).isPresent()) {
                continue;
            }
            S88Element equipment = equipmentOf(plant, element);
            if (equipment != null) {
                element.setEquipmentUid(equipment.getUid());
                changed = true;
            }
        }
        return changed;
    }

    private static S88Element equipmentOf(S88PlantModel plant, S88RecipeElement element) {
        for (String id : element.getActualEquipmentIds()) {
            S88Element found = plant.findById(id).orElse(null);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
