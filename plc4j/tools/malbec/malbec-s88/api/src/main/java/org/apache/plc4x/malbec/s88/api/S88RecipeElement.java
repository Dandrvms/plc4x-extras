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
import java.util.Optional;

/**
 * One step of a recipe: which element of the plant it works on, what it sets, and what it is made
 * of.
 * <p>
 * <b>A step that works on equipment is called by the name that equipment has in the plant.</b> If the
 * module is called {@code HEAT} then the step is called {@code HEAT}, and the same name is what
 * {@link #getActualEquipmentIds()} and {@link #getEquipmentClassId()} carry.
 * <p>
 * The levels above the equipment or unit do not have an element of the plant behind them and are named for
 * themselves: a procedure is a procedure wherever it sits. {@link S88RecipeElementKind} says which
 * of the two a step is.
 * <p>
 * This element gives value to the properties defined in the module: the parameters say
 * what to set on it, and the bars of the chart say what has to be read back off it before moving on,
 * which is where its reports come in. Nothing the plant owns is repeated here, so a recipe stays
 * true to the plant it was written against rather than drifting into a copy of it.
 * <p>
 * Elements nest, and a {@link S88ProcedureLogic} chart may be drawn at any level, so one element type
 * carries both the depth of the process and the flow through it.
 */
public class S88RecipeElement {

    private String id;
    private String version;
    private String versionDate;
    private final List<String> descriptions = new ArrayList<>();
    private S88RecipeElementKind kind;
    private String buildingBlockElementId;
    private String buildingBlockElementVersion;
    private final List<String> actualEquipmentIds = new ArrayList<>();
    private final List<S88EquipmentRequirement> equipmentRequirements = new ArrayList<>();
    private final List<S88RecipeParameter> parameters = new ArrayList<>();
    private S88ProcedureLogic procedureLogic;
    private final List<S88RecipeElement> recipeElements = new ArrayList<>();
    private final List<S88OtherInformation> otherInformation = new ArrayList<>();
    private S88RecipeElement parent;

    public S88RecipeElement() {
    }

    public S88RecipeElement(String id, S88RecipeElementKind kind) {
        this.id = id;
        this.kind = kind;
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

    public S88RecipeElementKind getKind() {
        return kind;
    }

    public void setKind(S88RecipeElementKind kind) {
        this.kind = kind;
    }

    public String getBuildingBlockElementId() {
        return buildingBlockElementId;
    }

    public void setBuildingBlockElementId(String buildingBlockElementId) {
        this.buildingBlockElementId = buildingBlockElementId;
    }

    public String getBuildingBlockElementVersion() {
        return buildingBlockElementVersion;
    }

    public void setBuildingBlockElementVersion(String buildingBlockElementVersion) {
        this.buildingBlockElementVersion = buildingBlockElementVersion;
    }

    /** The equipment this element applies to, one or more concrete modules of the plant. */
    public List<String> getActualEquipmentIds() {
        return Collections.unmodifiableList(actualEquipmentIds);
    }

    public void addActualEquipmentId(String equipmentId) {
        if (equipmentId != null && !equipmentId.isBlank()) {
            actualEquipmentIds.add(equipmentId);
        }
    }

    public void removeActualEquipmentId(String equipmentId) {
        actualEquipmentIds.remove(equipmentId);
    }

    /** What this element needs from the equipment, as opposed to the equipment it was given. */
    public List<S88EquipmentRequirement> getEquipmentRequirements() {
        return Collections.unmodifiableList(equipmentRequirements);
    }

    public void addEquipmentRequirement(S88EquipmentRequirement requirement) {
        if (requirement != null) {
            equipmentRequirements.add(requirement);
        }
    }

    public void removeEquipmentRequirement(S88EquipmentRequirement requirement) {
        equipmentRequirements.remove(requirement);
    }

    public List<S88RecipeParameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public void addParameter(S88RecipeParameter parameter) {
        if (parameter != null) {
            parameters.add(parameter);
        }
    }

    public void removeParameter(S88RecipeParameter parameter) {
        parameters.remove(parameter);
    }

    /** The first parameter addressing the given name, wherever it sits among the nested ones. */
    public Optional<S88RecipeParameter> findParameter(String address) {
        for (S88RecipeParameter parameter : parameters) {
            if (address != null && address.equals(parameter.getId())) {
                return Optional.of(parameter);
            }
            for (S88RecipeParameter nested : parameter.getParameters()) {
                if (address != null && address.equals(nested.getId())) {
                    return Optional.of(nested);
                }
            }
        }
        return Optional.empty();
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

    /** The elements made of this one, at the level below. */
    public List<S88RecipeElement> getRecipeElements() {
        return Collections.unmodifiableList(recipeElements);
    }

    /**
     * The element this one is made of, or {@code null} when it is a step of the recipe itself.
     * <p>
     * Kept as a way back so that removing a step does not have to be told where it is, in the same
     * way the plant model does for an element. It is set when a step is attached and cleared when it
     * is detached, so it cannot name an element that is no longer its parent.
     *
     * @return the element above this one, {@code null} when there is none
     */
    public S88RecipeElement getParent() {
        return parent;
    }

    /** True when this element is a step of the recipe itself rather than of another step. */
    public boolean isTopLevel() {
        return parent == null;
    }

    /**
     * Attaches a step under this one, setting its way back to this element so it can be detached
     * again without having to be told where it is.
     */
    public void addRecipeElement(S88RecipeElement child) {
        if (child != null) {
            child.parent = this;
            recipeElements.add(child);
        }
    }

    /**
     * Detaches a step. The way back is cleared, so a step that is later added somewhere else does not
     * answer two parents at once.
     */
    public void removeRecipeElement(S88RecipeElement child) {
        if (child != null && recipeElements.remove(child) && child.parent == this) {
            child.parent = null;
        }
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
     * The class of the equipment this element applies to, which is how a
     * {@link S88RecipeKind#CLASS} recipe names it.
     *
     * @return the class id, or {@code null} when this element does not name one
     */
    public String getEquipmentClassId() {
        S88OtherInformation info = S88OtherInformation.find(otherInformation,
                S88OtherInformation.EQUIPMENT_CLASS_ID);
        return info != null ? info.getFirstValue() : null;
    }

    /**
     * Records the class of the equipment this element applies to, replacing any already held.
     *
     * @param classId the class id, {@code null} to remove it
     */
    public void setEquipmentClassId(String classId) {
        otherInformation.removeIf(info -> S88OtherInformation.EQUIPMENT_CLASS_ID
                .equalsIgnoreCase(info.getId()));
        if (classId != null && !classId.isBlank()) {
            addOtherInformation(S88OtherInformation.of(S88OtherInformation.EQUIPMENT_CLASS_ID, classId));
        }
    }

    /**
     * The first element directly under this one with the given id, at any depth.
     *
     * @param wanted id to look for
     * @return the element, or empty when this one carries no such descendant
     */
    public Optional<S88RecipeElement> findDescendant(String wanted) {
        if (wanted == null) {
            return Optional.empty();
        }
        for (S88RecipeElement child : recipeElements) {
            if (wanted.equals(child.getId())) {
                return Optional.of(child);
            }
            Optional<S88RecipeElement> deeper = child.findDescendant(wanted);
            if (deeper.isPresent()) {
                return deeper;
            }
        }
        return Optional.empty();
    }

    /**
     * This element and every element below it, in the order they were added. Used to walk a whole
     * subtree when a recipe is checked against a plant, which has to reach the deepest step and not
     * only the ones at the top.
     *
     * @return this element followed by its descendants
     */
    public List<S88RecipeElement> flatten() {
        List<S88RecipeElement> all = new ArrayList<>();
        collect(this, all);
        return all;
    }

    private static void collect(S88RecipeElement element, List<S88RecipeElement> into) {
        into.add(element);
        for (S88RecipeElement child : element.getRecipeElements()) {
            collect(child, into);
        }
    }

    @Override
    public String toString() {
        return "S88RecipeElement[" + id + ", " + kind + ", " + parameters.size() + " parameter(s), "
                + recipeElements.size() + " child(ren)]";
    }
}
