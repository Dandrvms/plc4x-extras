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
package org.apache.plc4x.malbec.s88.batch.simulator.values;

import java.util.ArrayList;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableStoreTest {

    @Test
    void loadsTheVariablesOfAModule() {
        VariableStore store = store();

        assertEquals(20.0, store.numeric("TEMPERATURA_ACTUAL"), 1e-9);
        assertEquals(DataType.REAL, store.get("TEMPERATURA_ACTUAL").type());
        assertEquals(0.0, store.numeric("TEMPERATURA_SP"), 1e-9);
        assertEquals(DataType.INTEGER, store.get("DURACION").type());
        assertEquals("IDLE", store.text("STATE_CALENTADOR_1"));
        assertEquals("NONE", store.text("COMMAND_CALENTADOR_1"));
        assertTrue(store.get("STATE_CALENTADOR_1").enumerationValues().contains("COMPLETE"));
    }

    @Test
    void addsPlatformVariablesOnlyToEquipmentModules() {
        List<String> warnings = new ArrayList<>();
        VariableStore store = VariableStore.of(DemoFixtures.plant(),
                List.of("REACTOR_1", "BOMBA_1"), warnings);

        assertTrue(warnings.isEmpty(), () -> String.join("\n", warnings));
        assertFalse(store.contains("STATE_REACTOR_1"));
        assertFalse(store.contains("COMMAND_REACTOR_1"));
        assertTrue(store.contains("STATE_BOMBA_1"));
        assertTrue(store.contains("COMMAND_BOMBA_1"));
    }

    @Test
    void changesValuesAndRestoresDefaults() {
        VariableStore store = store();

        store.set("TEMPERATURA_ACTUAL", 72.0, 5.0);
        assertEquals(72.0, store.numeric("TEMPERATURA_ACTUAL"), 1e-9);
        assertEquals(5.0, store.get("TEMPERATURA_ACTUAL").lastChangeSimTime(), 1e-9);

        store.reset(10.0);
        assertEquals(20.0, store.numeric("TEMPERATURA_ACTUAL"), 1e-9);
        assertEquals(10.0, store.get("TEMPERATURA_ACTUAL").lastChangeSimTime(), 1e-9);
    }

    @Test
    void rejectsValuesThatDoNotFitTheVariable() {
        VariableStore store = store();

        assertThrows(IllegalArgumentException.class,
                () -> store.set("TEMPERATURA_ACTUAL", "hot", 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> store.set("STATE_CALENTADOR_1", 3, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> store.set("NOT_PUBLISHED", 1.0, 1.0));
    }

    @Test
    void infersTheTemperatureFollowRule() {
        S88Element heater = DemoFixtures.plant().findById("CALENTADOR_1").orElseThrow();

        List<FollowRule> rules = FollowRuleHeuristic.infer("CALENTADOR_1", heater);

        assertEquals(1, rules.size());
        FollowRule rule = rules.get(0);
        assertEquals("CALENTADOR_1", rule.moduleId());
        assertEquals("TEMPERATURA_ACTUAL", rule.reportBase());
        assertEquals("TEMPERATURA_SP", rule.setpointBase());
        assertEquals(1.0, rule.ratePerSecond(), 1e-9);
        assertEquals(0.5, rule.tolerance(), 1e-9);
    }

    @Test
    void infersASetpointThatNamesTheValueWithoutASuffix() {
        List<FollowRule> rules = FollowRuleHeuristic.infer("CALENTAR_TANQUE",
                List.of("TEMPERATURA_ACTUAL"), List.of("TEMPERATURA"));

        assertEquals(1, rules.size());
        assertEquals("TEMPERATURA_ACTUAL", rules.get(0).reportBase());
        assertEquals("TEMPERATURA", rules.get(0).setpointBase());
    }

    private static VariableStore store() {
        List<String> warnings = new ArrayList<>();
        VariableStore store = VariableStore.of(DemoFixtures.plant(), List.of("CALENTADOR_1"),
                warnings);
        assertTrue(warnings.isEmpty(), () -> String.join("\n", warnings));
        assertNotNull(store);
        return store;
    }
}
