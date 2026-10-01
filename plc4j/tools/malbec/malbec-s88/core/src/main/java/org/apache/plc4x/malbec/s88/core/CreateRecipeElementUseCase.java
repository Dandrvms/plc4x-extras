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

import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * Use case for adding a step to a recipe.
 * <p>
 * A step that works on equipment is added by the name that equipment has in the plant, and that name
 * is taken from the plant rather than invented here. {@code parent} is {@code null} for a step of the
 * recipe itself and the element above it for one nested inside another.
 * <p>
 * Nothing about the plant is checked. Whether the plant has an element of that name is a question
 * about the plant, and it is asked by {@code ResolveRecipeUseCase} when the recipe is bound, so that
 * a recipe can be written and half finished before there is anything to bind it to.
 */
public class CreateRecipeElementUseCase {

    private CreateRecipeElementUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Adds a step and indexes it, so a chart can find it straight away.
     *
     * @param recipe recipe to add to, may not be {@code null}
     * @param parent step to add it inside, {@code null} to make it a step of the recipe itself
     * @param id     name of the step, which for one that works on equipment is the name the plant
     *               gives that equipment
     * @param kind   what kind of step it is
     * @return the step that was added
     * @throws IllegalArgumentException when there is no recipe, or the name is not one it can use
     */
    public static S88RecipeElement execute(S88Recipe recipe, S88RecipeElement parent,
                                           String id, S88RecipeElementKind kind) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }
        RecipeNameValidator.validate(recipe, id);

        S88RecipeElement element = new S88RecipeElement(id, kind);
        if (parent == null) {
            recipe.addRecipeElement(element);
        } else {
            if (recipe.findElement(parent.getId()).map(found -> found != parent).orElse(true)) {
                throw new IllegalArgumentException("The step '" + parent.getId() + "' is not part of"
                        + " this recipe, so there is nothing to add a step to.");
            }
            recipe.attachUnder(parent, element);
        }
        recipe.fireChangeEvent(new S88RecipeChangeEvent(S88RecipeChangeEvent.Type.ADDED, element));
        return element;
    }

    /**
     * Adds a step that works on one particular element of the plant, naming it the way the plant
     * does. This is the ordinary case, and taking both together is what keeps the name and the
     * reference to it from drifting apart.
     *
     * @param recipe  recipe to add to
     * @param parent  step to add it inside, {@code null} for a step of the recipe itself
     * @param id      name the plant gives the element
     * @param kind    what kind of step it is
     * @return the step that was added
     */
    public static S88RecipeElement forEquipment(S88Recipe recipe, S88RecipeElement parent,
                                                String id, S88RecipeElementKind kind) {
        S88RecipeElement element = execute(recipe, parent, id, kind);
        element.addActualEquipmentId(id);
        return element;
    }

    /**
     * Adds a step that works on any equipment of a class, naming the class. The recipe addresses a
     * class until it is set for a batch, and what it becomes is decided then rather than now.
     *
     * @param recipe     recipe to add to
     * @param parent     step to add it inside, {@code null} for a step of the recipe itself
     * @param id         name to give the step
     * @param kind       what kind of step it is
     * @param classId    class of the equipment it works on
     * @return the step that was added
     */
    public static S88RecipeElement forEquipmentClass(S88Recipe recipe, S88RecipeElement parent,
                                                     String id, S88RecipeElementKind kind,
                                                     String classId) {
        S88RecipeElement element = execute(recipe, parent, id, kind);
        element.setEquipmentClassId(classId);
        return element;
    }
}
