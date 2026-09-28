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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameValidatorTest {

    @Test
    void testAcceptsUppercaseLettersDigitsAndUnderscore() {
        assertTrue(NameValidator.isValidName("TANQUE_1"));
        assertTrue(NameValidator.isValidName("A"));
        assertTrue(NameValidator.isValidName("0"));
        assertTrue(NameValidator.isValidName("_"));
        assertTrue(NameValidator.isValidName("TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1"));
    }

    @Test
    void testRejectsLowercaseLetters() {
        assertFalse(NameValidator.isValidName("tanque"));
        assertFalse(NameValidator.isValidName("NewPC"));
    }

    @Test
    void testRejectsAccentsAndSpaces() {
        assertFalse(NameValidator.isValidName("TEMPERATURA_MÁXIMA"));
        assertFalse(NameValidator.isValidName("TANQUE 1"));
        assertFalse(NameValidator.isValidName("TANQUE-1"));
        assertFalse(NameValidator.isValidName("TANQUE.1"));
    }

    @Test
    void testRejectsSpecialCharacters() {
        assertFalse(NameValidator.isValidName("TANQUE$1"));
        assertFalse(NameValidator.isValidName("TANQUE#"));
        assertFalse(NameValidator.isValidName("TANQUE/1"));
    }

    @Test
    void testRejectsEmptyAndNull() {
        assertFalse(NameValidator.isValidName(null));
        assertFalse(NameValidator.isValidName(""));
        assertFalse(NameValidator.isValidName("   "));
    }

    @Test
    void testAcceptsNameAtTheLengthLimit() {
        String name = "A".repeat(NameValidator.MAX_LENGTH);
        assertEquals(NameValidator.MAX_LENGTH, name.length());
        assertTrue(NameValidator.isValidName(name));
    }

    @Test
    void testRejectsNameOverTheLengthLimit() {
        String name = "A".repeat(NameValidator.MAX_LENGTH + 1);
        assertFalse(NameValidator.isValidName(name));
    }

    @Test
    void testLengthMessageExplainsTheLimit() {
        String name = "A".repeat(NameValidator.MAX_LENGTH + 1);
        String message = NameValidator.check(name, "Element ID").orElseThrow();
        assertTrue(message.contains("too long"), message);
        assertTrue(message.contains(String.valueOf(NameValidator.MAX_LENGTH)), message);
    }

    @Test
    void testInvalidCharacterMessageListsTheAllowedCharacters() {
        String message = NameValidator.check("tanque 1", "Element ID").orElseThrow();
        assertTrue(message.contains("invalid characters"), message);
        assertTrue(message.contains("A-Z"), message);
        assertTrue(message.contains("0-9"), message);
        assertTrue(message.contains("_"), message);
    }

    @Test
    void testCheckReturnsTheLabelInTheMessage() {
        String message = NameValidator.check("bad name", "Class name").orElseThrow();
        assertTrue(message.startsWith("Class name"), message);
    }

    @Test
    void testValidateThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> NameValidator.validate("bad name", "Element ID"));
    }

    @Test
    void testValidateAcceptsAValidName() {
        assertDoesNotThrow(() -> NameValidator.validate("TANQUE_1", "Element ID"));
    }

    @Test
    void testReservedPropertiesAreRecognised() {
        assertTrue(NameValidator.isReservedProperty("icon"));
        assertTrue(NameValidator.isReservedProperty("description"));
        assertTrue(NameValidator.isReservedProperty("xPos"));
        assertTrue(NameValidator.isReservedProperty("yPos"));
        assertTrue(NameValidator.isReservedProperty("Check"));
        assertTrue(NameValidator.isReservedProperty("targetLevel"));
        assertFalse(NameValidator.isReservedProperty("TEMPERATURA"));
        assertFalse(NameValidator.isReservedProperty(null));
    }

    @Test
    void testValidatePropertyLetsReservedNamesThrough() {
        assertDoesNotThrow(() -> NameValidator.validateProperty("icon"));
        assertDoesNotThrow(() -> NameValidator.validateProperty("targetLevel"));
        assertThrows(IllegalArgumentException.class, () -> NameValidator.validateProperty("lowercase"));
    }

    @Test
    void testValidateEntryLetsReservedNamesThroughOnlyAtRoot() {
        assertDoesNotThrow(() -> NameValidator.validateEntry(null, "icon"));
        assertDoesNotThrow(() -> NameValidator.validateEntry("Parameters", "TEMPERATURA_SP"));
        assertThrows(IllegalArgumentException.class,
                () -> NameValidator.validateEntry("Parameters", "temperatura_sp"));
    }
}
