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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S88ElementTest {

    @Test
    void everyElementHasAUid() {
        assertNotNull(new S88Element().getUid());
    }

    @Test
    void twoElementsNeverShareAUid() {
        assertNotEquals(new S88Element().getUid(), new S88Element().getUid());
    }

    @Test
    void theUidSurvivesARename() {
        S88Element element = new S88Element().setId("OLLA_1");

        element.setId("OLLA_2");

        assertNotNull(element.getUid());
        assertEquals("OLLA_2", element.getId());
    }

    @Test
    void aStoredUidIsKeptAsItIs() {
        assertEquals("a-uid", new S88Element("a-uid").getUid());
    }

    @Test
    void anEmptyStoredUidIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new S88Element(null));
        assertThrows(IllegalArgumentException.class, () -> new S88Element(""));
        assertThrows(IllegalArgumentException.class, () -> new S88Element("   "));
    }
}
