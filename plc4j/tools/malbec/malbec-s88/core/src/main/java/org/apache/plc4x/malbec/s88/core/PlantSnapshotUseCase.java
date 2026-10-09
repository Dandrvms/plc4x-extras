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

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Takes the plant a set of recipes is written against.
 * <p>
 * Recipes are written, approved, and then run against a plant that keeps being edited underneath
 * them. A copy taken once settles that the recipes are checked against the same thing every time.
 */
public class PlantSnapshotUseCase {

    private PlantSnapshotUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Takes a copy of the plant as it stands right now.
     *
     * @param plant plant to copy, may be {@code null}
     * @return the snapshot, or {@code null} when there was no plant to copy
     */
    public static S88PlantSnapshot of(S88PlantModel plant) {
        if (plant == null) {
            return null;
        }
        return new S88PlantSnapshot(copy(plant), Instant.now());
    }

    /**
     * A new plant holding the same elements, classes, enumerations and base names, and sharing
     * nothing with the original.
     *
     * @param source plant to copy
     * @return the copy
     */
    public static S88PlantModel copy(S88PlantModel source) {
        S88PlantModel copy = new S88PlantModel(copyElement(source.getRoot(), null));
        // One pass over the declared classes, and that is all.
        // <b>Enumerations come along with it.</b> The plant keeps them among the classes, under an
        // ENUM_ name, so a second pass that registered them again would be registering the same
        // class twice and refusing to do it. Copying them here also keeps their properties as they
        // were written, rather than taking them apart and putting them back together from what a
        // reader of the plant understands an enumeration to be.
        for (S88ElementClass elementClass : source.getClasses().values()) {
            copy.registerClass(copyClass(elementClass));
        }
        return copy;
    }

    private static S88Element copyElement(S88Element source, S88Element parent) {
        S88Element copy = new S88Element();
        copy.setId(source.getId());
        copy.setLevel(source.getLevel());
        copy.setParent(parent);
        copy.setProperty(S88Element.UID_PROPERTY, source.getUid());
        copy.setCheck(source.isCheck());
        for (S88ElementClass elementClass : source.getElementClasses()) {
            copy.addElementClass(copyClass(elementClass));
        }
        for (Map.Entry<String, Object> property : copyProperties(source.getProperties()).entrySet()) {
            copy.setProperty(property.getKey(), property.getValue());
        }
        for (Map.Entry<String, Map<String, String>> container
                : source.getBaseNameRegistry().all().entrySet()) {
            for (Map.Entry<String, String> entry : container.getValue().entrySet()) {
                copy.setBaseName(container.getKey(), entry.getKey(), entry.getValue());
            }
        }
        for (S88Element child : source.getChildren()) {
            copy.addChild(copyElement(child, copy));
        }
        return copy;
    }

    private static S88ElementClass copyClass(S88ElementClass source) {
        S88ElementClass copy = new S88ElementClass();
        copy.setName(source.getName());
        copy.setTargetLevel(source.getTargetLevel());
        for (Map.Entry<String, Object> property : copyProperties(source.getProperties()).entrySet()) {
            copy.setProperty(property.getKey(), property.getValue());
        }
        return copy;
    }

    /**
     * The properties of an element or a class, with nothing shared with the original.
     * <p>
     * The variables of a piece of equipment sit two maps deep: {@code Reports} holds one entry per
     * variable, and each of those holds the type, the units and the limits. Copying only the outer
     * map would leave the inner ones in both plants, so a change made to the plant afterwards would
     * show up in a recipe that was approved against the plant as it was before that change. That is
     * the whole point of the copy, so the nesting is followed all the way down.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyProperties(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    /** A property value, copied when it is a map so that the copy owns it. */
    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return copyProperties((Map<String, Object>) nested);
        }
        return value;
    }
}
