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
 * A box on the chart, naming the element of the plant it works on.
 * <p>
 * A step does not copy the element. It points at it by its exact name, so a box on the chart and the
 * module in the plant are recognisably the same thing by reading them. The recipe adds values to
 * the parameters of that module and reads its reports, and it does so on the element itself, which
 * two steps pointing at the same module share.
 * <p>
 * The name is the name the plant published. Nothing prefixes it, shortens it, or re-suffixes it
 * along the way, because a recipe that reads {@code HEAT} has to mean the same thing to whoever
 * wrote it, to the person looking at the plant, and to whatever runs it. A step whose element is
 * named differently from the element in the plant is a step nobody can follow.
 */
public class S88ProcedureStep {

    private String id;
    private String recipeElementId;
    private String recipeElementVersion;
    private final List<String> descriptions = new ArrayList<>();

    public S88ProcedureStep() {
    }

    public S88ProcedureStep(String id, String recipeElementId) {
        this.id = id;
        this.recipeElementId = recipeElementId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecipeElementId() {
        return recipeElementId;
    }

    public void setRecipeElementId(String recipeElementId) {
        this.recipeElementId = recipeElementId;
    }

    /**
     * The version of the element this step names, written down so that a recipe still means what
     * it meant when the plant it was written for has since been changed. Optional: a step that
     * leaves it out takes whatever version the plant carries.
     */
    public String getRecipeElementVersion() {
        return recipeElementVersion;
    }

    public void setRecipeElementVersion(String recipeElementVersion) {
        this.recipeElementVersion = recipeElementVersion;
    }

    public List<String> getDescriptions() {
        return Collections.unmodifiableList(descriptions);
    }

    public void addDescription(String description) {
        if (description != null) {
            descriptions.add(description);
        }
    }

    @Override
    public String toString() {
        return "S88ProcedureStep[" + id + " -> " + recipeElementId + "]";
    }
}
