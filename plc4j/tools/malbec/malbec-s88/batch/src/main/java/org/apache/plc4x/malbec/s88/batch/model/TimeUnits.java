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
package org.apache.plc4x.malbec.s88.batch.model;

import java.util.Locale;

/** Converts the time units a recipe and a plant may use. */
public final class TimeUnits {

    private TimeUnits() {
    }

    public static boolean isTime(String unit) {
        if (unit == null || unit.isBlank()) {
            return false;
        }
        return switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "S", "SEC", "SECOND", "SECONDS",
                 "MIN", "MINUTE", "MINUTES",
                 "H", "HR", "HOUR", "HOURS" -> true;
            default -> false;
        };
    }

    /** Converts a value in {@code unit} to seconds. */
    public static double toSeconds(double value, String unit) {
        if (unit == null || unit.isBlank()) {
            return value;
        }
        return switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "MIN", "MINUTE", "MINUTES" -> value * 60.0;
            case "H", "HR", "HOUR", "HOURS" -> value * 3600.0;
            default -> value;
        };
    }

    /** Converts a number of seconds to {@code unit}. */
    public static double fromSeconds(double seconds, String unit) {
        if (unit == null || unit.isBlank()) {
            return seconds;
        }
        return switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "MIN", "MINUTE", "MINUTES" -> seconds / 60.0;
            case "H", "HR", "HOUR", "HOURS" -> seconds / 3600.0;
            default -> seconds;
        };
    }

    /** Converts a numeric literal when both units are time units. */
    public static String normalizeLiteral(String literal, String sourceUnit, String targetUnit) {
        if (literal == null || !isTime(sourceUnit) || !isTime(targetUnit)) {
            return literal;
        }
        try {
            double value = Double.parseDouble(literal.trim());
            double seconds = toSeconds(value, sourceUnit);
            return Double.toString(fromSeconds(seconds, targetUnit));
        } catch (NumberFormatException notANumber) {
            return literal;
        }
    }
}
