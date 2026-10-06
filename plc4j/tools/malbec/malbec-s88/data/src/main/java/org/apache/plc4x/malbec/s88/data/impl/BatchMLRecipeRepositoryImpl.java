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
package org.apache.plc4x.malbec.s88.data.impl;

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88DataInterpretation;
import org.apache.plc4x.malbec.s88.api.S88Depiction;
import org.apache.plc4x.malbec.s88.api.S88EquipmentRequirement;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88IdScope;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88OtherInformation;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameterType;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.mesa.xml.b2MML.BatchEquipmentRequirementType;
import org.mesa.xml.b2MML.BatchInformationDocument;
import org.mesa.xml.b2MML.BatchParameterType;
import org.mesa.xml.b2MML.BatchValueType;
import org.mesa.xml.b2MML.ConstraintType;
import org.mesa.xml.b2MML.DescriptionType;
import org.mesa.xml.b2MML.FormulaType;
import org.mesa.xml.b2MML.FromIDType;
import org.mesa.xml.b2MML.IdentifierType;
import org.mesa.xml.b2MML.LinkType;
import org.mesa.xml.b2MML.MasterRecipeType;
import org.mesa.xml.b2MML.OtherInformationType;
import org.mesa.xml.b2MML.ProcedureLogicType;
import org.mesa.xml.b2MML.RecipeElementType;
import org.mesa.xml.b2MML.StepType;
import org.mesa.xml.b2MML.ToIDType;
import org.mesa.xml.b2MML.TransitionType;
import org.mesa.xml.b2MML.ValueStringType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Reads and writes recipes in the B2MML format of MESA.
 * <p>
 * The file is a {@code BatchInformation}, which is to recipes what {@code EquipmentInformation} is to
 * a plant: a document that can hold the whole set of a kind of thing. This one holds exactly one
 * {@code MasterRecipe}, because one file per recipe is what makes a recipe openable on its own,
 * without opening the rest of the project to find it.
 * <p>
 * Only master recipes are read and written. A control recipe is runtime state, built by binding a
 * master to a plant for one run, and what is kept of that run once it has happened is a production
 * record rather than a recipe, so there is nothing here for one.
 * <p>
 * The mapping is almost one to one, because the recipe format was designed around the same model.
 * Two things have no field of their own and are carried in {@code OtherInformation}, which the
 * format leaves free for exactly this: whether the recipe addresses equipment by class or by
 * instance, and the class a step of a class recipe applies to. The names of those entries are
 * constants, so there is one place that says what they mean.
 * <p>
 * <b>Two things are not carried, on purpose.</b> The layout of a chart, meaning where its boxes and
 * bars were drawn, has no room on a step or a bar in the format, and only the recipe and its steps
 * have somewhere free, so it is held in the model and not written. And the header, which is where
 * the format keeps who wrote a recipe and who approved it, has no field on a recipe either; it is
 * left out rather than smuggled in among the descriptions, because a description is something a
 * person wrote and a header entry is not, and putting one in place of the other would write back
 * something nobody wrote.
 */
public class BatchMLRecipeRepositoryImpl implements S88RecipeRepository {

    /**
     * Root element of a recipe file.
     * <p>
     * Worth knowing because the reading of a file turns on nothing else: a document whose root is
     * not this name is not a recipe file.
     */
public static final String ROOT = "BatchInformation";

    /**
     * The data type a value carries when the recipe does not say.
     * <p>
     * Text, because it is the one type every reader of a value understands. A recipe whose meaning
     * is not yet decided is nearly always about text, and a number written where the recipe has not
     * decided on one would be a claim the file does not make.
     */
    private static final String DEFAULT_DATA_TYPE = "string";

    /**
     * The version a box names when the step it points at has none.
     * <p>
     * One, because that is the first version of anything and because a recipe being drawn for the
     * first time is not asking for a version of a step that changes later.
     */
    private static final String FIRST_VERSION = "1";

    private final S88Storage storage;

    public BatchMLRecipeRepositoryImpl(S88Storage storage) {
        this.storage = storage;
    }

    @Override
    public S88MasterRecipe loadRecipe() {
        byte[] content;
        try (InputStream input = storage.openInput()) {
            content = input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("The recipe file could not be read.", e);
        }
        if (content.length == 0) {
            return null;
        }

        // Parsed twice on purpose. Parsing generically first is what tells a file that is not a
        // recipe apart from one that is, without having to trust a typed factory: a factory for a
        // document this repository knows about reads anything just as happily and then hands back
        // nothing usable.
        XmlObject parsed;
        try {
            parsed = XmlObject.Factory.parse(new ByteArrayInputStream(content));
        } catch (XmlException | IOException e) {
            throw new IllegalStateException("The recipe file is not valid XML.", e);
        }
        if (!ROOT.equals(rootOf(parsed))) {
            // Well formed XML that is not a recipe is not this repository's business, and there is
            // nothing to read into, so it reads as no recipe rather than as a broken one.
            return null;
        }

        S88MasterRecipe recipe = new S88MasterRecipe();
        try {
            BatchInformationDocument document =
                    BatchInformationDocument.Factory.parse(new ByteArrayInputStream(content));
            int found = document.getBatchInformation().sizeOfMasterRecipeArray();
            if (found > 1) {
                // The format allows a document to hold several, because the format is not about one
                // file per recipe. This one is, so which recipe is being opened has to be said
                // rather than guessed at by picking the first.
                throw new IllegalStateException("The recipe file holds " + found
                        + " recipes. A recipe file holds one, so opening this one is ambiguous.");
            }
            if (found == 1) {
                readMaster(document.getBatchInformation().getMasterRecipeArray(0), recipe);
            }
        } catch (XmlException | IOException e) {
            throw new IllegalStateException("The recipe file could not be read as a recipe.", e);
        }
        // The kind is read off the entry the recipe already carries rather than being left as the
        // default, and readStoredKind answers the case where the file says nothing at all.
        recipe.setKind(S88MasterRecipe.readStoredKind(recipe));
        return recipe;
    }

    /** The local name of the element at the root of the document. */
    private static String rootOf(XmlObject parsed) {
        try (org.apache.xmlbeans.XmlCursor cursor = parsed.newCursor()) {
            return cursor.toFirstChild() ? cursor.getName().getLocalPart() : null;
        }
    }

    @Override
    public void saveRecipe(S88MasterRecipe recipe) {
        if (recipe == null) {
            return;
        }
        try (OutputStream output = storage.openOutput()) {
            BatchInformationDocument document = BatchInformationDocument.Factory.newInstance();
            writeMaster(recipe, document.addNewBatchInformation().addNewMasterRecipe());
            // The same options the plant file is written with, so that the two read alike: pretty
            // printed, and not spreading the same declarations over every line.
            XmlOptions options = new XmlOptions();
            options.setSavePrettyPrint();
            options.setSaveAggressiveNamespaces();
            document.save(output, options);
        } catch (IOException e) {
            throw new IllegalStateException("The recipe file could not be written.", e);
        }
    }

// ========== The recipe itself ==========
    //
    // Everything below these two methods is shared by whatever holds recipe elements, because a
    // step, a chart or a parameter is the same type wherever it appears.

    private void readMaster(MasterRecipeType xml, S88MasterRecipe recipe) {
        recipe.setId(identifierOf(xml.getID()));
        recipe.setVersion(identifierOf(xml.getVersion()));
        recipe.setVersionDate(dateOf(xml.getVersionDate()));
        readDescriptions(xml.getDescriptionArray(), recipe::addDescription);
        readRequirements(xml.getEquipmentRequirementArray(), recipe::addEquipmentRequirement);
        if (xml.isSetFormula()) {
            for (BatchParameterType parameter : xml.getFormula().getParameterArray()) {
                recipe.addFormulaParameter(readParameter(parameter));
            }
        }
        if (xml.isSetProcedureLogic()) {
            recipe.setProcedureLogic(readChart(xml.getProcedureLogic()));
        }
        for (RecipeElementType element : xml.getRecipeElementArray()) {
            recipe.addRecipeElement(readElement(element));
        }
        readOtherInformation(xml.getOtherInformationArray(), recipe::addOtherInformation);
    }

private void writeMaster(S88MasterRecipe recipe, MasterRecipeType xml) {
        writeIdentity(recipe, xml::addNewID, xml::addNewVersion, xml::addNewVersionDate);
        recipe.getDescriptions().forEach(text -> xml.addNewDescription().setStringValue(text));
        recipe.getEquipmentRequirements().forEach(r -> writeRequirement(r, xml.addNewEquipmentRequirement()));
        writeFormula(recipe, xml::addNewFormula);
        writeChart(recipe, xml::addNewProcedureLogic);
        recipe.getRecipeElements().forEach(element -> writeElement(element, xml.addNewRecipeElement()));
        writeOtherInformation(recipe.getOtherInformation(), xml::addNewOtherInformation);
    }

private void writeIdentity(S88MasterRecipe recipe,
                               java.util.function.Supplier<IdentifierType> id,
                               java.util.function.Supplier<IdentifierType> version,
                               java.util.function.Supplier<org.mesa.xml.b2MML.DateTimeType> date) {
        if (recipe.getId() != null && !recipe.getId().isBlank()) {
            id.get().setStringValue(recipe.getId());
        }
        if (recipe.getVersion() != null && !recipe.getVersion().isBlank()) {
            version.get().setStringValue(recipe.getVersion());
        }
        if (recipe.getVersionDate() != null && !recipe.getVersionDate().isBlank()) {
            date.get().setCalendarValue(calendarOf(recipe.getVersionDate()));
        }
    }

private void writeFormula(S88MasterRecipe recipe,
                               java.util.function.Supplier<FormulaType> addFormula) {
        if (!recipe.hasFormula()) {
            return;
        }
        FormulaType xml = addFormula.get();
        recipe.getFormula().forEach(parameter -> writeParameter(parameter, xml.addNewParameter()));
    }

private void writeChart(S88MasterRecipe recipe,
                            java.util.function.Supplier<ProcedureLogicType> addChart) {
        if (!recipe.hasProcedureLogic()) {
            return;
        }
        writeChart(recipe.getProcedureLogic(), addChart.get());
    }

    // ========== Steps ==========

    private S88RecipeElement readElement(RecipeElementType xml) {
        S88RecipeElement element = new S88RecipeElement();
        element.setId(identifierOf(xml.getID()));
        element.setVersion(identifierOf(xml.getVersion()));
        element.setVersionDate(dateOf(xml.getVersionDate()));
        readDescriptions(xml.getDescriptionArray(), element::addDescription);
        if (textOf(xml.getRecipeElementType()) != null) {
            element.setKind(S88RecipeElementKind.fromString(textOf(xml.getRecipeElementType())));
        }
        element.setBuildingBlockElementId(identifierOf(xml.getBuildingBlockElementID()));
        element.setBuildingBlockElementVersion(identifierOf(xml.getBuildingBlockElementVersion()));
        for (IdentifierType equipment : xml.getActualEquipmentIDArray()) {
            element.addActualEquipmentId(identifierOf(equipment));
        }
        readRequirements(xml.getEquipmentRequirementArray(), element::addEquipmentRequirement);
        for (BatchParameterType parameter : xml.getParameterArray()) {
            element.addParameter(readParameter(parameter));
        }
        if (xml.isSetProcedureLogic()) {
            element.setProcedureLogic(readChart(xml.getProcedureLogic()));
        }
        for (RecipeElementType child : xml.getRecipeElementArray()) {
            element.addRecipeElement(readElement(child));
        }
        readOtherInformation(xml.getOtherInformationArray(), element::addOtherInformation);
        return element;
    }

    private void writeElement(S88RecipeElement source, RecipeElementType xml) {
        if (source.getId() != null) {
            xml.addNewID().setStringValue(source.getId());
        }
        if (source.getVersion() != null && !source.getVersion().isBlank()) {
            xml.addNewVersion().setStringValue(source.getVersion());
        }
        if (source.getVersionDate() != null && !source.getVersionDate().isBlank()) {
            xml.addNewVersionDate().setCalendarValue(calendarOf(source.getVersionDate()));
        }
        source.getDescriptions().forEach(text -> xml.addNewDescription().setStringValue(text));
        // What a step is, is mandatory in the format. A step that does not say is written as
        // whatever the vocabulary names last, so the file still opens, and the conformance rules
        // are what tell the reader that the step is unfinished.
        S88RecipeElementKind kind = source.getKind() != null
                ? source.getKind() : S88RecipeElementKind.OTHER;
        xml.addNewRecipeElementType().setStringValue(kind.getXmlName());
        if (source.getBuildingBlockElementId() != null) {
            xml.addNewBuildingBlockElementID().setStringValue(source.getBuildingBlockElementId());
        }
        if (source.getBuildingBlockElementVersion() != null) {
            xml.addNewBuildingBlockElementVersion().setStringValue(source.getBuildingBlockElementVersion());
        }
        source.getActualEquipmentIds().forEach(id -> xml.addNewActualEquipmentID().setStringValue(id));
        source.getEquipmentRequirements().forEach(r -> writeRequirement(r, xml.addNewEquipmentRequirement()));
        source.getParameters().forEach(parameter -> writeParameter(parameter, xml.addNewParameter()));
        if (source.hasProcedureLogic()) {
            writeChart(source.getProcedureLogic(), xml.addNewProcedureLogic());
        }
        source.getRecipeElements().forEach(child -> writeElement(child, xml.addNewRecipeElement()));
        writeOtherInformation(source.getOtherInformation(), xml::addNewOtherInformation);
    }

    // ========== The chart ==========

    private S88ProcedureLogic readChart(ProcedureLogicType xml) {
        S88ProcedureLogic chart = new S88ProcedureLogic();
        // The order the format asks for is links, steps, transitions. Reading in any other order
        // would not matter here, because a link names its ends rather than holding them, but
        // following the same order as writing keeps the two easy to compare.
        for (LinkType link : xml.getLinkArray()) {
            chart.addLink(readLink(link));
        }
        for (StepType step : xml.getStepArray()) {
            chart.addStep(readStep(step));
        }
for (TransitionType transition : xml.getTransitionArray()) {
            chart.addTransition(readTransition(transition));
        }
        return chart;
    }

    private void writeChart(S88ProcedureLogic source, ProcedureLogicType xml) {
        source.getLinks().forEach(link -> writeLink(link, xml.addNewLink()));
        source.getSteps().forEach(step -> writeStep(step, xml.addNewStep()));
        source.getTransitions().forEach(bar -> writeTransition(bar, xml.addNewTransition()));
        // No free-form entry is written here, because a chart has none in the format. Where a chart
        // is drawn is carried by whatever owns the chart: the recipe for the recipe's own chart, and
        // the step for the chart of a step. Both of those do have a free-form entry.
    }

    private S88ProcedureStep readStep(StepType xml) {
        S88ProcedureStep step = new S88ProcedureStep();
        step.setId(identifierOf(xml.getID()));
        step.setRecipeElementId(identifierOf(xml.getRecipeElementID()));
        step.setRecipeElementVersion(identifierOf(xml.getRecipeElementVersion()));
        readDescriptions(xml.getDescriptionArray(), step::addDescription);
        return step;
    }

private void writeStep(S88ProcedureStep source, StepType xml) {
        if (source.getId() != null) {
            xml.addNewID().setStringValue(source.getId());
        }
        // Both of these are mandatory in the format: a box on a chart is only a reference to a step
        // of the recipe, and the format makes it name one in full rather than leave it implied. A
        // box written before the step it points at has been given a version names the first version
        // of it, which is what the step is at that moment. Leaving the field out would produce a
        // file the schema rejects, and leaving it empty would produce a box that points at no
        // version at all while looking as though it did.
        xml.addNewRecipeElementID().setStringValue(
                source.getRecipeElementId() != null ? source.getRecipeElementId() : "");
        xml.addNewRecipeElementVersion().setStringValue(
                source.getRecipeElementVersion() != null
                        ? source.getRecipeElementVersion() : FIRST_VERSION);
        source.getDescriptions().forEach(text -> xml.addNewDescription().setStringValue(text));
    }

    private S88ProcedureLink readLink(LinkType xml) {
        S88ProcedureLink link = new S88ProcedureLink();
        link.setId(identifierOf(xml.getID()));
for (FromIDType from : xml.getFromIDArray()) {
            link.addFrom(new S88IdRef(from.getFromIDValue(),
                    S88IdRefType.fromString(textOf(from.getFromType())),
                    S88IdScope.fromString(textOf(from.getIDScope()))));
        }
        for (ToIDType to : xml.getToIDArray()) {
            link.addTo(new S88IdRef(to.getToIDValue(),
                    S88IdRefType.fromString(textOf(to.getToType())),
                    S88IdScope.fromString(textOf(to.getIDScope()))));
        }
        if (textOf(xml.getLinkType()) != null) {
            link.setLinkType(S88LinkType.fromString(textOf(xml.getLinkType())));
        }
        if (textOf(xml.getDepiction()) != null) {
            link.setDepiction(S88Depiction.fromString(textOf(xml.getDepiction())));
        }
        if (textOf(xml.getEvaluationOrder()) != null) {
            // The evaluation order is a number in the recipe format with no getter that hands one
            // back, so it is read as the text it was written as. A file that says something else is
            // a file written by somebody else, and the order is not worth refusing the recipe over.
            Integer order = integerOf(textOf(xml.getEvaluationOrder()));
            if (order != null) {
                link.setEvaluationOrder(order);
            }
        }
        readDescriptions(xml.getDescriptionArray(), link::addDescription);
        return link;
    }

    private void writeLink(S88ProcedureLink source, LinkType xml) {
        if (source.getId() != null) {
            xml.addNewID().setStringValue(source.getId());
        }
for (S88IdRef ref : source.getFrom()) {
            FromIDType from = xml.addNewFromID();
            from.setFromIDValue(ref.getValue());
            from.addNewFromType().setStringValue(ref.getType().getXmlName());
            from.addNewIDScope().setStringValue(ref.getScope().getXmlName());
        }
        for (S88IdRef ref : source.getTo()) {
            ToIDType to = xml.addNewToID();
            to.setToIDValue(ref.getValue());
            to.addNewToType().setStringValue(ref.getType().getXmlName());
            to.addNewIDScope().setStringValue(ref.getScope().getXmlName());
        }
        xml.addNewLinkType().setStringValue(source.getLinkType().getXmlName());
        xml.addNewDepiction().setStringValue(source.getDepiction().getXmlName());
        if (source.getEvaluationOrder() != null) {
            xml.addNewEvaluationOrder().setStringValue(String.valueOf(source.getEvaluationOrder()));
        }
        source.getDescriptions().forEach(description -> xml.addNewDescription().setStringValue(description));
    }

    private S88ProcedureTransition readTransition(TransitionType xml) {
        S88ProcedureTransition bar = new S88ProcedureTransition();
        bar.setId(identifierOf(xml.getID()));
        // The comparison travels as written. A bar is the only place in the format that says what
        // has to be true, and reading it as a bare address would throw away what it is compared
        // against.
        if (textOf(xml.getCondition()) != null) {
            bar.setCondition(textOf(xml.getCondition()));
        }
        if (textOf(xml.getConditionAnnotation()) != null) {
            bar.setConditionAnnotation(textOf(xml.getConditionAnnotation()));
        }
        readDescriptions(xml.getDescriptionArray(), bar::addDescription);
        return bar;
    }

    private void writeTransition(S88ProcedureTransition source, TransitionType xml) {
        if (source.getId() != null) {
            xml.addNewID().setStringValue(source.getId());
        }
        if (source.getCondition() != null && !source.getCondition().isBlank()) {
            xml.addNewCondition().setStringValue(source.getCondition());
        }
        if (source.getConditionAnnotation() != null && !source.getConditionAnnotation().isBlank()) {
            xml.addNewConditionAnnotation().setStringValue(source.getConditionAnnotation());
        }
        source.getDescriptions().forEach(text -> xml.addNewDescription().setStringValue(text));
    }

    // ========== Parameters and values ==========

    private S88RecipeParameter readParameter(BatchParameterType xml) {
        S88RecipeParameter parameter = new S88RecipeParameter();
        parameter.setId(identifierOf(xml.getID()));
        if (textOf(xml.getDescription()) != null) {
            parameter.setDescription(textOf(xml.getDescription()));
        }
        if (textOf(xml.getParameterType()) != null) {
            parameter.setParameterType(S88RecipeParameterType.fromString(textOf(xml.getParameterType())));
        }
        for (org.mesa.xml.b2MML.ParameterSubTypeType subType : xml.getParameterSubTypeArray()) {
            parameter.addParameterSubType(textOf(subType));
        }
        for (BatchValueType value : xml.getValueArray()) {
            parameter.addValue(readValue(value));
        }
        if (textOf(xml.getScaled()) != null) {
            parameter.setScaled("Yes".equalsIgnoreCase(textOf(xml.getScaled())));
        }
        if (textOf(xml.getScaleReference()) != null) {
            parameter.setScaleReference(textOf(xml.getScaleReference()));
        }
        for (BatchParameterType nested : xml.getParameterArray()) {
            parameter.addParameter(readParameter(nested));
        }
        return parameter;
    }

    private void writeParameter(S88RecipeParameter source, BatchParameterType xml) {
        if (source.getId() != null) {
            xml.addNewID().setStringValue(source.getId());
        }
        if (source.getDescription() != null && !source.getDescription().isBlank()) {
            xml.addNewDescription().setStringValue(source.getDescription());
        }
        xml.addNewParameterType().setStringValue(source.getParameterType().getXmlName());
        source.getParameterSubTypes().forEach(subType ->
                xml.addNewParameterSubType().setStringValue(subType));
        source.getValues().forEach(value -> writeValue(value, xml.addNewValue()));
        if (source.getScaled() != null) {
            xml.addNewScaled().setStringValue(source.getScaled() ? "Yes" : "No");
        }
        if (source.getScaleReference() != null && !source.getScaleReference().isBlank()) {
            xml.addNewScaleReference().setStringValue(source.getScaleReference());
        }
        source.getParameters().forEach(nested -> writeParameter(nested, xml.addNewParameter()));
    }

    private S88ParameterValue readValue(BatchValueType xml) {
        S88ParameterValue value = new S88ParameterValue();
        for (ValueStringType text : xml.getValueStringArray()) {
            value.addValueString(textOf(text));
        }
        if (textOf(xml.getDataInterpretation()) != null) {
            value.setDataInterpretation(S88DataInterpretation.fromString(textOf(xml.getDataInterpretation())));
        }
if (textOf(xml.getDataType()) != null) {
            value.setDataType(dataTypeOf(textOf(xml.getDataType())));
        }
        if (textOf(xml.getUnitOfMeasure()) != null) {
            value.setUnitOfMeasure(textOf(xml.getUnitOfMeasure()));
        }
        for (IdentifierType id : xml.getEnumerationSetIDArray()) {
            value.addEnumerationSetId(identifierOf(id));
        }
        return value;
    }

    private void writeValue(S88ParameterValue source, BatchValueType xml) {
source.getValueStrings().forEach(text -> xml.addNewValueString().setStringValue(text));
        S88DataInterpretation interpretation = source.getDataInterpretation() != null
                ? source.getDataInterpretation() : S88DataInterpretation.CONSTANT;
        xml.addNewDataInterpretation().setStringValue(interpretation.getXmlName());

        // Both of these are mandatory in the format, and a recipe is written long before anyone
        // knows which plant it will run in, so a value often carries neither. They are written
        // anyway: leaving a mandatory element out is a document the schema rejects, and a recipe
        // that no other tool can open is worse than one that says "this is text, with no unit".
        xml.addNewDataType().setStringValue(source.getDataType() != null
                ? xmlNameOf(source.getDataType()) : DEFAULT_DATA_TYPE);
        org.mesa.xml.b2MML.UnitOfMeasureType unit = xml.addNewUnitOfMeasure();
        if (source.getUnitOfMeasure() != null && !source.getUnitOfMeasure().isBlank()) {
            unit.setStringValue(source.getUnitOfMeasure());
        }

        source.getEnumerationSetIds().forEach(id -> xml.addNewEnumerationSetID().setStringValue(id));
    }

    // ========== Requirements ==========

    private void readRequirements(BatchEquipmentRequirementType[] all,
                                 Consumer<S88EquipmentRequirement> sink) {
        for (BatchEquipmentRequirementType xml : all) {
            S88EquipmentRequirement requirement = new S88EquipmentRequirement();
            requirement.setId(identifierOf(xml.getID()));
            for (ConstraintType constraint : xml.getConstraintArray()) {
                if (textOf(constraint.getCondition()) != null) {
                    requirement.addConstraint(textOf(constraint.getCondition()));
                }
            }
            if (textOf(xml.getDescription()) != null) {
                requirement.setDescription(textOf(xml.getDescription()));
            }
            sink.accept(requirement);
        }
    }

    private void writeRequirement(S88EquipmentRequirement source, BatchEquipmentRequirementType xml) {
        if (source.getId() != null && !source.getId().isBlank()) {
            xml.addNewID().setStringValue(source.getId());
        }
        source.getConstraints().forEach(constraint ->
                xml.addNewConstraint().addNewCondition().setStringValue(constraint));
        if (source.getDescription() != null && !source.getDescription().isBlank()) {
            xml.addNewDescription().setStringValue(source.getDescription());
        }
    }

    // ========== The free-form entries ==========

    private void readOtherInformation(OtherInformationType[] all,
                                     Consumer<S88OtherInformation> sink) {
        for (OtherInformationType xml : all) {
            S88OtherInformation info = new S88OtherInformation();
            info.setId(identifierOf(xml.getID()));
            for (BatchValueType value : xml.getValueArray()) {
                info.addValue(readValue(value));
            }
            readDescriptions(xml.getDescriptionArray(), info::addDescription);
            sink.accept(info);
        }
    }

    private void writeOtherInformation(List<S88OtherInformation> all,
                                       java.util.function.Supplier<OtherInformationType> sink) {
for (S88OtherInformation source : all) {
            // The entry has to come from the sink: building one of our own and then adding another
            // through the sink would leave an empty entry in the file and this one's contents
            // nowhere, which is how a named entry ends up lost without anything saying so.
            OtherInformationType xml = sink.get();
            if (source.getId() != null) {
                xml.addNewID().setStringValue(source.getId());
            }
            source.getValues().forEach(value -> writeValue(value, xml.addNewValue()));
            source.getDescriptions().forEach(text -> xml.addNewDescription().setStringValue(text));
        }
    }

// ========== Small helpers ==========

    // There is deliberately no schema validation here. XmlObject.validate() answers "Invalid type"
    // for every document in this generated type system, the plant file included, so it would print
    // the same complaint on every recipe ever opened and say nothing true about any of them. Whether
    // a recipe holds together is the question of the conformance rules in the core module.

    private void readDescriptions(DescriptionType[] all, Consumer<String> sink) {
        for (DescriptionType description : all) {
            sink.accept(textOf(description));
        }
    }

    /** The text of something that wraps a string, when it is there. */
    private static String textOf(org.apache.xmlbeans.XmlAnySimpleType value) {
        if (value == null) {
            return null;
        }
        String text = value.getStringValue();
        return text == null || text.isEmpty() ? null : text;
    }

/** The text of something whose getter already hands back a plain string. */
    private static Integer integerOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String identifierOf(IdentifierType value) {
        return textOf(value);
    }

    private static String dateOf(org.mesa.xml.b2MML.DateTimeType value) {
        if (value == null || value.getCalendarValue() == null) {
            return null;
        }
        return value.getCalendarValue().toInstant().toString();
    }

/**
     * The name the recipe format uses for one of our data types.
     * <p>
     * The two vocabularies do not use the same names and one of them is fixed by the schema, so
     * this is a mapping and not a change of name: {@code INTEGER} in the format is
     * {@code DataType.INTEGER} here, and writing ours straight into the file would produce a
     * document the schema does not accept.
     */
    private static String xmlNameOf(DataType type) {
        return switch (type) {
            case INTEGER -> "int";
            case REAL -> "double";
            case STRING -> "string";
            case ENUMERATION -> "Code";
        };
    }

    /**
     * Our name for a data type the recipe format wrote.
     * <p>
     * Read as what it is rather than as the closest name of ours, because a file written by
     * another tool may well say {@code double} where ours says {@code REAL}, and refusing the
     * recipe over a name we simply did not anticipate would lose everything else in the file.
     * A name we do not recognise is left unset, which leaves the value readable as text and lets
     * whoever opens the recipe decide what it meant.
     */
    private static DataType dataTypeOf(String text) {
        if (text == null) {
            return null;
        }
        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "int", "integer", "long", "short", "unsignedint", "unsignedlong", "unsignedshort",
                 "byte", "unsignedbyte", "positiveinteger", "negativeinteger",
                 "nonnegativeinteger", "nonpositiveinteger" -> DataType.INTEGER;
            case "double", "float", "decimal" -> DataType.REAL;
            case "string", "text" -> DataType.STRING;
            case "code", "enumeration" -> DataType.ENUMERATION;
            default -> null;
        };
    }

    private static Calendar calendarOf(String text) {
        // At UTC on purpose. A date is written as it is stored, which is an instant in UTC, so
        // reading it back with the same zone is what makes a date survive being saved twice.
        return GregorianCalendar.from(java.time.Instant.parse(text).atZone(java.time.ZoneOffset.UTC));
    }
}




