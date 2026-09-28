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
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The names under test are the ones creation actually writes: the exact string the user typed, and
 * the only name the variable carries. They are spelled out literally in the fixture on purpose, so
 * anything the tool would add to them shows up here as a difference rather than being reproduced by
 * the helper itself.
 */
class VariableKeySupportTest {

    private S88Element root;
    private S88Element unit;
    private S88Element heating;
    private S88Element cooling;
    private S88ElementClass heatingClass;
    private S88ElementClass coolingClass;
    private S88PlantModel model;

    @BeforeEach
    void setUp() {
        root = element("PLANTA", S88Level.AREA, null);
        unit = element("TANQUE_1", S88Level.UNIT, null);
        root.addChild(unit);

        heatingClass = elementClass("CALENTAMIENTO");
        coolingClass = elementClass("ENFRIAMIENTO");

        heating = element("CALENTAMIENTO_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        unit.addChild(heating);
        heating.setProperty("TEMPERATURA_CALENTAMIENTO_TANQUE_1", "48.5");
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1", "50"));
        heating.setProperty("Reports", parameters("TEMPERATURA_PV_CALENTAMIENTO_TANQUE_1", "48.5"));

        cooling = element("ENFRIAMIENTO_TANQUE_1", S88Level.EQUIPMENTMODULE, coolingClass);
        unit.addChild(cooling);
        cooling.setProperty("Parameters", parameters("TEMPERATURA_SP_ENFRIAMIENTO_TANQUE_1", "5"));

        unit.setProperty("NIVEL_TANQUE_1", "3.2");
        unit.setProperty("LISTO_TANQUE_1", "true");
        unit.setProperty("icon", "tank.png");
        unit.setProperty("description", "Tanque de fermentacion");

        model = new S88PlantModel(root);
    }

    // ========== the name is what was typed, nothing is added ==========

    @Test
    void testResolveHandsBackTheStoredNameUntouched() {
        assertEquals("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1",
                VariableKeySupport.resolve(heating, "TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1"));
        assertEquals("NIVEL_TANQUE_1", VariableKeySupport.resolve(unit, "NIVEL_TANQUE_1"));
    }

    @Test
    void testResolveAddsNoSuffixToAShortName() {
        // The whole point of the tool: what is in the field is what is stored and what the batch
        // layer and PVA address. Qualifying it here would hand them a second name.
        assertEquals("TEMPERATURA_SP", VariableKeySupport.resolve(heating, "TEMPERATURA_SP"));
        assertEquals("NIVEL", VariableKeySupport.resolve(unit, "NIVEL"));
    }

    @Test
    void testResolveDoesNotQualifyWithTheClassOrTheUnit() {
        assertEquals("TEMPERATURA", VariableKeySupport.resolve(heating, "TEMPERATURA"));
        assertEquals("PRESION", VariableKeySupport.resolve(cooling, "PRESION"));
    }

    @Test
    void testResolveNormalisesOnlyCaseAndSurroundingBlanks() {
        assertEquals("NIVEL_TANQUE_1", VariableKeySupport.resolve(unit, "  nivel_tanque_1  "));
    }

    @Test
    void testResolveIgnoresBlankInput() {
        assertNull(VariableKeySupport.resolve(unit, null));
        assertNull(VariableKeySupport.resolve(unit, "  "));
        assertNull(VariableKeySupport.resolve(null, "NIVEL_TANQUE_1"));
    }

    // ========== walking what is stored ==========

    @Test
    void testListWalksTopLevelPropertiesAndBothContainers() {
        List<VariableKeySupport.VariableKey> variables = VariableKeySupport.list(model);

        assertEquals(List.of("NIVEL_TANQUE_1",
                        "LISTO_TANQUE_1",
                        "TEMPERATURA_CALENTAMIENTO_TANQUE_1",
                        "TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1",
                        "TEMPERATURA_PV_CALENTAMIENTO_TANQUE_1",
                        "TEMPERATURA_SP_ENFRIAMIENTO_TANQUE_1"),
                variables.stream().map(VariableKeySupport.VariableKey::key).toList());
    }

    @Test
    void testListSkipsPropertiesReservedByTheModel() {
        List<VariableKeySupport.VariableKey> variables = VariableKeySupport.list(model);

        assertTrue(variables.stream().noneMatch(v -> v.key().equals("icon")), "icon must not be published");
        assertTrue(variables.stream().noneMatch(v -> v.key().equals("description")),
                "description must not be published");
    }

    @Test
    void testNoConflictOnAWellFormedPlant() {
        assertTrue(VariableKeySupport.findConflicts(model).isEmpty());
        assertDoesNotThrow(() -> VariableKeySupport.validate(model));
    }

    // ========== what giving up the automatic qualification costs ==========

    @Test
    void testTwoModulesOfTheSameUnitSharingANameAreReportedAsAConflict() {
        // Names are stored as typed, so two modules of the same unit that both call a variable
        // TEMPERATURA_SP are published under one name and the downstream mapping cannot tell them
        // apart. This is why the editor warns, and why it offers the conventional name: nothing
        // renames it on its own, but the clash cannot reach the plant unnoticed either.
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP", "50"));
        cooling.setProperty("Parameters", parameters("TEMPERATURA_SP", "5"));

        List<VariableKeySupport.VariableKeyConflict> conflicts = VariableKeySupport.findConflicts(model);

        assertEquals(1, conflicts.size());
        assertEquals("TEMPERATURA_SP", conflicts.get(0).key());
        assertEquals(2, conflicts.get(0).owners().size());
        assertTrue(conflicts.get(0).owners().stream().anyMatch(o -> o.contains("CALENTAMIENTO_TANQUE_1")),
                conflicts.get(0).owners().toString());
        assertTrue(conflicts.get(0).owners().stream().anyMatch(o -> o.contains("ENFRIAMIENTO_TANQUE_1")),
                conflicts.get(0).owners().toString());
    }

    @Test
    void testAPropertyAndAContainerEntrySharingANameAreAlsoAConflict() {
        unit.setProperty("NIVEL", "3.2");
        unit.setProperty("Parameters", parameters("NIVEL", "0.0"));

        List<VariableKeySupport.VariableKeyConflict> conflicts = VariableKeySupport.findConflicts(model);

        assertEquals(1, conflicts.size());
        assertEquals("NIVEL", conflicts.get(0).key());
    }

    @Test
    void testValidateFailsOnConflictAndNamesEveryOwner() {
        S88Element secondHeater = element("RESPALDO_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        unit.addChild(secondHeater);
        secondHeater.setProperty("Parameters",
                parameters("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1", "45"));

        String message = assertThrows(IllegalStateException.class,
                () -> VariableKeySupport.validate(model)).getMessage();

        assertTrue(message.contains("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1"), message);
        assertTrue(message.contains("CALENTAMIENTO_TANQUE_1"), message);
        assertTrue(message.contains("RESPALDO_TANQUE_1"), message);
    }

    @Test
    void testOverlongStoredNameIsReported() {
        String overlong = "P".repeat(NameValidator.MAX_LENGTH + 1);
        unit.setProperty(overlong, "1");

        List<VariableKeySupport.VariableKey> reported = VariableKeySupport.findOverlongKeys(model);

        assertEquals(1, reported.size());
        assertEquals(overlong, reported.get(0).key());
    }

    @Test
    void testANameThatOnlyGetsTooLongWhenQualifiedIsNotReported() {
        // Without qualification there is nothing to grow: the tool measures the name as stored, so
        // it never invents an overlong name out of a legal one.
        unit.setProperty("P".repeat(NameValidator.MAX_LENGTH), "1");

        assertTrue(VariableKeySupport.findOverlongKeys(model).isEmpty());
        assertDoesNotThrow(() -> VariableKeySupport.validate(model));
    }

    @Test
    void testOverlongNameMessageAsksForAShorterName() {
        unit.setProperty("P".repeat(NameValidator.MAX_LENGTH + 1), "1");

        String message = assertThrows(IllegalStateException.class,
                () -> VariableKeySupport.validate(model)).getMessage();

        assertTrue(message.contains("too long"), message);
        assertTrue(message.contains("shorter name"), message);
    }

    @Test
    void testOwnerPathIsReadable() {
        VariableKeySupport.VariableKey variable = VariableKeySupport.list(model).stream()
                .filter(v -> v.key().equals("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1"))
                .findFirst()
                .orElseThrow();

        assertEquals("PLANTA/TANQUE_1/CALENTAMIENTO_TANQUE_1"
                        + ".Parameters.TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1",
                variable.owner());
    }

    // ========== targeted lookup, used by the live editor preview ==========

    @Test
    void testFindOwnersOfReportsTheSingleOwnerOfAName() {
        assertEquals(List.of("PLANTA/TANQUE_1.NIVEL_TANQUE_1"),
                VariableKeySupport.findOwnersOf(model, "NIVEL_TANQUE_1"));
    }

    @Test
    void testFindOwnersOfIsCaseInsensitive() {
        assertEquals(1, VariableKeySupport.findOwnersOf(model, "nivel_tanque_1").size());
    }

    @Test
    void testFindOwnersOfReturnsEmptyForAFreeName() {
        assertTrue(VariableKeySupport.findOwnersOf(model, "PRESION_TANQUE_1").isEmpty());
    }

    @Test
    void testFindOwnersOfReturnsEveryOwnerWhenTheNameCollides() {
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP", "50"));
        cooling.setProperty("Parameters", parameters("TEMPERATURA_SP", "5"));

        assertEquals(2, VariableKeySupport.findOwnersOf(model, "TEMPERATURA_SP").size());
    }

    @Test
    void testFindOwnersOfNeverThrowsOnEmptyInput() {
        assertTrue(VariableKeySupport.findOwnersOf(null, "NIVEL_TANQUE_1").isEmpty());
        assertTrue(VariableKeySupport.findOwnersOf(model, null).isEmpty());
        assertTrue(VariableKeySupport.findOwnersOf(model, "   ").isEmpty());
    }

    // ========== subtree, used to size the impact of a rename ==========

    @Test
    void testListSubtreeOfALeafOnlyReturnsItsOwnVariables() {
        List<VariableKeySupport.VariableKey> subtree = VariableKeySupport.listSubtree(heating);

        assertEquals(List.of("TEMPERATURA_CALENTAMIENTO_TANQUE_1",
                        "TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1",
                        "TEMPERATURA_PV_CALENTAMIENTO_TANQUE_1"),
                subtree.stream().map(VariableKeySupport.VariableKey::key).toList());
    }

    @Test
    void testListSubtreeOfAUnitIncludesItsDescendants() {
        List<VariableKeySupport.VariableKey> subtree = VariableKeySupport.listSubtree(unit);

        assertEquals(6, subtree.size());
        assertTrue(subtree.stream().anyMatch(v -> v.key().equals("NIVEL_TANQUE_1")));
        assertTrue(subtree.stream()
                .anyMatch(v -> v.key().equals("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1")));
    }

    @Test
    void testListSubtreeOfTheRootIsTheWholePlant() {
        assertEquals(VariableKeySupport.list(model).size(), VariableKeySupport.listSubtree(root).size());
    }

    @Test
    void testListSubtreeOfNullIsEmpty() {
        assertTrue(VariableKeySupport.listSubtree(null).isEmpty());
    }

    @Test
    void editingAPropertyIsNotAClashWithItself() {
        // An existing property already publishes the very name being previewed, so it has to be
        // named as the one under edit or opening it to change its limits reports a clash with
        // itself. It is named as stored, which is what the form hands over.
        String stored = "TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1";

        assertEquals(1, VariableKeySupport.findOwnersOf(model, stored).size());
        assertTrue(VariableKeySupport.findOwnersOf(model, stored, heating, stored).isEmpty());
    }

    @Test
    void excludingThePropertyUnderEditStillReportsAGenuineClash() {
        S88Element twin = element("RESPALDO_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        unit.addChild(twin);
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP", "50"));
        twin.setProperty("Parameters", parameters("TEMPERATURA_SP", "45"));

        List<String> owners = VariableKeySupport.findOwnersOf(model, "TEMPERATURA_SP", heating, "TEMPERATURA_SP");

        assertEquals(1, owners.size());
        assertTrue(owners.get(0).contains("RESPALDO_TANQUE_1"), owners.get(0));
    }

    @Test
    void excludingOnePropertyLeavesTheOtherOwnersOfASharedNameAlone() {
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP", "50"));
        cooling.setProperty("Parameters", parameters("TEMPERATURA_SP", "5"));

        List<String> owners = VariableKeySupport.findOwnersOf(model, "TEMPERATURA_SP", heating, "TEMPERATURA_SP");

        assertEquals(1, owners.size());
        assertTrue(owners.get(0).contains("Parameters.TEMPERATURA_SP"), owners.get(0));
    }

    private static S88Element element(String id, S88Level level, S88ElementClass elementClass) {
        return new S88Element().setId(id).setLevel(level).setClass(elementClass);
    }

    private static S88ElementClass elementClass(String name) {
        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName(name);
        return elementClass;
    }

    private static Map<String, Object> parameters(String name, Object value) {
        Map<String, Object> container = new LinkedHashMap<>();
        container.put(name, value);
        return container;
    }
}
