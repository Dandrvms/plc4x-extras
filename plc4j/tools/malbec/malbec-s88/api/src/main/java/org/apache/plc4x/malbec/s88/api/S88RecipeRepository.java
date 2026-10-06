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
 * Interface for load and save recipes.
 * <p>
 * What this reads and writes is a {@link S88MasterRecipe} and nothing else, and that is a statement
 * about what a recipe file is rather than a convenience. A recipe file holds authoring data: what
 * the engineer decided, before anyone picked a batch or a tank. The
 * {@link S88ControlRecipe the control recipe} that later comes out of it is runtime state, built by
 * binding a master to a plant for one run, and what is kept of that run once it has happened is a
 * production record rather than a recipe. Writing a control recipe into a recipe file would put a
 * derived artefact where the source lives, and the next read would treat it as if an engineer had
 * written it by hand.
 * <p>
 * One storage holds one recipe, the same way one storage holds one plant. A project holds many
 * recipes as many files, and the storage given to this interface is the one for whichever recipe is
 * being opened.
 * <p>
 * Loading knows nothing about the plant. A recipe is a document that can be read, checked, edited
 * and saved on its own, and it is written and read long before anyone decides which tank it will run
 * in. Whether the steps name equipment and variables that exist is a separate question, asked by
 * whoever binds the recipe, and keeping the two apart is what lets a recipe be opened without a
 * plant and a plant be opened without any recipe.
 */
public interface S88RecipeRepository {

    /**
     * Reads the recipe from its storage.
     * <p>
     * A recipe that is incomplete, or that names things it does not carry, is still returned.
     * Whether it holds together is reported afterwards by the conformance rules, which live in the
     * core module rather than here. Failing the load instead would leave a recipe that needs fixing
     * impossible to open, and a recipe is usually opened in order to fix it.
     *
     * @return the recipe read, {@code null} when the storage is empty
     */
    S88MasterRecipe loadRecipe();

    /**
     * Writes the recipe to its storage.
     *
     * @param recipe recipe to write, ignored when {@code null}
     */
    void saveRecipe(S88MasterRecipe recipe);

    /**
     * Every recipe in the storage given to this repository.
     * <p>
     * Declared here so that a format that can hold several recipes in one file, such as a project
     * file gathering all of them, is free to answer it, while one that holds a single recipe answers
     * with that one. The default covers the ordinary case of a file per recipe.
     *
     * @return the recipes held, empty when the storage is empty
     */
    default List<S88MasterRecipe> loadAllRecipes() {
        S88MasterRecipe recipe = loadRecipe();
        return recipe != null ? List.of(recipe) : List.of();
    }
}
