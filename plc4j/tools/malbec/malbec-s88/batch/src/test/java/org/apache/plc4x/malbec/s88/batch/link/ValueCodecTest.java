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
package org.apache.plc4x.malbec.s88.batch.link;

import java.util.List;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueCodecTest {

    @Test
    void numericValuesTravelAsDoubles() {
        assertEquals(72.0, ValueCodec.toWire(DataType.INTEGER, 72));
        assertEquals(72.0, ValueCodec.fromWire(DataType.INTEGER, 72));
        assertEquals(2.5, ValueCodec.fromWire(DataType.REAL, "2.5"));
    }

    @Test
    void stringsKeepTheirTextWithAPrefix() {
        assertEquals("~72", ValueCodec.toWire(DataType.STRING, "72"));
        assertEquals("72", ValueCodec.fromWire(DataType.STRING, "~72"));
        assertEquals("~", ValueCodec.toWire(DataType.STRING, ""));
        assertEquals("", ValueCodec.fromWire(DataType.STRING, "~"));
    }

    @Test
    void enumerationsAreValidated() {
        assertEquals("RUNNING", ValueCodec.fromWire(DataType.ENUMERATION, "~RUNNING", List.of("RUNNING", "DONE")));
        assertThrows(IllegalArgumentException.class,
            () -> ValueCodec.fromWire(DataType.ENUMERATION, "~UNKNOWN", List.of("RUNNING", "DONE")));
    }

    @Test
    void aNumberOnAStringChannelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ValueCodec.fromWire(DataType.STRING, 72.0));
    }

    @Test
    void aStringOnANumericChannelIsRejectedWhenItIsPrefixed() {
        assertThrows(IllegalArgumentException.class, () -> ValueCodec.fromWire(DataType.REAL, "~72"));
    }
}
