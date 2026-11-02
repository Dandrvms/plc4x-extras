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

import org.apache.plc4x.malbec.s88.api.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelNamerTest {

    @Test
    void channelsAreIsolatedBySession() {
        String first = ChannelNamer.channel("first session", "SETPOINT", DataType.REAL);
        String second = ChannelNamer.channel("second session", "SETPOINT", DataType.REAL);

        assertNotEquals(first, second);
        assertTrue(first.startsWith("loc://MALBEC_first_session_SETPOINT"));
        assertTrue(second.startsWith("loc://MALBEC_second_session_SETPOINT"));
    }

    @Test
    void channelsUseTheWireType() {
        assertEquals("loc://MALBEC_s1_SETPOINT<VDouble>",
            ChannelNamer.channel("s1", "SETPOINT", DataType.REAL));
        assertEquals("loc://MALBEC_s1_STATE<VString>",
            ChannelNamer.channel("s1", "STATE", DataType.ENUMERATION));
    }

    @Test
    void namesAreSanitized() {
        assertEquals("_1_batch", ChannelNamer.sanitize("1 batch"));
        assertEquals("default", ChannelNamer.sanitize("  "));
    }

    @Test
    void batchContractAddsTheBatchSuffix() {
        assertEquals("BATCH_STATE_batch_1", BatchContract.state("batch 1"));
        assertEquals("BATCH_COMMAND_ACK_batch_1", BatchContract.commandAck("batch 1"));
    }
}
