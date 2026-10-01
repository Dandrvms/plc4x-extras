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
package org.apache.plc4x.malbec.s88.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformVariablesTest {

    private static S88Element module(String id) {
        return new S88Element().setId(id).setLevel(S88Level.EQUIPMENTMODULE);
    }

    @Test
    void aModuleGetsTheStateAndTheCommand() {
        S88Element cipPump = module("CIP_PUMP_1");

        PlatformVariables.injectIntoElement(cipPump);

        Map<String, Object> reports = cipPump.getStructuredProperty("Reports");
        assertTrue(reports.containsKey("STATE_CIP_PUMP_1"), reports.toString());
        assertTrue(reports.containsKey("FAILURE_CIP_PUMP_1"), reports.toString());
        Map<String, Object> parameters = cipPump.getStructuredProperty("Parameters");
        assertTrue(parameters.containsKey("COMMAND_CIP_PUMP_1"), parameters.toString());
    }

    @Test
    void theStateCarriesTheEnumerationAndStartsIdle() {
        S88Element cipPump = module("CIP_PUMP_1");

        PlatformVariables.injectIntoElement(cipPump);

        Map<?, ?> state = (Map<?, ?>) cipPump.getStructuredProperty("Reports").get("STATE_CIP_PUMP_1");
        assertEquals("ENUMERATION", state.get("Type"));
        assertEquals("STATE", state.get("Eng_Units/Enum"));
        assertEquals("IDLE", state.get("Default"));
    }

    @Test
    void theCommandStartsAsNoOrder() {
        S88Element cipPump = module("CIP_PUMP_1");

        PlatformVariables.injectIntoElement(cipPump);

        Map<?, ?> command = (Map<?, ?>) cipPump.getStructuredProperty("Parameters").get("COMMAND_CIP_PUMP_1");
        assertEquals("NONE", command.get("Default"),
                "a module reads NONE whenever no order has arrived, which is what it sees while the"
                        + " batch server is away");
    }

    @Test
    void noPlatformVariableCarriesARangeOrATag() {
        S88Element cipPump = module("CIP_PUMP_1");

        PlatformVariables.injectIntoElement(cipPump);

        Map<?, ?> state = (Map<?, ?>) cipPump.getStructuredProperty("Reports").get("STATE_CIP_PUMP_1");
        assertFalse(state.containsKey("Max"), "a state is drawn from a fixed set, not from a range");
        assertFalse(state.containsKey("Min"));
        assertFalse(state.containsKey("Reference"), "the module is found by the name of the variable");
    }

    @Test
    void everyVariableIsAddressableByTheSameBaseName() {
        S88Element cipPump = module("CIP_PUMP_1");

        PlatformVariables.injectIntoElement(cipPump);

        assertEquals("Reports/STATE", cipPump.getBaseName("Reports", "STATE_CIP_PUMP_1"),
                "a recipe written for one module is bound to another through this name");
        assertEquals("Parameters/COMMAND", cipPump.getBaseName("Parameters", "COMMAND_CIP_PUMP_1"));
        assertEquals("Reports/FAILURE", cipPump.getBaseName("Reports", "FAILURE_CIP_PUMP_1"));
    }

    @Test
    void twoModulesOfTheSameTypeAnswerTheSameBaseName() {
        S88Element first = module("CIP_PUMP_1");
        S88Element second = module("CIP_PUMP_2");

        PlatformVariables.injectIntoElement(first);
        PlatformVariables.injectIntoElement(second);

        assertEquals("Reports/STATE", first.getBaseName("Reports", "STATE_CIP_PUMP_1"));
        assertEquals("Reports/STATE", second.getBaseName("Reports", "STATE_CIP_PUMP_2"),
                "both answer the base name, so a recipe bound to either reaches the right one");
    }

    @Test
    void addingThemTwiceChangesNothing() {
        S88Element cipPump = module("CIP_PUMP_1");

        PlatformVariables.injectIntoElement(cipPump);
        int reportsAfterFirst = cipPump.getStructuredProperty("Reports").size();
        int parametersAfterFirst = cipPump.getStructuredProperty("Parameters").size();

        PlatformVariables.injectIntoElement(cipPump);
        PlatformVariables.injectIntoElement(cipPump);

        assertEquals(reportsAfterFirst, cipPump.getStructuredProperty("Reports").size());
        assertEquals(parametersAfterFirst, cipPump.getStructuredProperty("Parameters").size());
    }

    @Test
    void aUnitGetsNoneOfThem() {
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);

        PlatformVariables.injectIntoElement(unit);

        assertNull(unit.getProperties().get("Reports"),
                "a unit does not execute anything, so it has no state of its own to report");
        assertNull(unit.getProperties().get("Parameters"));
    }

    @Test
    void aUnitUnderARootIsAlsoLeftAlone() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        S88Element em = module("CALENTAMIENTO_TANQUE_1");
        unit.addChild(em);
        root.addChild(unit);
        S88PlantModel model = new S88PlantModel(root);

        PlatformVariables.injectInto(model);

        assertNull(unit.getProperties().get("Reports"));
        assertTrue(em.getStructuredProperty("Reports").containsKey("STATE_CALENTAMIENTO_TANQUE_1"),
                "the module below the unit is the one that runs the phase, so it is the one that reports");
    }

    @Test
    void theTypeDeclaresWhatEveryModuleOfItPublishes() {
        S88ElementClass type = new S88ElementClass();
        type.setName("CALENTAMIENTO_TANQUE");
        type.setTargetLevel(S88Level.EQUIPMENTMODULE);

        PlatformVariables.injectIntoClass(type);

        Map<String, Object> reports = (Map<String, Object>) type.getProperty("Reports");
        assertTrue(reports.containsKey("STATE"), "the type is keyed by the base name a recipe uses");
        assertTrue(((Map<String, Object>) type.getProperty("Parameters")).containsKey("COMMAND"));
    }

    @Test
    void aTypeOfAnythingElseIsLeftAlone() {
        S88ElementClass unitType = new S88ElementClass();
        unitType.setName("TANQUE");
        unitType.setTargetLevel(S88Level.UNIT);

        PlatformVariables.injectIntoClass(unitType);

        assertNull(unitType.getProperty("Reports"));
    }

    @Test
    void theTwoEnumerationsAreRegisteredOnceAndOnlyOnce() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);

        PlatformVariables.injectInto(model);
        int afterFirst = model.getEnumerations().size();

        PlatformVariables.injectInto(model);
        PlatformVariables.injectInto(model);

        assertEquals(afterFirst, model.getEnumerations().size());
        assertTrue(model.findEnumeration("STATE") != null);
        assertTrue(model.findEnumeration("COMMAND") != null);
    }

    @Test
    void theStateEnumerationCarriesTheTransientsBesideTheStableStates() {
        S88Enumeration state = PlatformEnumerations.state();

        assertEquals("IDLE", state.getValues().keySet().iterator().next(),
                "the first value is what a module reports before anything has happened to it");
        assertTrue(state.getValues().containsKey("RUNNING"));
        assertTrue(state.getValues().containsKey("COMPLETE"));
        assertTrue(state.getValues().containsKey("COMPLETING"),
                "a recipe waiting for COMPLETE can tell a phase that is still working from one that is done");
        assertTrue(state.getValues().containsKey("PAUSING"));
        assertTrue(state.getValues().containsKey("ABORTING"));
    }

    @Test
    void pauseAndHoldAreNotTheSameOrderAndResumeIsNotUnhold() {
        S88Enumeration command = PlatformEnumerations.command();

        assertTrue(command.getValues().containsKey("PAUSE"));
        assertTrue(command.getValues().containsKey("HOLD"));
        assertTrue(command.getValues().containsKey("RESUME"));
        assertTrue(command.getValues().containsKey("UNHOLD"),
                "RESUME undoes a PAUSE and UNHOLD undoes a HOLD, so neither is a synonym of the other");
    }

    @Test
    void theIndexOfEveryValueIsFixedByItsPosition() {
        S88Enumeration state = PlatformEnumerations.state();

        assertEquals(0, state.getIndex("IDLE"));
        assertEquals(1, state.getIndex("STARTING"));
        assertEquals(state.getValues().size() - 1, state.getIndex(state.getValues().keySet()
                .stream().toList().get(state.getValues().size() - 1)),
                "the last value sits at the end, so adding one at the end does not move the others");
    }

    @Test
    void theTwoEnumerationsAreRecognisedAsThePlatformsOwn() {
        assertTrue(PlatformEnumerations.isPlatformEnumeration("STATE"));
        assertTrue(PlatformEnumerations.isPlatformEnumeration("COMMAND"));
        assertFalse(PlatformEnumerations.isPlatformEnumeration("TEMPERATURA"));
        assertFalse(PlatformEnumerations.isPlatformEnumeration(null));
    }

    @Test
    void theVariablesAreFoundByBaseNameAndByPublishedName() {
        assertEquals(PlatformVariable.STATE, PlatformVariable.find("STATE").orElseThrow());
        assertTrue(PlatformVariable.find("TEMPERATURA").isEmpty());
        assertTrue(PlatformVariable.isPlatformName("COMMAND"));
        assertFalse(PlatformVariable.isPlatformName("TEMPERATURA"));

        assertEquals(PlatformVariable.STATE, PlatformVariable.findByPublishedName("STATE_CIP_1").orElseThrow());
        assertEquals(PlatformVariable.STATE, PlatformVariable.findByPublishedName("STATE").orElseThrow());
        assertTrue(PlatformVariable.findByPublishedName("NIVEL_CIP_1").isEmpty());
    }

    @Test
    void aNameThatOnlyStartsLikeAPlatformOneIsNotTaken() {
        S88Element cipPump = module("CIP_PUMP_1");
        cipPump.setProperty("Parameters",
                java.util.Map.of("STATE_MACHINE_SELECT", java.util.Map.of("Type", "INTEGER")));

        PlatformVariables.injectIntoElement(cipPump);

        assertTrue(cipPump.getStructuredProperty("Parameters").containsKey("STATE_MACHINE_SELECT"),
                "a bit selector of that name collides with nothing and is left alone");
        assertTrue(cipPump.getStructuredProperty("Parameters").containsKey("COMMAND_CIP_PUMP_1"));
    }

    @Test
    void theModulesOfAPlantAreFoundInTreeOrder() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        S88Element first = module("CIP_PUMP_1");
        S88Element second = module("VALVULA_1");
        unit.addChild(first);
        unit.addChild(second);
        root.addChild(unit);

        assertEquals(java.util.List.of(first, second), PlatformVariables.modulesOf(root));
    }
}
