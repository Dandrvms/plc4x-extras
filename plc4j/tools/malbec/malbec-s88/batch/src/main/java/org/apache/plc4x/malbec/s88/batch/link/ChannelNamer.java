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

/**
 * Builds protocol channel names from published batch variable names.
 */
public final class ChannelNamer {

    public static final String DEFAULT_PREFIX = "loc://";

    private ChannelNamer() {
    }

    public static String channel(String sessionId, String publishedName, DataType type) {
        return channel(DEFAULT_PREFIX, sessionId, publishedName, type);
    }

    /**
     * @throws IllegalArgumentException when {@code publishedName} is empty or {@code type} is null
     */
    public static String channel(String prefix, String sessionId, String publishedName, DataType type) {
        if (publishedName == null || publishedName.isBlank()) {
            throw new IllegalArgumentException("Published name cannot be empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        String base = normalizePrefix(prefix) + "MALBEC_" + sanitize(sessionId) + "_" + sanitize(publishedName);
        return normalizePrefix(prefix).startsWith(DEFAULT_PREFIX) ? base + '<' + wireType(type) + '>' : base;
    }

    public static String wireType(DataType type) {
        return switch (type) {
            case INTEGER, REAL -> "VDouble";
            case ENUMERATION, STRING -> "VString";
        };
    }

    /**
     * @return a name with only letters, digits, and underscores
     */
    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty()) {
            return "default";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "_" + sanitized;
        }
        return sanitized;
    }

    private static String normalizePrefix(String prefix) {
        return prefix == null || prefix.isBlank() ? DEFAULT_PREFIX : prefix;
    }
}
