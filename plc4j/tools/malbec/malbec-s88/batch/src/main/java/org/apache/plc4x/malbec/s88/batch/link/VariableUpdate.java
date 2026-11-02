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

import java.time.Instant;

/**
 * One value received from a process link.
 *
 * @param name       published name of the variable
 * @param value      a {@link Double}, a {@link String}, or {@code null}
 * @param quality    how much the value can be trusted
 * @param sourceTime time the source gave the value
 */
public record VariableUpdate(String name, Object value, Quality quality, Instant sourceTime) {

    public static VariableUpdate bad(String name, Instant sourceTime) {
        return new VariableUpdate(name, null, Quality.BAD, sourceTime);
    }

    public static VariableUpdate good(String name, Object value, Instant sourceTime) {
        return new VariableUpdate(name, value, Quality.GOOD, sourceTime);
    }
}
