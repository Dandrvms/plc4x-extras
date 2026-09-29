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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassConformanceTest {

    @Test
    void anElementMatchingItsClassConforms() {
        S88ElementClass type = type("OLLA");
        type.setProperty("Parameters", variables("TEMPERATURA_SP"));
        type.setProperty("PRESION", attribute());

        S88Element olla = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT).setClass(type);
        olla.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_1"));
        olla.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");
        olla.setProperty("PRESION_OLLA_1", attribute());
        olla.setBaseName(null, "PRESION_OLLA_1", "PRESION");

        assertTrue(ClassConformance.of(olla).isConforming());
    }

    @Test
    void whatTheElementPublishesAndTheClassDoesNotDeclareIsExcess() {
        S88ElementClass type = type("OLLA");
        S88Element olla = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT).setClass(type);
        olla.setProperty("Parameters", variables("TURBIDEZ_OLLA_1"));
        olla.setBaseName("Parameters", "TURBIDEZ_OLLA_1", "Parameters/TURBIDEZ");
        olla.setProperty("NIVEL_OLLA_1", attribute());
        olla.setBaseName(null, "NIVEL_OLLA_1", "NIVEL");

        ClassConformance conformance = ClassConformance.of(olla);

        assertEquals(List.of("Parameters/TURBIDEZ", "NIVEL"), conformance.excess());
        assertEquals(List.of(), conformance.deficit());
    }

    @Test
    void whatTheClassDeclaresAndTheElementDoesNotPublishIsDeficit() {
        S88ElementClass type = type("OLLA");
        type.setProperty("Parameters", variables("TEMPERATURA_SP", "TIEMPO"));
        S88Element olla = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT).setClass(type);
        olla.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_1"));
        olla.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");

        ClassConformance conformance = ClassConformance.of(olla);

        assertEquals(List.of(), conformance.excess());
        assertEquals(List.of("Parameters/TIEMPO"), conformance.deficit());
    }

    @Test
    void anAttributePointerWrittenWithAContainerStillResolves() {
        S88ElementClass type = type("TANQUE");
        type.setProperty("PRESION", attribute());
        S88Element tanque = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT).setClass(type);
        tanque.setProperty("PRESION_TANQUE_1", attribute());
        tanque.setBaseName(null, "PRESION_TANQUE_1", "Reports/PRESION");

        assertTrue(ClassConformance.of(tanque).isConforming(),
                "a plant written before the bare pointer is read by the base name either way");
    }

    @Test
    void addingToTheClassResolvesTheExcessAndLeavesTheSiblingsShort() {
        S88ElementClass type = type("OLLA");
        type.setProperty("Parameters", variables("TEMPERATURA_SP"));
        S88Element olla1 = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT).setClass(type);
        olla1.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_1", "TURBIDEZ_OLLA_1"));
        olla1.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");
        olla1.setBaseName("Parameters", "TURBIDEZ_OLLA_1", "Parameters/TURBIDEZ");
        S88Element olla2 = new S88Element().setId("OLLA_2").setLevel(S88Level.UNIT).setClass(type);
        olla2.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_2"));
        olla2.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_2", "Parameters/TEMPERATURA_SP");

        assertEquals(1, ClassConformance.addToClass(olla1, type));

        assertTrue(ClassConformance.of(olla1).isConforming());
        assertEquals(List.of("Parameters/TURBIDEZ"),
                ClassConformance.of(olla2).deficit(),
                "the sibling is left short of what the class now declares, rather than edited");
    }

    @Test
    void addingToAClassThatHasNoContainerYetCreatesIt() {
        S88ElementClass type = type("DOSIFICACION");
        S88Element em = new S88Element().setId("DOSIFICACION_1")
                .setLevel(S88Level.EQUIPMENTMODULE).setClass(type);
        em.setProperty("Parameters", variables("CANTIDAD_DOSIFICACION_1"));
        em.setBaseName("Parameters", "CANTIDAD_DOSIFICACION_1", "Parameters/CANTIDAD");

        assertEquals(1, ClassConformance.addToClass(em, type));

        assertTrue(ClassConformance.of(em).isConforming());
        assertTrue(((Map<?, ?>) type.getProperty("Parameters")).containsKey("CANTIDAD"),
                "the container the class lacked is created rather than the variable left dangling");
    }

    @Test
    void aligningPublishesWhatTheClassDeclaresAndTouchesNoSibling() {
        S88ElementClass type = type("DOSIFICACION");
        type.setProperty("Parameters", variables("CANTIDAD"));
        S88Element em = new S88Element().setId("DOSIFICACION_1")
                .setLevel(S88Level.EQUIPMENTMODULE).setClass(type);
        S88Element sibling = new S88Element().setId("DOSIFICACION_2")
                .setLevel(S88Level.EQUIPMENTMODULE).setClass(type);

        assertEquals(List.of("Parameters/CANTIDAD"), ClassConformance.alignWithClass(em, type));

        Map<?, ?> published = (Map<?, ?>) em.getProperties().get("Parameters");
        assertEquals("Parameters/CANTIDAD",
                em.getBaseName("Parameters", "CANTIDAD_DOSIFICACION_1"),
                "a variable is published inside its container, named for the element that owns it");
        assertTrue(published.containsKey("CANTIDAD_DOSIFICACION_1"), published.toString());
        assertTrue(ClassConformance.of(em).isConforming());
        assertTrue(sibling.getProperties().get("Parameters") == null,
                "the class is the contract, so aligning an instance does not reach the others");
    }

    @Test
    void aligningAlsoPublishesTheUnitAttributesOfTheClass() {
        S88ElementClass type = type("TANQUE");
        type.setProperty("PRESION", attribute());
        S88Element tanque = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT).setClass(type);

        assertEquals(List.of("PRESION"), ClassConformance.alignWithClass(tanque, type));

        assertEquals("PRESION", tanque.getBaseName(null, "PRESION_TANQUE_1"),
                "an attribute is named for the element, the base name is what the class declares");
        assertTrue(ClassConformance.of(tanque).isConforming());
    }

    @Test
    void aligningKeepsWhatTheElementAlreadyHolds() {
        S88ElementClass type = type("OLLA");
        type.setProperty("Parameters", variables("TEMPERATURA_SP"));
        S88Element olla = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT).setClass(type);
        olla.setProperty("Parameters", new LinkedHashMap<>(Map.of("TEMPERATURA_SP_OLLA_1",
                new LinkedHashMap<>(Map.of("Type", "REAL", "Default", "99")))));
        olla.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");

        assertEquals(List.of(), ClassConformance.alignWithClass(olla, type));
        Map<?, ?> held = (Map<?, ?>) ((Map<?, ?>) olla.getProperties().get("Parameters"))
                .get("TEMPERATURA_SP_OLLA_1");
        assertEquals("99", held.get("Default"),
                "a variable the element already publishes keeps the value it was given");
    }

    @Test
    void aVariableRenamedByHandIsComparedUnderTheNameItWasGiven() {
        S88ElementClass type = type("OLLA");
        type.setProperty("Parameters", variables("NIVEL"));
        S88Element olla = new S88Element().setId("OLLA_1").setLevel(S88Level.UNIT).setClass(type);
        olla.setProperty("Parameters", variables("CUSTOM_NAME_OLLA_1"));
        olla.setBaseName("Parameters", "CUSTOM_NAME_OLLA_1", "Parameters/NIVEL");

        assertTrue(ClassConformance.of(olla).isConforming(),
                "the recorded base name is what the class is compared against, not the concrete name");
    }

    private static S88ElementClass type(String name) {
        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName(name);
        elementClass.setTargetLevel(S88Level.UNIT);
        return elementClass;
    }

    private static Map<String, Object> variables(String... baseNames) {
        Map<String, Object> container = new LinkedHashMap<>();
        for (String base : baseNames) {
            container.put(base, new LinkedHashMap<>(Map.of("Type", "REAL")));
        }
        return container;
    }

    private static Map<String, Object> attribute() {
        return new LinkedHashMap<>(Map.of("Type", "REAL", "StaticValue", "1.5"));
    }
}
