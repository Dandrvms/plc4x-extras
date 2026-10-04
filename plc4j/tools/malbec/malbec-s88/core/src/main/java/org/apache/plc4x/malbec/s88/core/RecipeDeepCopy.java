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
import org.apache.plc4x.malbec.s88.api.S88EquipmentRequirement;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88OtherInformation;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;

/**
 * Copies a step, or a whole recipe, without sharing anything with the original.
 * <p>
 * A recipe is a document, and the obvious way to change one is to copy it first. That only works if
 * the copy shares nothing: a copy that still points at the original's parameters, chart or nested
 * steps is two names for one thing, and saving one of them writes changes into the other. Every
 * container is rebuilt and every value in it is put across by hand for that reason.
 * <p>
 * The chart is the one place where a copy has to decide something. {@link #copyRecipe} leaves the
 * chart to the caller, because the chart points at steps by name and a caller who copies one step
 * has to say what the new box should be called; {@link #copyChart} copies it as it stands, keeping
 * the same names, which is right when the whole recipe is being copied and the names come with it.
 */
public class RecipeDeepCopy {

    private RecipeDeepCopy() {
        /* This utility class should not be instantiated */
    }

    /**
     * A copy of a step and everything under it. The copy is not attached to anything, and the
     * original is left exactly as it was, way back included.
     *
     * @param source step to copy, may be {@code null}
     * @return the copy, or {@code null} when there was nothing to copy
     */
    public static S88RecipeElement copyElement(S88RecipeElement source) {
        if (source == null) {
            return null;
        }
        S88RecipeElement copy = new S88RecipeElement(source.getId(), source.getKind());
        copy.setVersion(source.getVersion());
        copy.setVersionDate(source.getVersionDate());
        source.getDescriptions().forEach(copy::addDescription);
        copy.setBuildingBlockElementId(source.getBuildingBlockElementId());
        copy.setBuildingBlockElementVersion(source.getBuildingBlockElementVersion());
        source.getActualEquipmentIds().forEach(copy::addActualEquipmentId);
        copy.setEquipmentClassId(source.getEquipmentClassId());
        for (S88EquipmentRequirement requirement : source.getEquipmentRequirements()) {
            copy.addEquipmentRequirement(copyRequirement(requirement));
        }
        source.getParameters().forEach(parameter -> copy.addParameter(copyParameter(parameter)));
        for (S88OtherInformation info : source.getOtherInformation()) {
            copy.addOtherInformation(copyOtherInformation(info));
        }
        if (source.hasProcedureLogic()) {
            copy.setProcedureLogic(copyChart(source.getProcedureLogic()));
        }
        for (S88RecipeElement child : source.getRecipeElements()) {
            copy.addRecipeElement(copyElement(child));
        }
        return copy;
    }

    /**
     * A copy of a whole recipe, chart included and keeping the same names for its steps. Used when a
     * recipe is duplicated, or when a master recipe is read to be set for a batch, where the copy has
     * to keep pointing at the same names for that to mean anything.
     *
     * @param source recipe to copy, may be {@code null}
     * @return a recipe of the same kind as the source, or {@code null} when there was nothing to copy
     */
    public static S88Recipe copyRecipe(S88Recipe source) {
        if (source == null) {
            return null;
        }
        S88Recipe copy = newRecipeOfSameKind(source);
        copy.setId(source.getId());
        copy.setVersion(source.getVersion());
        copy.setVersionDate(source.getVersionDate());
        source.getDescriptions().forEach(copy::addDescription);
        for (S88EquipmentRequirement requirement : source.getEquipmentRequirements()) {
            copy.addEquipmentRequirement(copyRequirement(requirement));
        }
        source.getFormula().forEach(value -> copy.addFormulaParameter(copyValue(value)));
        for (S88OtherInformation info : source.getOtherInformation()) {
            copy.addOtherInformation(copyOtherInformation(info));
        }
        if (source.hasProcedureLogic()) {
            copy.setProcedureLogic(copyChart(source.getProcedureLogic()));
        }
        for (S88RecipeElement element : source.getRecipeElements()) {
            copy.addRecipeElement(copyElement(element));
        }
        return copy;
    }

    /**
     * A recipe of the same kind as the one given, carrying nothing of it.
     * <p>
     * The kind matters because a control recipe is set for particular equipment and named after its
     * batch, and a copy that came back as a plain master would lose both. It is kept apart from
     * {@link #copyRecipe} so that a caller setting a recipe for a batch can make the shell first,
     * give it the batch, and only then put the steps in.
     *
     * @param source recipe whose kind is to be matched, may be {@code null}
     * @return an empty recipe of the same kind, a master when the kind is not known
     */
    public static S88Recipe newRecipeOfSameKind(S88Recipe source) {
        if (source instanceof S88ControlRecipe control) {
            return new S88ControlRecipe(control.getId(), control.getBatchId());
        }
        if (source instanceof S88MasterRecipe master) {
            return new S88MasterRecipe(master.getId(), master.getKind());
        }
        return new S88MasterRecipe(source != null ? source.getId() : null, S88RecipeKind.CLASS);
    }

    /** A copy of a chart, keeping the names of its steps, links and bars. */
    public static S88ProcedureLogic copyChart(S88ProcedureLogic source) {
        if (source == null) {
            return null;
        }
        S88ProcedureLogic copy = new S88ProcedureLogic();
        for (S88ProcedureStep step : source.getSteps()) {
            S88ProcedureStep stepCopy = new S88ProcedureStep(step.getId(), step.getRecipeElementId());
            stepCopy.setRecipeElementVersion(step.getRecipeElementVersion());
            step.getDescriptions().forEach(stepCopy::addDescription);
            copy.addStep(stepCopy);
        }
        for (S88ProcedureTransition transition : source.getTransitions()) {
            S88ProcedureTransition transitionCopy =
                    new S88ProcedureTransition(transition.getId(), transition.getCondition());
            transitionCopy.setConditionAnnotation(transition.getConditionAnnotation());
            transition.getDescriptions().forEach(transitionCopy::addDescription);
            copy.addTransition(transitionCopy);
        }
        for (S88ProcedureLink link : source.getLinks()) {
            S88ProcedureLink linkCopy = new S88ProcedureLink(link.getId());

            link.getFrom().forEach(ref -> linkCopy.addFrom(
                    new S88IdRef(ref.getValue(), ref.getType(), ref.getScope())));
            link.getTo().forEach(ref -> linkCopy.addTo(
                    new S88IdRef(ref.getValue(), ref.getType(), ref.getScope())));
            linkCopy.setLinkType(link.getLinkType());
            linkCopy.setDepiction(link.getDepiction());
            linkCopy.setEvaluationOrder(link.getEvaluationOrder());
            link.getDescriptions().forEach(linkCopy::addDescription);
            copy.addLink(linkCopy);
        }
        for (S88OtherInformation info : source.getOtherInformation()) {
            copy.addOtherInformation(copyOtherInformation(info));
        }
        return copy;
    }

    private static S88EquipmentRequirement copyRequirement(S88EquipmentRequirement source) {
        S88EquipmentRequirement copy = new S88EquipmentRequirement(source.getId());
        copy.setDescription(source.getDescription());
        source.getConstraints().forEach(copy::addConstraint);
        return copy;
    }

    private static S88RecipeParameter copyParameter(S88RecipeParameter source) {
        S88RecipeParameter copy = new S88RecipeParameter(source.getId());
        copy.setDescription(source.getDescription());
        copy.setParameterType(source.getParameterType());
        source.getParameterSubTypes().forEach(copy::addParameterSubType);
        source.getValues().forEach(value -> copy.addValue(copyValue(value)));
        copy.setScaled(source.getScaled());
        copy.setScaleReference(source.getScaleReference());
        for (S88RecipeParameter nested : source.getParameters()) {
            copy.addParameter(copyParameter(nested));
        }
        return copy;
    }

    private static S88ParameterValue copyValue(S88ParameterValue source) {
        S88ParameterValue copy = new S88ParameterValue();
        source.getValueStrings().forEach(copy::addValueString);
        copy.setDataInterpretation(source.getDataInterpretation());
        copy.setDataType(source.getDataType());
        copy.setUnitOfMeasure(source.getUnitOfMeasure());
        source.getEnumerationSetIds().forEach(copy::addEnumerationSetId);
        return copy;
    }

    private static S88OtherInformation copyOtherInformation(S88OtherInformation source) {
        S88OtherInformation copy = new S88OtherInformation(source.getId());
        source.getValues().forEach(value -> copy.addValue(copyValue(value)));
        source.getDescriptions().forEach(copy::addDescription);
        return copy;
    }
}
