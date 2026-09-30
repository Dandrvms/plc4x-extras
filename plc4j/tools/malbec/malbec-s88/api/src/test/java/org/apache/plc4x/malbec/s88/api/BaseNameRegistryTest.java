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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseNameRegistryTest {

    @Test
    void aRecordedBaseNameIsFoundAgain() {
        BaseNameRegistry registry = new BaseNameRegistry();

        registry.set("Parameters", "TEMPERATURA_SP_OLLA_1", "Parameters/TEMPERATURA_SP");

        assertEquals("Parameters/TEMPERATURA_SP",
                registry.get("Parameters", "TEMPERATURA_SP_OLLA_1"));
    }

    @Test
    void anAbsentBaseNameIsNotFound() {
        assertNull(new BaseNameRegistry().get("Parameters", "TEMPERATURA_SP_OLLA_1"));
        assertNull(new BaseNameRegistry().get(null, "PRESION_OLLA_1"));
    }

    @Test
    void aNullVariableIsIgnoredRatherThanRecorded() {
        BaseNameRegistry registry = new BaseNameRegistry();

        registry.set("Parameters", null, "Parameters/TEMPERATURA_SP");

        assertTrue(registry.isEmpty());
        assertNull(registry.get("Parameters", null));
    }

    @Test
    void aTopLevelPropertyIsRecordedWithoutAContainer() {
        BaseNameRegistry registry = new BaseNameRegistry();

        registry.set(null, "PRESION_OLLA_1", "PRESION");

        assertEquals("PRESION", registry.get(null, "PRESION_OLLA_1"));
        assertEquals("PRESION", registry.get("", "PRESION_OLLA_1"),
                "an absent container is the same container as an empty one");
    }

    @Test
    void forgettingTheLastBaseNameDropsTheEmptyContainer() {
        BaseNameRegistry registry = new BaseNameRegistry();
        registry.set("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");

        registry.set("Parameters", "NIVEL_OLLA_1", null);

        assertTrue(registry.isEmpty(), "a container left empty is not kept around");
        assertNull(registry.get("Parameters", "NIVEL_OLLA_1"));
    }

    @Test
    void forgettingOneOfSeveralBaseNamesKeepsTheContainer() {
        BaseNameRegistry registry = new BaseNameRegistry();
        registry.set("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");
        registry.set("Parameters", "CAUDAL_OLLA_1", "Parameters/CAUDAL");

        registry.set("Parameters", "NIVEL_OLLA_1", null);

        assertEquals("Parameters/CAUDAL", registry.get("Parameters", "CAUDAL_OLLA_1"));
        assertNull(registry.get("Parameters", "NIVEL_OLLA_1"));
    }

    @Test
    void theSnapshotCannotBeUsedToChangeWhatIsRecorded() {
        BaseNameRegistry registry = new BaseNameRegistry();
        registry.set("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");

        Map<String, Map<String, String>> snapshot = registry.all();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove("Parameters"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.get("Parameters").remove("NIVEL_OLLA_1"));

        assertEquals("Parameters/NIVEL", registry.get("Parameters", "NIVEL_OLLA_1"));
    }

    @Test
    void theCopyKeepsTheEntriesAndStopsSharingThem() {
        BaseNameRegistry registry = new BaseNameRegistry();
        registry.set("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");

        BaseNameRegistry copy = registry.copy();
        copy.set("Parameters", "NIVEL_OLLA_1", "Parameters/OTRO");

        assertEquals("Parameters/NIVEL", registry.get("Parameters", "NIVEL_OLLA_1"),
                "the original does not follow the copy");
        assertEquals("Parameters/OTRO", copy.get("Parameters", "NIVEL_OLLA_1"));
    }

    @Test
    void copyingEntriesKeepsWhatIsAlreadyThere() {
        BaseNameRegistry registry = new BaseNameRegistry();
        registry.set("Parameters", "NIVEL_OLLA_1", "Parameters/NIVEL");
        registry.set("Parameters", "CAUDAL_OLLA_1", "Parameters/CAUDAL");

        registry.putAll(registry.copy());

        assertEquals("Parameters/NIVEL", registry.get("Parameters", "NIVEL_OLLA_1"));
        assertEquals("Parameters/CAUDAL", registry.get("Parameters", "CAUDAL_OLLA_1"));
        assertEquals(2, registry.all().get("Parameters").size());
    }

    @Test
    void copyingFromNothingChangesNothing() {
        BaseNameRegistry registry = new BaseNameRegistry();
        registry.putAll(null);

        assertTrue(registry.isEmpty());
    }
}
