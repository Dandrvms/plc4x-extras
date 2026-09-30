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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseNameResolverTest {

    private static S88Element olla() {
        S88Element olla = new S88Element("uid-1").setId("OLLA_1");
        olla.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_1", "NIVEL_OLLA_1"));
        olla.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");
        olla.setBaseName("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");
        return olla;
    }

    @Test
    void aRecordedBaseNameIsTheOneTheVariableAnswers() {
        assertEquals("TEMPERATURA_SP",
                BaseNameResolver.baseNameOf(olla(), "Parameters", "TEMPERATURA_SP_OLLA_1"));
    }

    @Test
    void aHandTypedVariableAnswersUnderItsOwnName() {
        assertEquals("TURBIDEZ",
                BaseNameResolver.baseNameOf(olla(), "Parameters", "TURBIDEZ"));
    }

    @Test
    void aBaseNameFindsTheConcreteVariableOfAnInstance() {
        assertEquals("TEMPERATURA_SP_OLLA_1",
                BaseNameResolver.resolveVariable(olla(), "Parameters", "TEMPERATURA_SP").orElseThrow());
    }

    @Test
    void aBaseNameIsFoundHoweverItWasTyped() {
        assertEquals("TEMPERATURA_SP_OLLA_1",
                BaseNameResolver.resolveVariable(olla(), "Parameters", " temperatura_sp ").orElseThrow());
    }

    @Test
    void aBaseNameNoVariableAnswersIsNotFound() {
        assertTrue(BaseNameResolver.resolveVariable(olla(), "Parameters", "PRESION").isEmpty());
        assertTrue(BaseNameResolver.resolveVariable(olla(), "Parameters", null).isEmpty());
        assertTrue(BaseNameResolver.resolveVariable(null, "Parameters", "NIVEL").isEmpty());
    }

    @Test
    void twoVariablesAnsweringTheSameBaseNameAreRefusedRatherThanPicked() {
        S88Element olla = new S88Element("uid-1").setId("OLLA_1");
        olla.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_1", "TEMPERATURA_SP_OLLA_2"));
        olla.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");
        olla.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_2", "Parameters/TEMPERATURA_SP");

        assertThrows(IllegalStateException.class,
                () -> BaseNameResolver.resolveVariable(olla, "Parameters", "TEMPERATURA_SP"));
    }

    @Test
    void aHandTypedNameClashingWithARecordedOneIsReported() {
        S88Element olla = new S88Element("uid-1").setId("OLLA_1");
        olla.setProperty("Parameters", variables("NIVEL_OLLA_1", "NIVEL"));
        olla.setBaseName("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");

        List<BaseNameResolver.Conflict> conflicts = BaseNameResolver.validate(olla);

        assertEquals(1, conflicts.size());
        assertEquals("NIVEL", conflicts.get(0).baseName());
        assertEquals("Parameters", conflicts.get(0).containerKey());
        assertEquals(List.of("NIVEL_OLLA_1", "NIVEL"), conflicts.get(0).variables());
    }

    @Test
    void aSoundElementReportsNoConflict() {
        assertTrue(BaseNameResolver.validate(olla()).isEmpty());
        assertTrue(BaseNameResolver.validate(null).isEmpty());
    }

    @Test
    void resolvingAllGivesEveryBaseNameItsVariable() {
        Map<String, String> resolved = BaseNameResolver.resolveAll(olla(), "Parameters");

        assertEquals("TEMPERATURA_SP_OLLA_1", resolved.get("TEMPERATURA_SP"));
        assertEquals("NIVEL_OLLA_1", resolved.get("NIVEL"));
    }

    @Test
    void everyVariableOfThePlantAnswersItsBaseName() {
        // A class recipe names the type, not the instance: the same base name is answered by the
        // temperature of every unit of the class, which is what lets the recipe be bound later.
        S88Element olla1 = new S88Element("uid-1").setId("OLLA_1");
        olla1.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_1"));
        olla1.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");
        S88Element olla2 = new S88Element("uid-2").setId("OLLA_2");
        olla2.setProperty("Parameters", variables("TEMPERATURA_SP_OLLA_2"));
        olla2.setBaseName("Parameters", "TEMPERATURA_SP_OLLA_2", "Parameters/TEMPERATURA_SP");

        List<BaseNameResolver.VariableRef> found =
                BaseNameResolver.resolveAcrossContainers(olla1, "TEMPERATURA_SP");
        List<BaseNameResolver.VariableRef> foundElsewhere =
                BaseNameResolver.resolveAcrossContainers(olla2, "TEMPERATURA_SP");

        assertEquals(List.of(new BaseNameResolver.VariableRef("Parameters", "TEMPERATURA_SP_OLLA_1", "uid-1")),
                found);
        assertEquals(List.of(new BaseNameResolver.VariableRef("Parameters", "TEMPERATURA_SP_OLLA_2", "uid-2")),
                foundElsewhere);
    }

    private static Map<String, Object> variables(String... names) {
        Map<String, Object> container = new LinkedHashMap<>();
        for (String name : names) {
            container.put(name, new LinkedHashMap<>(Map.of("Type", "REAL")));
        }
        return container;
    }
}
