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

/**
 * Converts batch values to and from the wire representation.
 */
public final class ValueCodec {

    public static final String STRING_PREFIX = "~";

    private ValueCodec() {
    }

    /**
     * @throws IllegalArgumentException when the type does not accept the value
     */
    public static Object toWire(DataType type, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        return switch (type) {
            case INTEGER, REAL -> toDouble(value);
            case ENUMERATION, STRING -> STRING_PREFIX + toStringValue(value);
        };
    }

    public static Object fromWire(DataType type, Object raw) {
        return fromWire(type, raw, List.of());
    }

    /**
     * @param enumerationValues valid enumeration names; an empty list skips validation
     * @throws IllegalArgumentException when the value does not match the type
     */
    public static Object fromWire(DataType type, Object raw, List<String> enumerationValues) {
        if (raw == null) {
            return null;
        }
        return switch (type) {
            case INTEGER, REAL -> toDoubleFromWire(raw);
            case STRING -> toStringFromWire(raw);
            case ENUMERATION -> toEnumerationFromWire(raw, enumerationValues);
        };
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Expected a number but got " + value.getClass().getName());
    }

    private static Double toDoubleFromWire(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && !text.startsWith(STRING_PREFIX)) {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a number but got '" + text + "'", e);
            }
        }
        throw new IllegalArgumentException("Expected a number but got " + raw.getClass().getName());
    }

    private static String toStringValue(Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Expected a string but got " + value.getClass().getName());
    }

    private static String toStringFromWire(Object raw) {
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException("Expected a string but got " + raw.getClass().getName());
        }
        return stripPrefix(text);
    }

    private static String toEnumerationFromWire(Object raw, List<String> enumerationValues) {
        String value = toStringFromWire(raw);
        if (enumerationValues != null && !enumerationValues.isEmpty() && !enumerationValues.contains(value)) {
            throw new IllegalArgumentException("Unknown enumeration value: '" + value + "'");
        }
        return value;
    }

    private static String stripPrefix(String text) {
        return text.startsWith(STRING_PREFIX) ? text.substring(STRING_PREFIX.length()) : text;
    }
}
