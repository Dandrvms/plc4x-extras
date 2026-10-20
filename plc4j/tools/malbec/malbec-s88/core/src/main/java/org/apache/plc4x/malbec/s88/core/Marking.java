/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The steps holding a token at one moment of the flow, with how many each holds.
 */
public final class Marking {

    private final Map<String, Integer> tokens;

    private Marking(Map<String, Integer> tokens) {
        this.tokens = Collections.unmodifiableMap(tokens);
    }

    public static Marking none() {
        return new Marking(new LinkedHashMap<>());
    }

    public static Marking of(String... stepIds) {
        Marking marking = none();
        for (String stepId : stepIds) {
            marking = marking.add(stepId);
        }
        return marking;
    }

    public Set<String> active() {
        return tokens.keySet();
    }

    public int count(String stepId) {
        return tokens.getOrDefault(stepId, 0);
    }

    public boolean has(String stepId) {
        return count(stepId) > 0;
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    public Marking add(String stepId) {
        Map<String, Integer> next = new LinkedHashMap<>(tokens);
        next.merge(stepId, 1, Integer::sum);
        return new Marking(next);
    }

    public Marking remove(String stepId) {
        Integer current = tokens.get(stepId);
        if (current == null) {
            return this;
        }
        Map<String, Integer> next = new LinkedHashMap<>(tokens);
        if (current == 1) {
            next.remove(stepId);
        } else {
            next.put(stepId, current - 1);
        }
        return new Marking(next);
    }

    /** A key that does not depend on the order the steps were added in. */
    public String key() {
        StringBuilder key = new StringBuilder();
        new TreeMap<>(tokens).forEach((id, count) -> key.append(id).append('=').append(count).append(';'));
        return key.toString();
    }

    Map<String, Integer> tokens() {
        return tokens;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Marking that && tokens.equals(that.tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    @Override
    public String toString() {
        return key();
    }
}
