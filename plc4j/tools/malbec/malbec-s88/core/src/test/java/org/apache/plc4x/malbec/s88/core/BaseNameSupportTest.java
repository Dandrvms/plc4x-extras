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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseNameSupportTest {

    @Test
    void testBaseIdStripsTheTrailingSequenceNumber() {
        assertEquals("OLLA", BaseNameSupport.baseIdOf("OLLA_1"));
        assertEquals("OLLA", BaseNameSupport.baseIdOf("OLLA_2"));
        assertEquals("PC", BaseNameSupport.baseIdOf("PC_7"));
    }

    @Test
    void testBaseIdKeepsAnUnnumberedId() {
        assertEquals("OLLA", BaseNameSupport.baseIdOf("OLLA"));
    }

    @Test
    void testBaseIdOfNullIsNull() {
        assertNull(BaseNameSupport.baseIdOf(null));
    }

    @Test
    void testBaseNameIsDerivedByStrippingTheElementId() {
        assertEquals("TEMPERATURA_SP", BaseNameSupport.suggestBaseName("TEMPERATURA_SP_OLLA_2", "OLLA_2"));
    }

    @Test
    void testBaseNameKeepsAHandTypedVariableName() {
        assertEquals("TURBIDEZ", BaseNameSupport.suggestBaseName("TURBIDEZ", "OLLA_2"));
    }

    @Test
    void testBaseNameStripsOnlyTheLastSegment() {
        assertEquals("TEMPERATURA_SP_CALENTAMIENTO",
                BaseNameSupport.suggestBaseName("TEMPERATURA_SP_CALENTAMIENTO_OLLA_1", "OLLA_1"));
    }

    @Test
    void testBaseNameIsCaseInsensitiveAboutTheElementId() {
        assertEquals("TEMPERATURA_SP", BaseNameSupport.suggestBaseName("TEMPERATURA_SP_olla_2", "OLLA_2"));
    }

    @Test
    void testRePrefixedReplacesTheOldDiscriminator() {
        assertEquals("TEMPERATURA_SP_OLLA_2",
                BaseNameSupport.rePrefixed("TEMPERATURA_SP_OLLA_1", "OLLA_1", "OLLA_2"));
    }

    @Test
    void testRePrefixedKeepsANameAlreadyCarryingTheNewId() {
        assertEquals("TEMPERATURA_SP_OLLA_2",
                BaseNameSupport.rePrefixed("TEMPERATURA_SP_OLLA_2", "OLLA_1", "OLLA_2"));
    }

    @Test
    void testRePrefixedAppendsTheNewIdToANameWithoutOne() {
        assertEquals("TURBIDEZ_OLLA_2",
                BaseNameSupport.rePrefixed("TURBIDEZ", "OLLA_1", "OLLA_2"));
    }

    @Test
    void testRePrefixedKeepsANameMatchingTheNewIdExactly() {
        assertEquals("TURBIDEZ_OLLA_2",
                BaseNameSupport.rePrefixed("TURBIDEZ_OLLA_2", null, "OLLA_2"));
    }

    @Test
    void testNextIdsSkipsTakenNamesAndTheSource() {
        org.apache.plc4x.malbec.s88.api.S88Element root =
                new org.apache.plc4x.malbec.s88.api.S88Element().setId("PLANTA");
        org.apache.plc4x.malbec.s88.api.S88Element olla1 =
                new org.apache.plc4x.malbec.s88.api.S88Element().setId("OLLA_1");
        org.apache.plc4x.malbec.s88.api.S88Element olla2 =
                new org.apache.plc4x.malbec.s88.api.S88Element().setId("OLLA_2");
        org.apache.plc4x.malbec.s88.api.S88Element olla5 =
                new org.apache.plc4x.malbec.s88.api.S88Element().setId("OLLA_5");
        root.addChild(olla1);
        root.addChild(olla2);
        root.addChild(olla5);
        org.apache.plc4x.malbec.s88.api.S88PlantModel model =
                new org.apache.plc4x.malbec.s88.api.S88PlantModel(root);

        List<String> ids = BaseNameSupport.nextIds(model, "OLLA_1", 3);

        assertEquals(List.of("OLLA_3", "OLLA_4", "OLLA_6"), ids);
    }

    @Test
    void testNullInputYieldsNoIds() {
        assertEquals(List.of(), BaseNameSupport.nextIds(null, (String) null, 3));
        assertEquals(List.of(), BaseNameSupport.nextIds(null, "OLLA_1", 0));
    }

    @Test
    void testAStaticUnitAttributeIsToldApartFromOneReadBack() {
        S88Element olla = new S88Element().setId("OLLA_1");
        olla.setProperty("PRESION_OLLA_1", Map.of("Type", "double", "StaticValue", "1.5"));
        olla.setProperty("NIVEL_OLLA_1", Map.of("Type", "double", "Reference", "7"));

        assertTrue(BaseNameSupport.holdsStaticValue(olla, "PRESION_OLLA_1"));
        assertFalse(BaseNameSupport.holdsStaticValue(olla, "NIVEL_OLLA_1"));
    }

    @Test
    void testAUnitAttributeIsAddressedByItsBareBaseName() {
        S88Element olla = new S88Element().setId("OLLA_1");
        olla.setProperty("PRESION_OLLA_1", Map.of("Type", "double", "StaticValue", "1.5"));
        olla.setProperty("NIVEL_OLLA_1", Map.of("Type", "double", "Reference", "7"));

        // The class declares an attribute at the top level of its schema, so the pointer carries no
        // container segment: the two kinds of attribute are told apart for the user, not in the name.
        assertEquals("PRESION", BaseNameSupport.attributePointer(olla, "PRESION_OLLA_1"));
        assertEquals("NIVEL", BaseNameSupport.attributePointer(olla, "NIVEL_OLLA_1"));
    }

    @Test
    void testAPointerWrittenWithAContainerStillResolvesToItsBaseName() {
        S88Element olla = new S88Element().setId("OLLA_1");
        olla.setBaseName(null, "NIVEL_OLLA_1", "Reports/NIVEL");

        assertEquals("NIVEL", BaseNameSupport.resolveBaseName(olla, null, "NIVEL_OLLA_1"));
    }

    @Test
    void testAttributePointerOfNothingIsNull() {
        assertNull(BaseNameSupport.attributePointer(null, "PRESION_OLLA_1"));
        assertNull(BaseNameSupport.attributePointer(new S88Element().setId("OLLA_1"), null));
    }
}
