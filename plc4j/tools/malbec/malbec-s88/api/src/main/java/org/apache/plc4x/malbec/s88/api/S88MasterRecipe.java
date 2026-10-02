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

/**
 * A recipe that is not bound to one batch: the thing an engineer writes and keeps.
 * <p>
 * A master recipe may be written against the class of the equipment or against one particular
 * module. That choice, {@link #getKind() the kind}, applies to every one of its steps at once, so
 * that one recipe never mixes a step that names a class with a step that names a specific tank: a
 * recipe that did would be readable but not runnable, and the mistake would only show up once.
 * <p>
 * The choice is what {@link #addressOf(S88RecipeParameter)} answers with, and it is the only thing
 * it changes. Which modules of the plant can stand in for a class is a question for the binding
 * against the plant, and is deliberately not answered here.
 */
public class S88MasterRecipe extends S88Recipe {

    private S88RecipeKind kind = S88RecipeKind.CLASS;

    public S88MasterRecipe() {
    }

    public S88MasterRecipe(String id, S88RecipeKind kind) {
        super(id);
        setKind(kind);
    }

    /**
     * How the steps of this recipe name their equipment and their variables.
     * <p>
     * Setting this also writes the choice into the recipe as an
     * {@link S88OtherInformation#RECIPE_KIND} entry, because the recipe format has no field of its
     * own for it and this is the only place the answer can live once the recipe is saved.
     *
     * @return the kind, never {@code null}
     */
    public S88RecipeKind getKind() {
        return kind;
    }

    public void setKind(S88RecipeKind kind) {
        this.kind = kind != null ? kind : S88RecipeKind.CLASS;
        S88OtherInformation existing = S88OtherInformation.find(
                getOtherInformation(), S88OtherInformation.RECIPE_KIND);
        if (existing != null) {
            removeOtherInformation(existing);
        }
        addOtherInformation(S88OtherInformation.of(
                S88OtherInformation.RECIPE_KIND, this.kind.name()));
    }

    /**
     * The kind as it was written in the recipe file, for a recipe that was just read.
     * <p>
     * A file that does not say is read as {@link S88RecipeKind#CLASS}, which is the reading that
     * needs the most from the plant and therefore the one worth assuming: a recipe that says
     * nothing about how it addresses its equipment is one that has to be bound before it can run.
     *
     * @param recipe recipe to read, may be {@code null}
     * @return the stored kind, or {@code null} when there is no recipe
     */
    public static S88RecipeKind readStoredKind(S88MasterRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        S88OtherInformation info = S88OtherInformation.find(
                recipe.getOtherInformation(), S88OtherInformation.RECIPE_KIND);
        if (info == null) {
            return S88RecipeKind.CLASS;
        }
        S88RecipeKind stored = S88RecipeKind.fromString(info.getFirstValue());
        return stored != null ? stored : S88RecipeKind.CLASS;
    }

    /** True when the steps of this recipe name the class of the equipment rather than an instance. */
    public boolean addressesByClass() {
        return kind == S88RecipeKind.CLASS;
    }

    /**
     * The address of the variable a parameter applies to, read the way this recipe writes it.
     * <p>
     * A recipe of the {@link S88RecipeKind#CLASS} kind names the base name of the variable and the
     * container it lives in, such as {@code Reports/STATE}, which is the same on every module of the
     * plant. A recipe of the {@link S88RecipeKind#INSTANCE} kind names the variable as that module
     * published it, such as {@code STATE_CALENTAMIENTO_TANQUE_1}, which already identifies one
     * variable and so carries no container of its own.
     *
     * @param parameter parameter to read the address of
     * @return the address, or {@code null} when the parameter has no id
     */
    @Override
    public S88VariableAddress addressOf(S88RecipeParameter parameter) {
        if (parameter == null || parameter.getId() == null) {
            return null;
        }
        if (addressesByClass()) {
            return S88VariableAddress.parse(parameter.getId());
        }
        return S88VariableAddress.of(null, parameter.getId());
    }
}
