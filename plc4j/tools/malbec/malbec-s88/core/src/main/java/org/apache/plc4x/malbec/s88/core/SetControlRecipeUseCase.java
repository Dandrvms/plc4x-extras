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

/**
 * Sets a master recipe for one batch, producing the control recipe that goes to the plant.
 * <p>
 * What this does is small on purpose. It takes a copy of the master, names the batch it is for, and
 * records which master it came from. It does not choose equipment.
 * <p>
 * A master recipe written by class says "a unit of this class". Setting it for a batch does not
 * turn it into a recipe for one particular unit, because which of the hundred free units this
 * batch gets is decided while the batch runs.
 * <p>
 * A plant may have a thousand identical tanks, and a recipe that names the
 * class is the recipe that lets any of them do the work. So the control recipe keeps the way
 * of naming it that the master had, and the question of which equipment answers to it belongs
 * to whatever runs the recipe.
 */
public class SetControlRecipeUseCase {

    /**
     * The id given to the control recipe, built from the batch it is for.
     */
    public static final String ID_PREFIX = "CTL_";

    private final S88ControlRecipe control = new S88ControlRecipe();

    /**
     * Produces a control recipe for {@code batchId} out of a master recipe.
     *
     * @param master  recipe to set, may not be {@code null}
     * @param batchId batch this run is for, may not be blank
     * @return the control recipe, naming its equipment the same way the master did
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

        control.setId(ID_PREFIX + batchId);
        control.setBatchId(batchId);
        control.setSourceRecipeId(master.getId());

        control.setKind(master.getKind());

        S88Recipe structure = RecipeDeepCopy.copyRecipe(master);
        control.setVersion(structure.getVersion());
        structure.getDescriptions().forEach(control::addDescription);
        structure.getEquipmentRequirements().forEach(control::addEquipmentRequirement);
        structure.getFormula().forEach(control::addFormulaParameter);
        for (S88OtherInformation info : structure.getOtherInformation()) {

            if (!decidedHere(info.getId())) {
                control.addOtherInformation(info);
            }
        }
        if (structure.hasProcedureLogic()) {
            control.setProcedureLogic(structure.getProcedureLogic());
        }
        for (S88RecipeElement element : structure.getRecipeElements()) {
            control.addRecipeElement(element);
        }
        return control;
    }

    private static boolean decidedHere(String id) {
        return S88OtherInformation.RECIPE_KIND.equalsIgnoreCase(id)
                || S88ControlRecipe.SOURCE_RECIPE_ID.equalsIgnoreCase(id);
    }

    /** The control recipe produced by the last call to {@link #from(S88MasterRecipe, String)}. */
    public S88ControlRecipe getControlRecipe() {
        return control;
    }

    @Override
    public String toString() {
        return "SetControlRecipeUseCase[" + control.getId() + ", " + control.getKind() + "]";
    }
}