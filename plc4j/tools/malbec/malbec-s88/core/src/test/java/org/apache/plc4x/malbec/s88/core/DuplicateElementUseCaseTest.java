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
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateElementUseCaseTest {

    private S88PlantModel model;
    private S88Element olla1;
    private S88Element calentamiento;

    @BeforeEach
    void setUp() {
        S88Element root = new S88Element().setId("SOPA").setLevel(S88Level.AREA);
        olla1 = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT);
        calentamiento = new S88Element().setId("CALENTAMIENTO_OLLA_1").setLevel(S88Level.EQUIPMENTMODULE);
        calentamiento.setProperty("Parameters",
                parameters("TEMPERATURA_SP_CALENTAMIENTO_OLLA_1", 50));
        olla1.addChild(calentamiento);
        root.addChild(olla1);
        olla1.setProperty("Parameters", parameters("NIVEL_OLLA_1", 3.2f));
        model = new S88PlantModel(root);
    }

    @Test
    void testDuplicatingCreatesUniqueCopiesWithRenumberedIds() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 3, null, Map.of());

        assertEquals(3, copies.size());
        List<String> ids = copies.stream().map(S88Element::getId).toList();
        assertEquals(List.of("OLLA_2", "OLLA_3", "OLLA_4"), ids);
        for (String id : ids) {
            assertTrue(model.findById(id).isPresent(), id + " must be reachable by name");
        }
    }

    @Test
    void testEveryCopySharesTheTypeOfTheSource() {
        DuplicateElementUseCase.execute(model, olla1, 2, null, Map.of());

        S88ElementClass type = olla1.getElementClass();
        assertNotNull(type);
        assertEquals("OLLA", type.getName());
        for (S88Element copy : model.findInstancesOf("OLLA")) {
            assertSame(type, copy.getElementClass(), "all instances must share the same type");
        }
    }

    @Test
    void testEveryCopyBelongsToTheBranchOfTheSource() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 2, null, Map.of());

        S88Element cell = model.getRoot();
        for (S88Element copy : copies) {
            assertSame(cell, copy.getParent(),
                    "a copy must know its parent, or it looks like the root of the plant");
        }
        assertTrue(cell.getChildren().containsAll(copies), cell.getChildren().toString());
    }

    @Test
    void testACopyCanBeDuplicatedInTurn() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        List<S88Element> second = DuplicateElementUseCase.execute(model, copies.get(0), 1, null, Map.of());

        assertEquals(List.of("OLLA_3"), second.stream().map(S88Element::getId).toList());
        assertSame(copies.get(0).getParent(), second.get(0).getParent());
        assertTrue(second.get(0).getParent().getChildren().contains(second.get(0)));
    }

    @Test
    void testTheTypeSchemaIsKeyedByTheBaseNames() {
        DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        Object schema = model.findClass("OLLA").getProperty("Parameters");
        assertTrue(schema instanceof Map, schema + " must be a map");
        Map<?, ?> container = (Map<?, ?>) schema;
        assertTrue(container.containsKey("NIVEL"), container.toString());
        assertFalse(container.containsKey("NIVEL_OLLA_1"),
                "the schema holds the base name, not the instance-suffixed name");
    }

    @Test
    void testNestedElementsAreReidentifiedAndTheirVariablesReSuffixed() {
        DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88Element olla2 = model.findById("OLLA_2").orElseThrow();
        S88Element copyEm = olla2.getChildren().stream()
                .filter(child -> child.getId().equals("CALENTAMIENTO_OLLA_2"))
                .findFirst().orElseThrow();

        assertTrue(copyEm.getProperties().get("Parameters") instanceof Map, "copy EM must publish variables");
        Map<?, ?> parameters = (Map<?, ?>) copyEm.getProperties().get("Parameters");
        assertTrue(parameters.containsKey("TEMPERATURA_SP_CALENTAMIENTO_OLLA_2"), parameters.toString());
        assertEquals("Parameters/TEMPERATURA_SP",
                copyEm.getBaseName("Parameters", "TEMPERATURA_SP_CALENTAMIENTO_OLLA_2"),
                "the variable must remember where it was derived from");
    }

    @Test
    void testTheSourceKeepsItsNamesAndGetsBaseNamesToo() {
        DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        Map<?, ?> parameters = (Map<?, ?>) olla1.getProperties().get("Parameters");
        assertTrue(parameters.containsKey("NIVEL_OLLA_1"), parameters.toString());
        assertEquals("Parameters/NIVEL", olla1.getBaseName("Parameters", "NIVEL_OLLA_1"));
        Map<?, ?> emParameters = (Map<?, ?>) calentamiento.getProperties().get("Parameters");
        assertTrue(emParameters.containsKey("TEMPERATURA_SP_CALENTAMIENTO_OLLA_1"), emParameters.toString());
        assertEquals("Parameters/TEMPERATURA_SP",
                calentamiento.getBaseName("Parameters", "TEMPERATURA_SP_CALENTAMIENTO_OLLA_1"));
    }

    @Test
    void testCopiesAreIndependentOfTheSource() {
        DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88Element olla2 = model.findById("OLLA_2").orElseThrow();
        olla2.setProperty("Parameters", deepParameter("NIVEL_OLLA_2", 9));
        assertTrue(olla1.getProperties().get("Parameters") instanceof Map);
        Map<?, ?> sourceParameters = (Map<?, ?>) olla1.getProperties().get("Parameters");
        assertTrue(((Map<?, ?>) sourceParameters.get("NIVEL_OLLA_1")).get("Max") instanceof Number
                        && ((Number) ((Map<?, ?>) sourceParameters.get("NIVEL_OLLA_1")).get("Max")).doubleValue() == 10.0,
                "editing a copy must not leak into the source");
    }

    @Test
    void testAnOverrideRedefinesTheBaseNameOfAVariable() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null,
                Map.of("Parameters/TEMPERATURA_SP_CALENTAMIENTO_OLLA_1", "TEMPERATURA_SP_SET"));

        assertEquals(1, copies.size());
        S88Element copyEm = copies.get(0).getChildren().stream()
                .filter(child -> child.getId().equals("CALENTAMIENTO_OLLA_2"))
                .findFirst().orElseThrow();
        assertEquals("Parameters/TEMPERATURA_SP_SET",
                copyEm.getBaseName("Parameters", "TEMPERATURA_SP_CALENTAMIENTO_OLLA_2"));
    }

    @Test
    void aCopyKeepsTheBaseNameTheSourceRecorded() {
        Map<String, Object> sourceParameters = new LinkedHashMap<>();
        sourceParameters.put("NIVEL_OLLA_1", bag("NIVEL_OLLA_1", 3.2f));
        olla1.setProperty("Parameters", sourceParameters);
        olla1.setBaseName("Parameters", "NIVEL_OLLA_1", "Parameters/ALTURA");

        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88Element copy = copies.get(0);
        assertEquals("Parameters/ALTURA", copy.getBaseName("Parameters", "NIVEL_OLLA_2"));
    }

    @Test
    void aCopyWhoseVariablesShareABaseNameIsRefused() {
        Map<String, Object> sourceParameters = new LinkedHashMap<>();
        sourceParameters.put("NIVEL_OLLA_1", bag("NIVEL_OLLA_1", 3.2f));
        sourceParameters.put("ALTURA_OLLA_1", bag("ALTURA_OLLA_1", 1.5));
        olla1.setProperty("Parameters", sourceParameters);
        olla1.setBaseName("Parameters", "NIVEL_OLLA_1", "Parameters/ALTURA");
        olla1.setBaseName("Parameters", "ALTURA_OLLA_1", "Parameters/ALTURA");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of()));

        assertTrue(ex.getMessage().contains("ALTURA"), ex.getMessage());
        assertTrue(model.findById("OLLA_2").isEmpty(),
                "a refused copy leaves the plant as it was");
    }

    @Test
    void everyCopyGetsItsOwnIdentity() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 2, null, Map.of());

        for (S88Element copy : copies) {
            assertNotEquals(olla1.getUid(), copy.getUid(),
                    "a copy is another instance, so a recipe bound to the source must not reach it");
        }
        assertNotEquals(copies.get(0).getUid(), copies.get(1).getUid());
    }

    @Test
    void anElementBelowTheCopiedRootAlsoGetsItsOwnIdentity() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        assertNotEquals(calentamiento.getUid(), copies.get(0).getChildren().get(0).getUid(),
                "the module of the copy is an instance of its own, not the module of the source");
    }

    @Test
    void testZeroCopiesStillRegistersTheTypeAndTheBaseNames() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 0, null, Map.of());

        assertTrue(copies.isEmpty());
        assertNotNull(olla1.getElementClass());
        assertEquals("Parameters/NIVEL", olla1.getBaseName("Parameters", "NIVEL_OLLA_1"));
    }

    @Test
    void testReusingAnExistingTypeKeepsItAndStillCopies() {
        S88ElementClass existing = new S88ElementClass();
        existing.setName("OLLA_GRANDE");
        existing.setTargetLevel(S88Level.UNIT);
        model.registerClass(existing);
        olla1.setClass(existing);

        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, "IGNORED", Map.of());

        assertEquals(1, copies.size());
        assertEquals("OLLA_GRANDE", olla1.getElementClass().getName());
        assertEquals("OLLA_GRANDE", copies.get(0).getElementClass().getName());
    }

    @Test
    void testAlreadyTakenCopyIdsAreSkipped() {
        S88Element olla2 = new S88Element().setId("OLLA_2").setLevel(S88Level.UNIT);
        olla1.getParent().addChild(olla2);

        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 3, null, Map.of());

        assertEquals(List.of("OLLA_3", "OLLA_4", "OLLA_5"), copies.stream().map(S88Element::getId).toList());
    }

    @Test
    void testThrowsWhenTheElementHasABadClassForItsLevel() {
        S88ElementClass wrongLevel = new S88ElementClass();
        wrongLevel.setName("AREA_CLASE");
        wrongLevel.setTargetLevel(S88Level.AREA);
        model.registerClass(wrongLevel);
        olla1.setClass(wrongLevel);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of()));
    }

    @Test
    void testThrowsOnNullSource() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DuplicateElementUseCase.execute(model, null, 1, null, Map.of()));
    }

    @Test
    void testDeeplCopyDoesNotShareNestedMapsAcrossCopies() {
        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 2, null, Map.of());

        S88Element copyA = copies.get(0);
        S88Element copyB = copies.get(1);
        assertNotSame(copyA.getProperties(), copyB.getProperties());
        assertFalse(copyA.getProperties().get("Parameters") == copyB.getProperties().get("Parameters"),
                "each copy must hold its own container");
    }

    @Test
    void testTheRootAndTheProcessCellsCannotBeTurnedIntoAType() {
        S88Element root = new S88Element().setId("SOPA").setLevel(S88Level.AREA);
        S88Element cell = new S88Element().setId("PC_1").setLevel(S88Level.PROCESSCELL);
        root.addChild(cell);
        S88PlantModel fresh = new S88PlantModel(root);
        root.addChild(olla1);

        assertThrows(IllegalArgumentException.class,
                () -> DuplicateElementUseCase.execute(fresh, root, 1, null, Map.of()),
                "the root of the plant is never duplicated");
        assertThrows(IllegalArgumentException.class,
                () -> DuplicateElementUseCase.execute(fresh, cell, 1, null, Map.of()),
                "a process cell is a container of units, not a type");
    }

    @Test
    void testMissingBaseNamesAreMergedIntoAReusedClass() {
        DuplicateElementUseCase.execute(model, olla1, 0, null, Map.of());
        Map<String, Object> sourceParameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) olla1.getProperty("Parameters")).entrySet()) {
            sourceParameters.put(entry.getKey(), entry.getValue());
        }
        sourceParameters.put("CAUDAL_OLLA_1", bag("CAUDAL_OLLA_1", 5.0));
        olla1.setProperty("Parameters", sourceParameters);

        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88ElementClass type = model.findClass("OLLA");
        Map<?, ?> schema = (Map<?, ?>) type.getProperty("Parameters");
        assertTrue(schema.containsKey("NIVEL"), "the merged class keeps what it already had");
        assertTrue(schema.containsKey("CAUDAL"), schema.toString());

        S88Element copy = copies.get(0);
        Map<?, ?> copyParameters = (Map<?, ?>) copy.getProperty("Parameters");
        assertTrue(copyParameters.containsKey("CAUDAL_OLLA_2"), copyParameters.toString());
        assertEquals("Parameters/CAUDAL",
                copy.getBaseName("Parameters", "CAUDAL_OLLA_2"));
    }

    @Test
    void testAVariableRefusingToReSuffixUniquelyBlocksTheCopy() {
        Map<String, Object> sourceParameters = new LinkedHashMap<>();
        sourceParameters.put("NIVEL_OLLA_1", bag("NIVEL_OLLA_1", 3.2f));
        sourceParameters.put("NIVEL_OLLA_2", bag("NIVEL_OLLA_2", 9.0));
        olla1.setProperty("Parameters", sourceParameters);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of()));

        assertTrue(ex.getMessage().contains("twice"), ex.getMessage());
    }

    @Test
    void testTopLevelAttributesAreReSuffixedWithTheCopyId() {
        olla1.setProperty("TEMPERATURA_OLLA_1", 60.0);

        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88Element copy = copies.get(0);
        assertEquals(60.0, copy.getProperty("TEMPERATURA_OLLA_2"),
                "the copy re-suffixes the top level attribute with its own id");
        assertFalse(copy.getProperties().containsKey("TEMPERATURA_OLLA_1"),
                "the copy must not keep the source instance's attribute name");
        assertEquals(60.0, olla1.getProperty("TEMPERATURA_OLLA_1"),
                "the source keeps its own attribute untouched");
    }

    @Test
    void testATopLevelAttributeRefusingToReSuffixUniquelyBlocksTheCopy() {
        olla1.setProperty("TEMPERATURA_OLLA_1", 60.0);
        olla1.setProperty("TEMPERATURA_OLLA_2", 70.0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of()));

        assertTrue(ex.getMessage().contains("twice"), ex.getMessage());
    }

    @Test
    void testChildEquipmentModulesShareTheTypeDerivedFromTheirId() {
        DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88ElementClass moduleType = calentamiento.getElementClass();
        assertNotNull(moduleType, "the equipment module is given a type of its own");
        assertEquals("CALENTAMIENTO_OLLA", moduleType.getName(),
                "the type derives from the base id of the module");
        assertEquals(S88Level.EQUIPMENTMODULE, moduleType.getTargetLevel());
        assertSame(moduleType, model.findClass("CALENTAMIENTO_OLLA"));

        S88Element copyEm = model.findById("OLLA_2").orElseThrow().getChildren().stream()
                .filter(child -> child.getId().equals("CALENTAMIENTO_OLLA_2"))
                .findFirst().orElseThrow();
        assertSame(moduleType, copyEm.getElementClass(),
                "the copies of the unit share the kinds of modules it is made of");

        Map<?, ?> schema = (Map<?, ?>) moduleType.getProperty("Parameters");
        assertTrue(schema.containsKey("TEMPERATURA_SP"), schema.toString());
    }

    @Test
    void testUnitAttributesGetTheBaseNameTheRecipeAddresses() {
        // A unit attribute is published at the top level, so it has no container of its own to key
        // it by and its pointer is the bare base name: a container segment would name a container
        // the class, which declares the attribute at its top level, does not have.
        olla1.setProperty("PRESION_OLLA_1", attribute("double", "1.5", null));
        olla1.setProperty("NIVEL_OLLA_1", attribute("double", null, "7"));

        DuplicateElementUseCase.execute(model, olla1, 0, null, Map.of());

        assertEquals("PRESION", olla1.getBaseName(null, "PRESION_OLLA_1"),
                "a static unit attribute is addressed by its bare base name");
        assertEquals("NIVEL", olla1.getBaseName(null, "NIVEL_OLLA_1"),
                "an attribute read back from the plant is addressed the same way");
    }

    @Test
    void testTheUnitTypeDeclaresTheAttributesTheUnitPublishes() {
        olla1.setProperty("PRESION_OLLA_1", attribute("double", "1.5", null));

        DuplicateElementUseCase.execute(model, olla1, 0, null, Map.of());

        Object declared = model.findClass("OLLA").getProperty("PRESION");
        assertTrue(declared instanceof Map, "the type declares the attribute under its base name, was: " + declared);
        assertEquals("1.5", ((Map<?, ?>) declared).get("StaticValue"));
    }

    @Test
    void testTheCopyOfAnAttributeKeepsTheBaseNameItWasDerivedFrom() {
        // A recipe addresses the attribute by base name, so the copy has to answer to the very same
        // schema entry the source does, no matter which instance of the type it is.
        olla1.setProperty("PRESION_OLLA_1", attribute("double", "1.5", null));

        List<S88Element> copies = DuplicateElementUseCase.execute(model, olla1, 1, null, Map.of());

        S88Element copy = copies.get(0);
        assertEquals("PRESION", copy.getBaseName(null, "PRESION_OLLA_2"),
                "the copy is addressed by the same base name the source is");
    }

    @Test
    void testAnAttributeBaseNameCanBeOverriddenByItsOwnName() {
        // The overrides of the variables are keyed container/name, so an attribute is addressed by
        // its plain name and the two can never be read as one another.
        olla1.setProperty("PRESION_OLLA_1", attribute("double", "1.5", null));

        DuplicateElementUseCase.execute(model, olla1, 0, null, Map.of("PRESION_OLLA_1", "PRESION_CARGA"));

        assertEquals("PRESION_CARGA", olla1.getBaseName(null, "PRESION_OLLA_1"));
        assertTrue(model.findClass("OLLA").getProperties().containsKey("PRESION_CARGA"),
                "the type declares the attribute under the base name the user chose");
    }

    @Test
    void testReservedPropertiesOfTheModelAreNotTreatedAsUnitAttributes() {
        olla1.setProperty("Check", true);

        DuplicateElementUseCase.execute(model, olla1, 0, null, Map.of());

        assertNull(olla1.getBaseName(null, "Check"),
                "a property the model reserves for itself is not a unit attribute");
    }

    private static Map<String, Object> attribute(String type, String staticValue, String reference) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Type", type);
        entry.put("Eng_Units/Enum", "bar");
        if (staticValue != null) {
            entry.put("StaticValue", staticValue);
        }
        if (reference != null) {
            entry.put("Reference", reference);
        }
        return entry;
    }

    private static Map<String, Object> bag(String name, Object value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Type", "double");
        entry.put("Max", 10.0);
        return entry;
    }

    private static Map<String, Object> parameters(String name, Object value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Type", "double");
        entry.put("Max", 10.0);
        Map<String, Object> container = new LinkedHashMap<>();
        container.put(name, entry);
        return container;
    }

    private static Map<String, Object> deepParameter(String name, Object value) {
        return parameters(name, value);
    }
}