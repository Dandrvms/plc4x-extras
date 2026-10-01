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
 * This is where the second half gets decided, and it is decided here rather than in the data class
 * because a control recipe can be produced in more than one way: set from a master, handed over from
 * a batch server, or read back from a file somebody wrote. Whatever produces one has to say the same
 * thing about it, and a rule buried in a setter would apply to all of them and to none of them on
 * purpose.
 * <p>
 * The result is set for particular equipment whatever the master said. A master written by class
 * still has to name real modules before anything can run, and doing that is what this does. It does
 * not do it: choosing which tank is free is the plant's business, and this reports what the recipe
 * still needs so that whoever binds it can be asked.
 */
public class SetControlRecipeUseCase {

    /**
     * The id given to the control recipe when none is offered. The batch is what tells one run
     * apart from another, so the id is built from it rather than from a counter that would not mean
     * the same thing on two machines.
     */
    public static final String ID_PREFIX = "CTL_";

    private final S88ControlRecipe control = new S88ControlRecipe();
    private final List<String> unresolved = new ArrayList<>();

    public SetControlRecipeUseCase() {
    }

    /**
     * Produces a control recipe for {@code batchId} out of a master recipe.
     * <p>
     * The master is read, not taken over: the control recipe is a separate recipe, because it is a
     * separate thing with a separate life. It will have the batch bound to it, it will name the
     * equipment that was chosen, and it will be saved and kept as the record of what that run did.
     * Copying the master's structure and pointing it at the same objects would make an edit to one
     * of them change the other.
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
        // A recipe on its way to the plant is always set for particular equipment. The master may
        // have been written by class; that is what the steps below have to be resolved against, and
        // the control recipe records the answer rather than the question.
        control.setKind(S88RecipeKind.INSTANCE);

        S88Recipe structure = RecipeDeepCopy.copyRecipe(master);
        control.setVersion(structure.getVersion());
        structure.getDescriptions().forEach(control::addDescription);
        structure.getEquipmentRequirements().forEach(control::addEquipmentRequirement);
        structure.getFormula().forEach(control::addFormulaParameter);
        for (S88OtherInformation info : structure.getOtherInformation()) {
            // The kind and the source master are the control recipe's own business, so the copy's
            // versions of them are dropped rather than carried across and overwriting what was just
            // decided above.
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
     * <p>
     * A step of a master recipe written by class keeps its class here, and is reported as still
     * needing to be bound rather than having a module invented for it. Guessing which tank is free
     * would produce a control recipe that looks finished and would send the batch to the wrong place.
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
     * Steps of the control recipe that still name a class of equipment rather than a module, and so
     * are not yet runnable.
     * <p>
     * Reported rather than resolved, because resolving them means deciding which module of the plant
     * is free, which is a question about the plant at a moment in time and not a step in setting a
     * recipe for a batch.
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
