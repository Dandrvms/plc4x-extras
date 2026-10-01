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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a step of a recipe needs from the equipment, and what it will not accept.
 * <p>
 * This is the demand a step makes, as opposed to the equipment it was given. Keeping the two apart
 * is what lets a recipe written for one class of module be checked against another one: the
 * requirement says what is needed, the plant says what exists, and comparing them is a question for
 * the binding rather than something either model decides on its own.
 */
public class S88EquipmentRequirement {

    private String id;
    private final List<String> constraints = new ArrayList<>();
    private String description;

    public S88EquipmentRequirement() {
    }

    public S88EquipmentRequirement(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * The conditions that narrow this requirement down, kept as the recipe wrote them. A condition
     * here is a name rather than an expression, in the same way that the condition of a transition
     * is, so it is not evaluated at this level.
     */
    public List<String> getConstraints() {
        return Collections.unmodifiableList(constraints);
    }

    public void addConstraint(String constraint) {
        if (constraint != null && !constraint.isBlank()) {
            constraints.add(constraint);
        }
    }

    public void removeConstraint(String constraint) {
        constraints.remove(constraint);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "S88EquipmentRequirement[" + id + ", " + constraints.size() + " constraint(s)]";
    }
}
