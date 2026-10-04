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

import org.apache.plc4x.malbec.s88.api.S88ControlRecipe;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88OtherInformation;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Sets a master recipe for one batch, producing the control recipe that goes to the plant.
 * <p>
 * A master recipe says what the process is and, if it was written by class, what kind of equipment
 * it expects. A control recipe says both that and which particular equipment is doing it this time.
 * <p>
 * The result is set for particular equipment whatever the master said. This module ensures that
 * a master recipe written by class ends naming real modules before anything can run.
 */
public class SetControlRecipeUseCase {

    /**
     * The id given to the control recipe when none is offered.
     */
    public static final String ID_PREFIX = "CTL_";

    private final S88ControlRecipe control = new S88ControlRecipe();
    private final List<String> unresolved = new ArrayList<>();

    public SetControlRecipeUseCase() {
    }

    /**
     * Produces a control recipe for {@code batchId} out of a master recipe.
     *
     * @param master  recipe to set, may be {@code null}
     * @param batchId batch this run is for
     * @return the control recipe
     * @throws IllegalArgumentException when there is no master, or no batch to set it for
     */
    public S88ControlRecipe from(S88MasterRecipe master, String batchId) {
        if (master == null) {
            throw new IllegalArgumentException("There is no master recipe to set for this batch.");
        }
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("A control recipe has to name the batch it was set"
                    + " for, and no batch was given.");
        }
        unresolved.clear();

        control.setId(ID_PREFIX + batchId);
        control.setBatchId(batchId);
        control.setSourceRecipeId(master.getId());

        control.setKind(S88RecipeKind.INSTANCE);

        S88Recipe structure = RecipeDeepCopy.copyRecipe(master);
        control.setVersion(structure.getVersion());
        structure.getDescriptions().forEach(control::addDescription);
        structure.getEquipmentRequirements().forEach(control::addEquipmentRequirement);
        structure.getFormula().forEach(control::addFormulaParameter);
        for (S88OtherInformation info : structure.getOtherInformation()) {

            if (!isOursToKeep(info.getId())) {
                control.addOtherInformation(info);
            }
        }
        if (structure.hasProcedureLogic()) {
            control.setProcedureLogic(structure.getProcedureLogic());
        }
        for (S88RecipeElement element : structure.getRecipeElements()) {
            control.addRecipeElement(setElement(element));
        }
        return control;
    }

    private static boolean isOursToKeep(String id) {
        return S88OtherInformation.RECIPE_KIND.equalsIgnoreCase(id)
                || S88ControlRecipe.SOURCE_RECIPE_ID.equalsIgnoreCase(id);
    }

    /**
     * Copies a step, leaving the equipment it will run on to be decided against the plant.
     */
    private S88RecipeElement setElement(S88RecipeElement element) {
        if (element.getEquipmentClassId() != null && element.getActualEquipmentIds().isEmpty()) {
            unresolved.add("Step '" + element.getId() + "' is set for equipment of class '"
                    + element.getEquipmentClassId() + "', which has to be bound to a module of the"
                    + " plant before the batch can be given it.");
        }
        return element;
    }


    /**
     * The control recipe produced by the last call to {@link #from(S88MasterRecipe, String)}.
     *
     * @return the control recipe, empty when nothing has been set yet
     */
    public S88ControlRecipe getControlRecipe() {
        return control;
    }

    /**
     * Steps of the control recipe that still name a class of equipment rather than a module.
     *
     * @return one line per step still waiting to be bound
     */
    public List<String> getUnresolvedSteps() {
        return List.copyOf(unresolved);
    }

    /** True when every step of the control recipe names equipment it can actually be run on. */
    public boolean isFullyBound() {
        return unresolved.isEmpty();
    }

    @Override
    public String toString() {
        return "SetControlRecipeUseCase[" + control.getId() + ", " + unresolved.size() + " step(s)"
                + " waiting to be bound]";
    }
}
