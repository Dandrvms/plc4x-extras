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
package org.apache.plc4x.malbec.s88.recipes.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe belongs to the plant it was written against, not to the plant as it is now.
 *
 * <p>
 * What is checked here is the freeze itself. Whether the file lands in the right folder of a NetBeans
 * project is the platform's part, and what every recipe of a set reads is the same bytes.
 */
class RecipeSetPlantSnapshotTest {

    /** A plant with one unit, one module of it, and the parameter and report that module publishes. */
    private static S88PlantModel plantWithOneHeater() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);

        S88Element root = new S88Element();
        root.setId("PLANT");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element tank = new S88Element();
        tank.setId("TANQUE_1");
        tank.setLevel(S88Level.UNIT);
        S88Element heater = new S88Element();
        heater.setId("CALENTAMIENTO_1");
        heater.setLevel(S88Level.EQUIPMENTMODULE);
        heater.setClass(heaters);
        heater.setProperty("Parameters", java.util.Map.of("TEMPERATURA",
                java.util.Map.of("Type", "REAL", "Eng_Units/Enum", "C", "Default", "100")));
        heater.setProperty("Reports", java.util.Map.of("TEMPERATURA_ACTUAL",
                java.util.Map.of("Type", "REAL", "Eng_Units/Enum", "C")));

        S88PlantModel plant = new S88PlantModel(root);
        plant.addChild(root, cell);
        plant.addChild(cell, tank);
        plant.addChild(tank, heater);
        plant.registerClass(heaters);
        return plant;
    }

    @Test
    void aFrozenPlantComesBackAsItWas() throws IOException {
        MemoryStorage frozen = new MemoryStorage();

        RecipeSetPlantSnapshot.freeze(frozen, plantWithOneHeater());
        S88PlantModel read = RecipeSetPlantSnapshot.thaw(frozen);

        assertNotNull(read.findClass("CALENTAMIENTO"),
                "and the class a recipe step is attached to is still there");
        S88Element heater = read.findById("CALENTAMIENTO_1").orElseThrow();
        assertEquals("REAL", ((java.util.Map<?, ?>) ((java.util.Map<?, ?>) heater
                .getStructuredProperty("Parameters")).get("TEMPERATURA")).get("Type"),
                "with the parameter the recipe gives a value to");
        assertTrue(((java.util.Map<?, ?>) heater.getStructuredProperty("Reports"))
                .containsKey("TEMPERATURA_ACTUAL"),
                "and the report a recipe transition waits on");
    }

    /**
     * The whole point of freezing: a plant changed afterwards is not the plant a recipe means.
     */
    @Test
    void aPlantChangedAfterwardsDoesNotChangeTheFrozenOne() throws IOException {
        MemoryStorage frozen = new MemoryStorage();
        S88PlantModel plant = plantWithOneHeater();
        RecipeSetPlantSnapshot.freeze(frozen, plant);

        plant.findById("CALENTAMIENTO_1").orElseThrow().setId("RENAMED_AFTER_THE_RECIPE");

        assertTrue(RecipeSetPlantSnapshot.thaw(frozen).findById("CALENTAMIENTO_1").isPresent(),
                "and the recipe keeps saying the equipment it was written against, because the recipe"
                        + " is about that equipment and not about the file it came from");
        assertFalse(RecipeSetPlantSnapshot.thaw(frozen).findById("RENAMED_AFTER_THE_RECIPE")
                .isPresent(), "and not the one the plant says now");
    }

    @Test
    void theIdentityOfAEquipmentSurvivesTheFreeze() throws IOException {
        MemoryStorage frozen = new MemoryStorage();
        S88PlantModel plant = plantWithOneHeater();
        String uid = plant.findById("CALENTAMIENTO_1").orElseThrow().getUid();
        RecipeSetPlantSnapshot.freeze(frozen, plant);

        assertEquals(uid,
                RecipeSetPlantSnapshot.thaw(frozen).findById("CALENTAMIENTO_1")
                        .orElseThrow().getUid(),
                "and a recipe that names the equipment by that rather than by its name keeps naming"
                        + " it when the equipment is renamed");
    }

    @Test
    void nothingIsFrozenFromNoPlant() {
        assertThrows(IllegalArgumentException.class,
                () -> RecipeSetPlantSnapshot.freeze(new MemoryStorage(), null),
                "and a recipe set with no plant is refused rather than made empty");
    }

    @Test
    void aSnapshotIsACopyAndNotThePlantItself() throws IOException {
        MemoryStorage frozen = new MemoryStorage();
        S88PlantModel plant = plantWithOneHeater();
        RecipeSetPlantSnapshot.freeze(frozen, plant);

        S88PlantSnapshot snapshot = PlantSnapshotUseCase.of(plant);
        plant.findById("CALENTAMIENTO_1").orElseThrow().setId("AFTER");

        assertTrue(snapshot.findById("CALENTAMIENTO_1").isPresent(),
                "and what a recipe set holds does not change under it either");
    }

    /** Somewhere to freeze a plant that is not the filesystem. */
    private static final class MemoryStorage implements S88Storage {

        private byte[] written;

        @Override
        public InputStream openInput() throws IOException {
            if (written == null) {
                throw new IOException("nothing has been written here");
            }
            return new ByteArrayInputStream(written);
        }

        @Override
        public OutputStream openOutput() {
            ByteArrayOutputStream out = new ByteArrayOutputStream() {
                @Override
                public void close() {
                    written = toByteArray();
                }
            };
            return out;
        }
    }
}