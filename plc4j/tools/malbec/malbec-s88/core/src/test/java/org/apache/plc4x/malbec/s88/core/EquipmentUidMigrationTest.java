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
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentUidMigrationTest {

    private static S88PlantSnapshot plant() {
        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element heater = new S88Element();
        heater.setId("CALENTADOR_1");
        heater.setLevel(S88Level.EQUIPMENTMODULE);
        root.addChild(heater);
        return PlantSnapshotUseCase.of(new S88PlantModel(root));
    }

    @Test
    void aStaleUidIsReplacedByTheUidOfTheNamedEquipment() {
        S88PlantSnapshot plant = plant();
        S88MasterRecipe recipe = new S88MasterRecipe("R", S88RecipeKind.INSTANCE);
        S88RecipeElement phase = new S88RecipeElement("CALENTAR", S88RecipeElementKind.PHASE);
        phase.addActualEquipmentId("CALENTADOR_1");
        phase.setEquipmentUid("stale-uid");
        recipe.addRecipeElement(phase);

        assertTrue(EquipmentUidMigration.repair(recipe, plant));
        assertEquals(plant.findById("CALENTADOR_1").orElseThrow().getUid(),
                phase.getEquipmentUid());
        assertFalse(EquipmentUidMigration.repair(recipe, plant),
                "a repaired recipe is not repaired again");
    }

    @Test
    void aClassRecipeIsLeftAlone() {
        S88MasterRecipe recipe = new S88MasterRecipe("R", S88RecipeKind.CLASS);

        assertFalse(EquipmentUidMigration.repair(recipe, plant()));
    }
}
