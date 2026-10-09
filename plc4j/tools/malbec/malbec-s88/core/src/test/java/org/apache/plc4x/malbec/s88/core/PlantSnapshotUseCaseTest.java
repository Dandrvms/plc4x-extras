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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A snapshot of the plant a set of recipes is written against.
 *
 * <p>
 * The plant keeps being edited underneath the recipes, so the copy has to settle what the recipes
 * are checked against. Two things make that work, and both are checked here: the copy carries
 * everything the plant declared, enumerations included, and it carries nothing by reference, so
 * the plant can be edited afterwards without reaching into a recipe that was already approved.
 */
class PlantSnapshotUseCaseTest {

    /**
     * A plant with one class of equipment and the five enumerations the plant in the batch project
     * declares.
     *
     * <p>
     * The enumerations are the reason this test exists. The plant keeps them among the equipment
     * classes, and a copy that registered them a second time refused to open a recipe set at all.
     */
    private static S88PlantModel modelOf() {
        S88ElementClass heaters = new S88ElementClass();
        heaters.setName("CALENTAMIENTO");
        heaters.setTargetLevel(S88Level.EQUIPMENTMODULE);
        heaters.setProperty("Reports", Map.of("ESTADO",
                Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "ENUM_TRUE_FALSE")));

        S88Element root = new S88Element();
        root.setId("PLANTA");
        root.setLevel(S88Level.AREA);
        S88Element cell = new S88Element();
        cell.setId("CELDA");
        cell.setLevel(S88Level.PROCESSCELL);
        S88Element unit = new S88Element();
        unit.setId("TANQUE_1");
        unit.setLevel(S88Level.UNIT);
        S88Element heater = new S88Element();
        heater.setId("CALENTAMIENTO_1");
        heater.setLevel(S88Level.EQUIPMENTMODULE);
        heater.setProperty("Reports", Map.of("ESTADO",
                Map.of("Type", "ENUMERATION", "Eng_Units/Enum", "ENUM_TRUE_FALSE")));
        heater.setBaseName("Reports", "ESTADO", "ESTADO_CALENTAMIENTO_1");

        S88PlantModel model = new S88PlantModel(root);
        model.addChild(root, cell);
        model.addChild(cell, unit);
        model.addChild(unit, heater);
        model.registerClass(heaters);
        model.registerEnumeration(enumeration("TRUE_FALSE", Map.of("TRUE", 1, "FALSE", 0)));
        model.registerEnumeration(enumeration("INGREDIENTES",
                Map.of("HARINA", 0, "AGUA", 1, "SAL", 2)));
        model.registerEnumeration(enumeration("ESTADO_ADICION_MANUAL",
                Map.of("SIN_ADICION", 0, "CON_ADICION", 1)));
        model.registerEnumeration(enumeration("STATE",
                Map.of("IDLE", 0, "RUNNING", 1, "HELD", 2)));
        model.registerEnumeration(enumeration("COMMAND",
                Map.of("START", 0, "STOP", 1)));
        return model;
    }

    private static S88Enumeration enumeration(String name, Map<String, Integer> values) {
        S88Enumeration enumeration = new S88Enumeration(name);
        values.forEach(enumeration::setValue);
        return enumeration;
    }

    @Test
    void aPlantThatDeclaresEnumerationsCanBeCopied() {
        assertDoesNotThrow(() -> PlantSnapshotUseCase.of(modelOf()),
                "and a recipe set whose plant declares enumerations opens, rather than refusing to"
                        + " install the same class twice");
    }

    @Test
    void theCopyIsTakenAgainWithoutComplaint() {
        S88PlantSnapshot first = PlantSnapshotUseCase.of(modelOf());

        assertDoesNotThrow(() -> PlantSnapshotUseCase.of(first.getPlant()),
                "because the plant being copied is the copy, and copying it has to work as well as"
                        + " copying the plant it was taken from");
    }

    @Test
    void theEnumerationsComeThroughWithTheirValues() {
        S88PlantSnapshot copy = PlantSnapshotUseCase.of(modelOf());

        S88Enumeration trueFalse = copy.findEnumeration("TRUE_FALSE");
        assertNotNull(trueFalse, "and an enumeration of the plant is still there to resolve a"
                + " variable against");
        assertEquals(Map.of("TRUE", 1, "FALSE", 0), trueFalse.getValues(),
                "with the values it had, because an enumeration whose values moved would silently"
                        + " change what a recipe written against it means");
        assertEquals(5, copy.getEnumerations().size(),
                "and all five of them came through, not only the first");
    }

    @Test
    void anEnumerationIsNotAClassOfEquipment() {
        S88PlantModel model = modelOf();

        assertNull(model.getEquipmentClasses().get("ENUM_TRUE_FALSE"),
                "and it is not offered as something an element can be, because it is a set of"
                        + " values rather than a piece of equipment");
        assertEquals(List.of("CALENTAMIENTO"),
                List.copyOf(model.getEquipmentClasses().keySet()),
                "so what is left is the class of equipment and nothing else");
        assertTrue(model.getClasses().containsKey("ENUM_TRUE_FALSE"),
                "while the plant still declares it, because the same place holds both");
    }

    @Test
    void anEnumerationIsNotOfferedAsSomethingToCreateOrToBind() {
        S88PlantModel model = modelOf();

        assertTrue(model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE).stream()
                        .noneMatch(S88PlantModel::isEnumerationClass),
                "and nothing that offers classes to choose from offers an enumeration as one of"
                        + " them");
    }

    @Test
    void changingThePlantAfterwardsDoesNotReachTheSnapshot() {
        S88PlantModel plant = modelOf();
        S88PlantSnapshot copy = PlantSnapshotUseCase.of(plant);

        replaceReportWithType(plant.findById("CALENTAMIENTO_1").orElseThrow(), "ESTADO", "STRING");
        replaceReportWithType(plant.findClass("CALENTAMIENTO"), "ESTADO", "STRING");

        assertEquals("ENUMERATION", reportOf(copy.findById("CALENTAMIENTO_1").orElseThrow(), "ESTADO")
                        .get("Type"),
                "and a report edited in the plant afterwards is not edited in the snapshot, which is"
                        + " what makes the snapshot a statement of the plant as it was");
        assertEquals("ENUMERATION",
                reportOf(copy.findClass("CALENTAMIENTO"), "ESTADO").get("Type"),
                "and neither is a report of a class");
    }

    @Test
    void theEnumerationsThemselvesAreNotSharedEither() {
        S88PlantModel plant = modelOf();
        S88PlantSnapshot copy = PlantSnapshotUseCase.of(plant);

        plant.findEnumeration("TRUE_FALSE").setValue("TRUE", 99);

        assertEquals(1, copy.findEnumeration("TRUE_FALSE").getValues().get("TRUE"),
                "because an enumeration whose value changed in one plant and not the other would"
                        + " compare a variable against a number the recipe never heard of");
    }

    @Test
    void aSnapshotOfNothingIsNothing() {
        assertNull(PlantSnapshotUseCase.of(null),
                "and a recipe set with no plant behind it says so rather than holding an empty copy");
    }

    /**
     * The definition of one report, as a map an author can change.
     *
     * <p>
     * A plant read from a file holds its properties in maps that refuse to be changed, so a test that
     * wants to see whether an edit reaches the snapshot has to put an editable one in its place.
     * That is what a change made through the editor does: it replaces the definition rather than
     * writing into a map that came from a parser.
     */
    @SuppressWarnings("unchecked")
    private static void replaceReportWithType(S88Element element, String report, String type) {
        element.setProperty(S88PlantModel.REPORTS, reportsOf(element, type, report));
    }

    @SuppressWarnings("unchecked")
    private static void replaceReportWithType(S88ElementClass elementClass, String report,
                                              String type) {
        elementClass.setProperty(S88PlantModel.REPORTS, reportsOf(elementClass, type, report));
    }

    private static Map<String, Object> reportsOf(Object holder, String type, String report) {
        return new java.util.LinkedHashMap<>(Map.of(report, new java.util.LinkedHashMap<>(
                Map.of("Type", type, "Eng_Units/Enum", "ENUM_TRUE_FALSE"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reportOf(S88Element element, String report) {
        return (Map<String, Object>) ((Map<String, Object>) element.getProperties()
                .get(S88PlantModel.REPORTS)).get(report);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reportOf(S88ElementClass elementClass, String report) {
        return (Map<String, Object>) ((Map<String, Object>) elementClass.getProperties()
                .get(S88PlantModel.REPORTS)).get(report);
    }
}