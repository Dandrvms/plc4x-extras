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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A recipe: a set of steps, the chart that joins them, and what each step needs and sets.
 * <p>
 * This is the shape both kinds of recipe have. How the steps name their equipment is not stored
 * here but decided by the recipe that owns them, which is why the reading of a parameter address is
 * left to a subclass. That is the one thing that genuinely differs between a recipe that can run
 * anywhere and one that can only run where it was written.
 * <p>
 * The steps are indexed by id when the recipe is built, so that the chart can find the element a
 * step points at without a walk of the whole tree on every lookup.
 */
public abstract class S88Recipe {

    private String id;
    private String version;
    private String versionDate;
    private final List<String> descriptions = new ArrayList<>();
    private final List<S88EquipmentRequirement> equipmentRequirements = new ArrayList<>();
    /**
     * The calculation the recipe carries on its own, kept as the recipe wrote it.
     * <p>
     * Nothing here works the expression out. A recipe is written once, so a model that agreed
     * on a meaning for the expression would commit every recipe already saved to whatever the
     * first reader of it thought.
     * <p>
     * The parameters are held as text, and reading them is the business of whatever runs the recipe.
     */
    private final List<S88ParameterValue> formula = new ArrayList<>();

    public List<S88ParameterValue> getFormula() {
        return Collections.unmodifiableList(formula);
    }

    public void addFormulaParameter(S88ParameterValue value) {
        if (value != null) {
            formula.add(value);
        }
    }

    public boolean hasFormula() {
        return !formula.isEmpty();
    }
    private S88ProcedureLogic procedureLogic;
    private final List<S88RecipeElement> recipeElements = new ArrayList<>();
    private final List<S88OtherInformation> otherInformation = new ArrayList<>();

    private final Map<String, S88RecipeElement> elementById = new LinkedHashMap<>();
    private final Set<String> duplicateIds = new LinkedHashSet<>();
    private final List<S88RecipeChangeListener> listeners = new CopyOnWriteArrayList<>();

    protected S88Recipe() {
    }

    protected S88Recipe(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionDate() {
        return versionDate;
    }

    public void setVersionDate(String versionDate) {
        this.versionDate = versionDate;
    }

    public List<String> getDescriptions() {
        return Collections.unmodifiableList(descriptions);
    }

    public void addDescription(String description) {
        if (description != null) {
            descriptions.add(description);
        }
    }

    /** What the recipe as a whole needs from the equipment. */
    public List<S88EquipmentRequirement> getEquipmentRequirements() {
        return Collections.unmodifiableList(equipmentRequirements);
    }

    public void addEquipmentRequirement(S88EquipmentRequirement requirement) {
        if (requirement != null) {
            equipmentRequirements.add(requirement);
        }
    }

    public S88ProcedureLogic getProcedureLogic() {
        return procedureLogic;
    }

    public void setProcedureLogic(S88ProcedureLogic procedureLogic) {
        this.procedureLogic = procedureLogic;
    }

    public boolean hasProcedureLogic() {
        return procedureLogic != null;
    }

    public List<S88RecipeElement> getRecipeElements() {
        return Collections.unmodifiableList(recipeElements);
    }

    /**
     * Attaches a step and indexes it, so that a step added after the recipe was read is reachable
     * without waiting for the next reload.
     *
     * @param element step being attached, ignored when {@code null}
     */
    public void addRecipeElement(S88RecipeElement element) {
        if (element == null) {
            return;
        }
        recipeElements.add(element);
        index(element);
    }

    public void removeRecipeElement(S88RecipeElement element) {
        if (element != null && recipeElements.remove(element)) {
            unindex(element);
        }
    }

    /**
     * Attaches a step under another step and indexes it, so that a chart anywhere in the recipe can
     * find it by name without a walk of the tree.
     * <p>
     * Separate from {@link #addRecipeElement(S88RecipeElement)} because a nested step belongs to the
     * step above it and not to the recipe's own list. Adding it to both would put it in the tree
     * twice, and the index would have to guess which of the two the reader meant.
     *
     * @param parent step to attach under, ignored when {@code null}
     * @param child  step being attached, ignored when {@code null}
     */
    public void attachUnder(S88RecipeElement parent, S88RecipeElement child) {
        if (parent == null || child == null) {
            return;
        }
        parent.addRecipeElement(child);
        index(child);
    }

    /**
     * Detaches a step from the step above it and drops it from the index, so that a chart still
     * holding the old name finds nothing rather than a step that is no longer there.
     *
     * @param parent step it is attached to, {@code null} to use the one it remembers
     * @param child  step being detached, ignored when {@code null}
     * @return true when the step was attached there and has been taken off
     */
    public boolean detachFrom(S88RecipeElement parent, S88RecipeElement child) {
        if (child == null) {
            return false;
        }
        S88RecipeElement owner = parent != null ? parent : child.getParent();
        if (owner == null) {
            return false;
        }
        owner.removeRecipeElement(child);
        unindex(child);
        return true;
    }

    public List<S88OtherInformation> getOtherInformation() {
        return Collections.unmodifiableList(otherInformation);
    }

    public void addOtherInformation(S88OtherInformation info) {
        if (info != null) {
            otherInformation.add(info);
        }
    }

    public void removeOtherInformation(S88OtherInformation info) {
        otherInformation.remove(info);
    }

    /**
     * The step with the given id, at any depth of this recipe.
     *
     * @param wanted id to look for
     * @return the step, or empty when this recipe has none by that name
     */
    public Optional<S88RecipeElement> findElement(String wanted) {
        return Optional.ofNullable(elementById.get(wanted));
    }

    /**
     * Every step of this recipe at any depth, in the order they were added.
     *
     * @return the steps, this recipe's own children first and then the deeper ones
     */
    public List<S88RecipeElement> getAllElements() {
        List<S88RecipeElement> all = new ArrayList<>();
        for (S88RecipeElement element : recipeElements) {
            all.addAll(element.flatten());
        }
        return all;
    }

    /**
     * Ids shared by more than one step of this recipe.
     * <p>
     * A chart that points a step at an id held by two of them does not say which one it meant, so
     * the first one is what {@link #findElement(String)} returns and the clash is recorded rather
     * than thrown. A recipe that arrived broken from another tool is worth opening and repairing,
     * and refusing to load it would throw that away.
     *
     * @return the duplicated ids, in the order they were found
     */
    public Set<String> getDuplicateIds() {
        return Collections.unmodifiableSet(duplicateIds);
    }

    /**
     * How a parameter of this recipe names the variable it applies to.
     * <p>
     * This is the whole of the difference between the two kinds of recipe, and it is what the
     * binding asks. Whether the answer reaches anything in the plant is a question for whoever
     * binds this recipe, and not something this model can know.
     *
     * @param parameter parameter to read the address of
     * @return the address as this recipe writes it, or {@code null} when the parameter has no id
     */
    public abstract S88VariableAddress addressOf(S88RecipeParameter parameter);

    /**
     * Registers someone who has to hear about changes to this recipe, so that a tree, a table and a
     * chart showing it do not fall out of step with each other.
     *
     * @param listener who to tell, ignored when {@code null}
     */
    public void addChangeListener(S88RecipeChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(S88RecipeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Tells whoever is listening that something changed.
     * <p>
     * A listener that throws is not allowed to stop the others from hearing, because the thing being
     * reported has already happened and a view that is out of date is better than a view that never
     * updates at all.
     *
     * @param event what changed
     */
    public void fireChangeEvent(S88RecipeChangeEvent event) {
        for (S88RecipeChangeListener listener : listeners) {
            try {
                listener.onRecipeChange(event);
            } catch (RuntimeException ignored) {
                // A listener failing must not stop the rest from being told.
            }
        }
    }

    private void index(S88RecipeElement element) {
        if (element.getId() != null && !element.getId().isBlank()) {
            if (elementById.putIfAbsent(element.getId(), element) != null) {
                duplicateIds.add(element.getId());
            }
        }
        for (S88RecipeElement child : element.getRecipeElements()) {
            index(child);
        }
    }

    private void unindex(S88RecipeElement element) {
        if (element.getId() != null && elementById.get(element.getId()) == element) {
            elementById.remove(element.getId());
        }
        for (S88RecipeElement child : element.getRecipeElements()) {
            unindex(child);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + ", " + recipeElements.size() + " step(s)]";
    }
}
