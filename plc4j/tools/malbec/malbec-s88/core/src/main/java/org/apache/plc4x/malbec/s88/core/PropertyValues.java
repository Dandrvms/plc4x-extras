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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defensive copies of the values held by the properties of an element.
 * <p>
 * Properties are stored as arbitrarily nested {@code Map} structures (a parameter owns its
 * own children, such as its {@code Reference}). Copying only the outermost container would
 * leave the inner maps shared, so that editing one element would silently mutate a sibling.
 * Every boundary that hands properties from one owner to another must therefore go through
 * {@link #deepCopy(Object)}.
 */
final class PropertyValues {

    private PropertyValues() {
        /* This utility class should not be instantiated */
    }

    /**
     * Returns a copy of the given value in which every nested map and collection is cloned
     * too. Scalar values (numbers, booleans, strings) are returned as is, they are immutable
     * for our purposes.
     *
     * @param value value to copy, may be {@code null}
     * @return an independent copy of the value
     */
    static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }
}
