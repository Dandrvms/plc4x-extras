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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The base names of the variables an element publishes, keyed by container and then by variable
 * name.
 * <p>
 * A base name is the identifier a recipe addresses within an equipment type, so that the variable
 * can be found even when its concrete name was given something else. Keeping the mapping here,
 * apart from the element, gives the recipe modules one contract to resolve against instead of
 * reading a nested map out of the plant model.
 * <p>
 * A {@code null} container addresses a top level property and is stored under the empty key.
 * Instances are not thread safe; the plant model is edited from one thread at a time.
 */
public final class BaseNameRegistry {

    private static final String ROOT_CONTAINER = "";

    private final Map<String, Map<String, String>> entries = new LinkedHashMap<>();

    /**
     * Records the base name of a variable.
     *
     * @param containerKey container holding the variable, {@code null} for a top level property
     * @param variableName concrete name of the variable, ignored when {@code null}
     * @param baseName     base name, or {@code null} to forget the recorded one
     */
    public void set(String containerKey, String variableName, String baseName) {
        if (variableName == null) {
            return;
        }
        String key = containerKey != null ? containerKey : ROOT_CONTAINER;
        if (baseName == null) {
            Map<String, String> variables = entries.get(key);
            if (variables != null) {
                variables.remove(variableName);
                if (variables.isEmpty()) {
                    entries.remove(key);
                }
            }
            return;
        }
        entries.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(variableName, baseName);
    }

    /**
     * The base name recorded for a variable.
     *
     * @param containerKey container holding the variable, {@code null} for a top level property
     * @param variableName concrete name of the variable, ignored when {@code null}
     * @return the recorded base name, or {@code null} when the variable has none
     */
    public String get(String containerKey, String variableName) {
        if (variableName == null) {
            return null;
        }
        Map<String, String> variables = entries.get(containerKey != null ? containerKey : ROOT_CONTAINER);
        return variables != null ? variables.get(variableName) : null;
    }

    /**
     * A read-only view of every recorded base name, keyed by container and then by variable name.
     *
     * @return an immutable snapshot, never {@code null}
     */
    public Map<String, Map<String, String>> all() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : entries.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * An independent copy of this registry.
     *
     * @return a registry holding the same entries, never {@code null}
     */
    public BaseNameRegistry copy() {
        BaseNameRegistry copy = new BaseNameRegistry();
        copy.putAll(this);
        return copy;
    }

    /**
     * Copies the entries of another registry into this one. Existing entries are kept.
     *
     * @param other registry whose entries are copied, ignored when {@code null}
     */
    public void putAll(BaseNameRegistry other) {
        if (other == null) {
            return;
        }
        for (Map.Entry<String, Map<String, String>> entry : other.entries.entrySet()) {
            entries.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>()).putAll(entry.getValue());
        }
    }

    /**
     * Whether any base name is recorded.
     *
     * @return {@code true} when the registry holds no entry
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
