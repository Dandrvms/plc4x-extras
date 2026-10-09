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
package org.apache.plc4x.malbec.s88.api;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The plant as it was when a set of recipes was started, seen read only.
 * <p>
 * A recipe is written against a plant, and the plant keeps being edited afterward. Someone renames
 * a module, or adds a variable to a class, and a recipe that was correct an hour ago now names
 * something that is not there. Taking the plant as it stood when the recipes were written settles
 * that: the recipes are checked against the same thing every time, and a plant edited
 * does not silently change what an approved recipe means.
 * <p>
 * The plant handed to this is taken over by the snapshot. Whatever the caller had it becomes
 * unreachable except through here, which is what makes holding one worth something, and
 * {@code PlantSnapshotUseCase} is what hands it a plant of its own.
 * <p>
 * A plant changed is not re-read into it.
 */
public class S88PlantSnapshot {

    private final S88PlantModel plant;
    private final Instant takenAt;

    /**
     * Takes a plant into the snapshot.
     *
     * @param plant the plant to hold, which the caller must let go of
     */
    public S88PlantSnapshot(S88PlantModel plant) {
        this(plant, Instant.now());
    }

    public S88PlantSnapshot(S88PlantModel plant, Instant takenAt) {
        if (plant == null) {
            throw new IllegalArgumentException("A snapshot is of a plant, so there has to be one.");
        }
        this.plant = plant;
        this.takenAt = takenAt != null ? takenAt : Instant.now();
    }

    /** When the plant was copied. */
    public Instant getTakenAt() {
        return takenAt;
    }

    /**
     * The plant this snapshot holds.
     * <p>
     * Exposed because a caller has to be able to walk the hierarchy to find the elements a recipe
     * names, and there is no read only tree to look at instead.
     */
    public S88PlantModel getPlant() {
        return plant;
    }

    public S88Element getRoot() {
        return plant.getRoot();
    }

    public List<S88Element> childrenOf(S88Element element) {
        return element != null ? element.getChildren() : List.of();
    }

    public Optional<S88Element> findById(String id) {
        return plant.findById(id);
    }

    public Optional<S88Element> findByUid(String uid) {
        return plant.findByUid(uid);
    }

    public S88ElementClass findClass(String name) {
        return plant.findClass(name);
    }

    /** The classes an element can be. Enumerations are left out, because one is not a piece of equipment. */
    public Map<String, S88ElementClass> getClasses() {
        return Collections.unmodifiableMap(plant.getEquipmentClasses());
    }

    public List<S88ElementClass> getClassesForChildLevel(S88Level childLevel) {
        return plant.getClassesForChildLevel(childLevel);
    }

    /** Every element of the given class, wherever it sits in the plant. */
    public List<S88Element> findInstancesOf(String className) {
        return plant.findInstancesOf(className);
    }

    public S88Enumeration findEnumeration(String name) {
        return plant.findEnumeration(name);
    }

    public List<S88Enumeration> getEnumerations() {
        return plant.getEnumerations();
    }

    /**
     * Ids the plant has on more than one element. A snapshot of a plant that is already broken is
     * worth having, since a recipe cannot be checked against an ambiguous name, and this is how the
     * reader finds out which names are the ones in question.
     */
    public Set<String> getDuplicateIds() {
        return plant.getDuplicateIds();
    }

    @Override
    public String toString() {
        return "S88PlantSnapshot[taken " + takenAt + ", root "
                + (plant.getRoot() != null ? plant.getRoot().getId() : "none") + "]";
    }
}
