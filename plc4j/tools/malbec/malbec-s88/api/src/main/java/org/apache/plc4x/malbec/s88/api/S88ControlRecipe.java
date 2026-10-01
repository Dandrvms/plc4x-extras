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

import java.util.List;

/**
 * A master recipe that has been set for one batch and is on its way to the equipment.
 * <p>
 * This is the recipe that runs. It carries the identifier of the batch it belongs to, which is what
 * makes it different from every other recipe on disk: a master recipe may be edited and reused as
 * often as the process needs, while a control recipe belongs to one run and is what the plant is
 * working on right now.
 * <p>
 * A control recipe is always addressed for particular equipment, whatever the master recipe it came
 * from was written for. That is the point of it: a master written by class still has to name real
 * modules before anything can run, and this is the recipe where that happens. The kind is therefore
 * forced here rather than taken from the master, so that a control recipe that somehow arrived
 * claiming to be addressed by class is corrected on load instead of being run as something that
 * could not work.
 */
public class S88ControlRecipe extends S88MasterRecipe {

    /**
     * The id of the master recipe this one was set from, held as an
     * {@link S88OtherInformation} entry under this name. Kept on the recipe so that a change to the
     * master can be traced to the runs already made from it.
     */
    public static final String SOURCE_RECIPE_ID = "SourceRecipeID";

    private String batchId;

    /**
     * A control recipe is set for particular equipment, and that is what it defaults to.
     * <p>
     * The rule is not enforced from here on purpose. It belongs to whatever sets a control recipe
     * for a batch, which is where the decision is actually made; a class placed on the data would
     * apply it to every control recipe constructed anywhere, including the one a reader builds from
     * a file that says otherwise, and would quietly turn a mistake into a recipe that looks fine.
     */
    public S88ControlRecipe() {
        this(null, null);
    }

    public S88ControlRecipe(String id, String batchId) {
        super(id, S88RecipeKind.INSTANCE);
        this.batchId = batchId;
    }

    /** The batch this recipe was set for. This is what ties the recipe to one run. */
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public boolean hasBatchId() {
        return batchId != null && !batchId.isBlank();
    }

    /**
     * The master recipe this one was set from.
     *
     * @return the id of the master recipe, or {@code null} when this recipe does not say
     */
    public String getSourceRecipeId() {
        S88OtherInformation info = S88OtherInformation.find(getOtherInformation(), SOURCE_RECIPE_ID);
        return info != null ? info.getFirstValue() : null;
    }

    /**
     * Records the master recipe this one was set from, replacing any already held.
     *
     * @param sourceRecipeId id of the master recipe, {@code null} to remove it
     */
    public void setSourceRecipeId(String sourceRecipeId) {
        S88OtherInformation existing = S88OtherInformation.find(getOtherInformation(), SOURCE_RECIPE_ID);
        if (existing != null) {
            removeOtherInformation(existing);
        }
        if (sourceRecipeId != null && !sourceRecipeId.isBlank()) {
            addOtherInformation(S88OtherInformation.of(SOURCE_RECIPE_ID, sourceRecipeId));
        }
    }
}
