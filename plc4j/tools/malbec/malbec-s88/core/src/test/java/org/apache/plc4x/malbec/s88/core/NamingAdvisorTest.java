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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NamingAdvisorTest {

    private S88Element root;
    private S88Element unit;
    private S88Element heating;
    private S88ElementClass heatingClass;
    private S88PlantModel model;

    @BeforeEach
    void setUp() {
        root = element("PLANTA", S88Level.AREA, null);
        unit = element("TANQUE_1", S88Level.UNIT, null);
        root.addChild(unit);

        heatingClass = new S88ElementClass();
        heatingClass.setName("CALENTAMIENTO");
        heating = element("CALENTAMIENTO_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        unit.addChild(heating);
        // Names are stored exactly as typed, and are the only name the variable carries.
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1", 50));

        unit.setProperty("NIVEL_TANQUE_1", 3.2f);
        model = new S88PlantModel(root);
    }

    // ===== structural convention: advised, never enforced =====

    @Test
    void testEquipmentModuleNotFollowingTheConventionIsAdvised() {
        NamingAdvisor.Advice advice =
                NamingAdvisor.suggestConventionalId(heating, "CALENTADOR");

        assertNotNull(advice);
        assertEquals(NamingAdvisor.Severity.WARNING, advice.severity());
        assertEquals("CALENTADOR_TANQUE_1", advice.suggestion());
        assertTrue(advice.message().contains("does not follow the naming convention"), advice.message());
    }

    @Test
    void testAUnitIsNamedFreelyAndGetsNoConventionAdvice() {
        assertNull(NamingAdvisor.suggestConventionalId(unit, "TANQUE"));
    }

    @Test
    void testAProcessCellIsNamedFreelyAndGetsNoConventionAdvice() {
        S88Element cell = element("SECO_PLANTA", S88Level.PROCESSCELL, null);
        root.addChild(cell);

        assertNull(NamingAdvisor.suggestConventionalId(cell, "CENTRAL_SECO"));
    }

    @Test
    void testNameAlreadyEndingWithTheParentIdIsLeftAlone() {
        S88Element sibling = element("SECO_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        unit.addChild(sibling);

        assertNull(NamingAdvisor.suggestConventionalId(sibling, "CENTRAL_SECO_TANQUE_1"));
    }

    @Test
    void testNameEndingExactlyWithTheParentIdIsLeftAlone() {
        S88Element sibling = element("TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        unit.addChild(sibling);

        assertNull(NamingAdvisor.suggestConventionalId(sibling, "TANQUE_1"));
    }

    @Test
    void testLowerCaseInputIsNormalisedBeforeComparing() {
        NamingAdvisor.Advice advice = NamingAdvisor.suggestConventionalId(heating, "calentador");

        assertEquals("CALENTADOR_TANQUE_1", advice.suggestion());
    }

    @Test
    void testNoAdviceForAnElementWithoutAParent() {
        S88Element orphan = element("SUELTO", S88Level.EQUIPMENTMODULE, null);

        assertNull(NamingAdvisor.suggestConventionalId(orphan, "TANQUE"));
    }

    @Test
    void testNoAdviceForBlankOrNullInput() {
        assertNull(NamingAdvisor.suggestConventionalId(heating, null));
        assertNull(NamingAdvisor.suggestConventionalId(heating, "   "));
    }

    @Test
    void testNoAdviceWhenTheNameWouldNotEvenPassTheBlockingValidator() {
        assertNull(NamingAdvisor.suggestConventionalId(heating, "calentador con espacios"));
        assertNull(NamingAdvisor.suggestConventionalId(heating, "A".repeat(NameValidator.MAX_LENGTH + 1)));
    }

    @Test
    void testAdvisesShorteningWhenAppendingTheParentIdWouldOverflow() {
        String almostFull = "P".repeat(NameValidator.MAX_LENGTH - 6);
        NamingAdvisor.Advice advice = NamingAdvisor.suggestConventionalId(heating, almostFull);

        assertNotNull(advice);
        assertNull(advice.suggestion(), "no suggestion is offered when it would not be a valid name");
        assertTrue(advice.message().contains("exceed"), advice.message());
    }

    // ===== the naming convention for a variable, offered and never applied =====

    @Test
    void testAnEquipmentModuleVariableIsOfferedTheClassAndUnitAsASuffix() {
        NamingAdvisor.Advice advice = NamingAdvisor.suggestConventionalVariableName(heating, "TEMPERATURA_SP");

        assertNotNull(advice);
        assertEquals(NamingAdvisor.Severity.INFO, advice.severity());
        assertEquals("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1", advice.suggestion());
    }

    @Test
    void testAVariableOfAnyOtherLevelIsOfferedItsElementId() {
        NamingAdvisor.Advice advice = NamingAdvisor.suggestConventionalVariableName(unit, "NIVEL");

        assertEquals("NIVEL_TANQUE_1", advice.suggestion());
    }

    @Test
    void testASuggestionIsOnlyOfferedNeverApplied() {
        // The advisor returns a string and nothing else: no entry, no event, no model change. The
        // user presses the button or leaves the name alone, and the tool keeps quiet either way.
        S88Element before = heating;

        assertNotNull(NamingAdvisor.suggestConventionalVariableName(heating, "TEMPERATURA_SP"));

        assertSame(before, heating);
        assertEquals(List.of("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1"),
                heating.getStructuredProperty("Parameters").keySet().stream().toList());
    }

    @Test
    void testANameThatAlreadyEndsWithTheSuffixIsNotGivenASecondOne() {
        assertNull(NamingAdvisor.suggestConventionalVariableName(heating,
                "TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1"));
    }

    @Test
    void testANameEndingExactlyWithTheSuffixIsLeftAlone() {
        assertNull(NamingAdvisor.suggestConventionalVariableName(unit, "TANQUE_1"));
    }

    @Test
    void testNoVariableConventionIsOfferedForBlankOrNullInput() {
        assertNull(NamingAdvisor.suggestConventionalVariableName(heating, null));
        assertNull(NamingAdvisor.suggestConventionalVariableName(heating, "   "));
        assertNull(NamingAdvisor.suggestConventionalVariableName(null, "TEMPERATURA_SP"));
    }

    @Test
    void testNoVariableConventionIsOfferedForANameTheValidatorWouldReject() {
        assertNull(NamingAdvisor.suggestConventionalVariableName(heating, "temperatura con espacios"));
    }

    @Test
    void testTheVariableConventionIsSkippedWhenTheSuffixWouldNotFit() {
        String almostFull = "P".repeat(NameValidator.MAX_LENGTH - 5);
        NamingAdvisor.Advice advice = NamingAdvisor.suggestConventionalVariableName(heating, almostFull);

        assertNotNull(advice);
        assertNull(advice.suggestion(), "a suggestion is only offered when it would be a valid name");
        assertTrue(advice.message().contains("exceed"), advice.message());
    }

    // ===== colliding and oversized names =====

    @Test
    void testFreeNameGetsNoWarning() {
        assertNull(NamingAdvisor.warnIfKeyTaken(model, "NIVEL_TANQUE_1_NEW"));
    }

    @Test
    void testTakenNameIsReportedWithItsOwner() {
        NamingAdvisor.Advice advice = NamingAdvisor.warnIfKeyTaken(model, "NIVEL_TANQUE_1");

        assertNotNull(advice);
        assertEquals(NamingAdvisor.Severity.WARNING, advice.severity());
        assertTrue(advice.message().contains("NIVEL_TANQUE_1"), advice.message());
        assertTrue(advice.message().contains("NIVEL"), advice.message());
    }

    @Test
    void testTwoModulesOfTheSameUnitReusingANameAreWarnedAboutWhileTyping() {
        S88Element cooling = element("ENFRIAMIENTO_TANQUE_1", S88Level.EQUIPMENTMODULE, null);
        unit.addChild(cooling);
        heating.setProperty("Parameters", parameters("TEMPERATURA_SP", 50));

        assertNotNull(NamingAdvisor.warnIfKeyTaken(model, "TEMPERATURA_SP"),
                "the typed name is free until the second module is given it too");

        cooling.setProperty("Parameters", parameters("TEMPERATURA_SP", 5));

        NamingAdvisor.Advice advice = NamingAdvisor.warnIfKeyTaken(model, "TEMPERATURA_SP", cooling, "TEMPERATURA_SP");
        assertNotNull(advice);
        assertTrue(advice.message().contains("already used by 1 propert"), advice.message());
        assertTrue(advice.message().contains("CALENTAMIENTO_TANQUE_1"), advice.message());
    }

    @Test
    void testTakenNameIsDetectedCaseInsensitively() {
        assertNotNull(NamingAdvisor.warnIfKeyTaken(model, "nivel_tanque_1"));
    }

    @Test
    void testLookingUpANameNeverThrowsOnANullModel() {
        assertNull(NamingAdvisor.warnIfKeyTaken(null, "NIVEL_TANQUE_1"));
        assertNull(NamingAdvisor.warnIfKeyTaken(model, null));
        assertNull(NamingAdvisor.warnIfKeyTaken(model, "  "));
    }

    @Test
    void testOversizedNameIsReported() {
        String longName = "K".repeat(NameValidator.MAX_LENGTH + 1);
        NamingAdvisor.Advice advice = NamingAdvisor.warnIfKeyTooLong(longName);

        assertNotNull(advice);
        assertTrue(advice.message().contains("maximum is " + NameValidator.MAX_LENGTH), advice.message());
    }

    @Test
    void testNameAtTheLimitIsAccepted() {
        assertNull(NamingAdvisor.warnIfKeyTooLong("K".repeat(NameValidator.MAX_LENGTH)));
        assertNull(NamingAdvisor.warnIfKeyTooLong(null));
    }

    // ===== rename impact =====

    @Test
    void testRenamingALeafLeavesItsVariableNamesAlone() {
        NamingAdvisor.Advice advice = NamingAdvisor.describeRenameImpact(heating);

        assertNotNull(advice);
        assertTrue(advice.message().contains("1 variable"), advice.message());
        assertTrue(advice.message().contains("untouched"), advice.message());
    }

    @Test
    void testRenamingAUnitLeavesEveryVariableBelowItAlone() {
        NamingAdvisor.Advice advice = NamingAdvisor.describeRenameImpact(unit);

        assertNotNull(advice);
        // the unit publishes NIVEL_TANQUE_1, and the module below it publishes its temperature
        assertTrue(advice.message().contains("2 variables"), advice.message());
        assertTrue(advice.message().contains("untouched"), advice.message());
    }

    @Test
    void testRenamingAnElementWithoutVariablesIsNotWorthAWarning() {
        S88Element empty = element("VACIO_TANQUE_1", S88Level.EQUIPMENTMODULE, null);
        unit.addChild(empty);

        assertNull(NamingAdvisor.describeRenameImpact(empty));
    }

    @Test
    void testRenamingNullIsNotWorthAWarning() {
        assertNull(NamingAdvisor.describeRenameImpact(null));
    }

    // ===== the advisor never blocks, unlike the validator =====

    @Test
    void testAdvisorDoesNotThrowWhereTheValidatorDoes() {
        String badName = "calentador con espacios";

        assertThrows(IllegalArgumentException.class, () -> NameValidator.validate(badName, "Element ID"));
        assertDoesNotThrow(() -> NamingAdvisor.suggestConventionalId(heating, badName));
    }

    // ===== whole plant report, for models arriving from another tool =====

    @Test
    void testACleanModelHasNoProblemsToReport() {
        NamingAdvisor.ModelReport report = NamingAdvisor.describeModel(model);

        assertFalse(report.hasProblems());
        assertTrue(report.conflicts().isEmpty());
        assertTrue(report.overlong().isEmpty());
        assertTrue(report.duplicateIds().isEmpty());
    }

    @Test
    void testTwoEquipmentModulesOfTheSameUnitReusingANameCollide() {
        S88Element plant = element("PLANTA", S88Level.AREA, null);
        S88Element tank = element("TANQUE_1", S88Level.UNIT, null);
        plant.addChild(tank);
        S88ElementClass heatingClass = new S88ElementClass();
        heatingClass.setName("CALENTAMIENTO");
        S88Element heater = element("CALENTAMIENTO_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        S88Element resistance = element("RESISTENCIA_TANQUE_1", S88Level.EQUIPMENTMODULE, heatingClass);
        tank.addChild(heater);
        tank.addChild(resistance);
        heater.setProperty("Parameters", parameters("TEMPERATURA_SP", 50));
        resistance.setProperty("Parameters", parameters("TEMPERATURA_SP", 60));

        NamingAdvisor.ModelReport report = NamingAdvisor.describeModel(new S88PlantModel(plant));

        // Both modules store the name the user typed, and both typed the same one. Nothing is
        // qualified on the way in, so the report is what makes the clash visible.
        assertEquals(1, report.conflicts().size());
        assertEquals("TEMPERATURA_SP", report.conflicts().get(0).key());
        assertEquals(2, report.conflicts().get(0).owners().size());
    }

    @Test
    void testAnOverlongStoredNameIsReported() {
        S88Element plant = element("PLANTA", S88Level.AREA, null);
        S88Element tank = element("TANQUE_PRINCIPAL", S88Level.UNIT, null);
        plant.addChild(tank);
        // A name over the limit, as a model built outside the editor can carry. Creation advises
        // against it but does not block, so the report is where it still gets caught.
        String overlong = "NIVEL_TANQUE_AGITACION_CENTRIFUGA_ZONA_NORTE_DEL_PROCESO_SUR_TANQUE_PRINCIPAL";
        assertTrue(overlong.length() > NameValidator.MAX_LENGTH, overlong);
        tank.setProperty(overlong, 1.0f);

        NamingAdvisor.ModelReport report = NamingAdvisor.describeModel(new S88PlantModel(plant));

        assertEquals(1, report.overlong().size());
        assertEquals(overlong, report.overlong().get(0).key());
    }

    @Test
    void testADuplicatedElementIdIsReportedAsTheRootCauseOfTheCollision() {
        S88Element plant = element("PLANTA", S88Level.AREA, null);
        S88Element first = element("TANQUE_1", S88Level.UNIT, null);
        S88Element second = element("TANQUE_1", S88Level.UNIT, null);
        plant.addChild(first);
        plant.addChild(second);
        first.setProperty("NIVEL_TANQUE_1", 1.0f);
        second.setProperty("NIVEL_TANQUE_1", 2.0f);

        NamingAdvisor.ModelReport report = NamingAdvisor.describeModel(new S88PlantModel(plant));

        // One id, one colliding key. Showing both lets the user fix the id instead of chasing
        // what looks like an unrelated naming problem.
        assertTrue(report.duplicateIds().contains("TANQUE_1"), report.duplicateIds().toString());
        assertEquals(1, report.conflicts().size());
        assertEquals("NIVEL_TANQUE_1", report.conflicts().get(0).key());
    }

    @Test
    void testAllThreeCategoriesAreReportedTogether() {
        S88Element plant = element("PLANTA", S88Level.AREA, null);
        S88Element duplicated = element("TANQUE_1", S88Level.UNIT, null);
        S88Element alsoDuplicated = element("TANQUE_1", S88Level.UNIT, null);
        S88Element longNamed = element("UNIDAD_DE_COCIMIENTO", S88Level.UNIT, null);
        plant.addChild(duplicated);
        plant.addChild(alsoDuplicated);
        plant.addChild(longNamed);
        duplicated.setProperty("NIVEL_TANQUE_1", 1.0f);
        alsoDuplicated.setProperty("NIVEL_TANQUE_1", 2.0f);
        longNamed.setProperty("NIVEL_TANQUE_AGITACION_CENTRIFUGA_ZONA_NORTE_DEL_PROCESO_UNIDAD_DE_COCIMIENTO", 3.0f);

        NamingAdvisor.ModelReport report = NamingAdvisor.describeModel(new S88PlantModel(plant));

        assertTrue(report.hasProblems());
        assertEquals(1, report.conflicts().size());
        assertEquals(1, report.overlong().size());
        assertEquals(1, report.duplicateIds().size());
    }

    @Test
    void testDescribingANullModelYieldsAnEmptyReport() {
        NamingAdvisor.ModelReport report = NamingAdvisor.describeModel(null);

        assertFalse(report.hasProblems());
        assertTrue(report.conflicts().isEmpty());
        assertTrue(report.overlong().isEmpty());
        assertTrue(report.duplicateIds().isEmpty());
    }

    @Test
    void testTheReportIsDetachedFromTheCollectionsItWasBuiltFrom() {
        List<VariableKeySupport.VariableKey> overlong = new ArrayList<>();
        List<VariableKeySupport.VariableKeyConflict> conflicts = new ArrayList<>();
        Set<String> duplicateIds = new LinkedHashSet<>();
        NamingAdvisor.ModelReport report = new NamingAdvisor.ModelReport(conflicts, overlong, duplicateIds);

        overlong.add(new VariableKeySupport.VariableKey("X", "OWNER"));
        duplicateIds.add("TANQUE_1");

        assertTrue(report.overlong().isEmpty());
        assertTrue(report.duplicateIds().isEmpty());
    }

    private static S88Element element(String id, S88Level level, S88ElementClass elementClass) {
        return new S88Element().setId(id).setLevel(level).setClass(elementClass);
    }

    private static Map<String, Object> parameters(String name, Object value) {
        Map<String, Object> container = new LinkedHashMap<>();
        container.put(name, value);
        return container;
    }
}
