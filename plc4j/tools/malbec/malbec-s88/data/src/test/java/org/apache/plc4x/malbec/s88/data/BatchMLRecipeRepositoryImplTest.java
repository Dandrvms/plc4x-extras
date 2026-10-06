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
package org.apache.plc4x.malbec.s88.data;

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ChartLayout;
import org.apache.plc4x.malbec.s88.api.S88Position;
import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88IdScope;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88OtherInformation;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;
import org.apache.plc4x.malbec.s88.data.impl.BatchMLRecipeRepositoryImpl;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.mesa.xml.b2MML.BatchInformationDocument;
import org.mesa.xml.b2MML.BatchInformationType;
import org.mesa.xml.b2MML.MasterRecipeType;
import org.mesa.xml.b2MML.RecipeElementType;
import org.mesa.xml.b2MML.StepType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe written and read again.
 * <p>
 * Every test here writes a recipe, reads it back and says what survived. The last group checks the
 * file against the recipe format itself, which is what pins the words and the labels: until a test
 * validates, a wrong label is written out and read back as if it were right, and nothing notices.
 */
class BatchMLRecipeRepositoryImplTest {

    // ========== A master written by class ==========

    @Test
    void aMasterWrittenByClassComesBackWrittenByClass() {
        S88MasterRecipe master = new S88MasterRecipe("REC_CALENTAMIENTO", S88RecipeKind.CLASS);
        S88RecipeElement operation = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        operation.setEquipmentClassId("HEATER");
        master.addRecipeElement(operation);

InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertSame(S88RecipeKind.CLASS, read.getKind(),
                "a recipe that names the class still names the class after a trip through the"
                        + " file, which is the whole point of writing one");
        S88RecipeElement readOperation = read.findElement("HEAT").orElseThrow();
        assertEquals("HEATER", readOperation.getEquipmentClassId(),
                "the class a step applies to rides along as a named entry rather than being lost");
        assertTrue(readOperation.getActualEquipmentIds().isEmpty());
    }

    @Test
    void aMasterWrittenForParticularEquipmentComesBackForParticularEquipment() {
        S88MasterRecipe master = new S88MasterRecipe("REC_LOTE", S88RecipeKind.INSTANCE);
        S88RecipeElement operation = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        operation.addActualEquipmentId("CALENTAMIENTO_TANQUE_1");
        master.addRecipeElement(operation);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertSame(S88RecipeKind.INSTANCE, read.getKind(),
                "which of a thousand free units a recipe names is the one choice the engineer makes"
                        + " while writing it, so it has to come back the same way");
        assertEquals(List.of("CALENTAMIENTO_TANQUE_1"),
                read.findElement("HEAT").orElseThrow().getActualEquipmentIds());
    }

    // ========== The chart ==========

    @Test
    void theChartOfARecipeComesBackTheWayItWasDrawn() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        S88RecipeElement heat = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        S88RecipeElement end = new S88RecipeElement("END", S88RecipeElementKind.END);
        master.addRecipeElement(procedure);
        master.addRecipeElement(heat);
        master.addRecipeElement(end);

        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep("BOX_HEAT", "HEAT"));
        chart.addStep(new S88ProcedureStep("BOX_END", "END"));
        chart.addTransition(new S88ProcedureTransition("T_TEMP_OK",
                S88ConditionExpression.of(S88VariableAddress.parse("Reports/STATE"),
                        S88ConditionOperator.EQUALS, "COMPLETE").toText()));
        S88ProcedureLink first = new S88ProcedureLink("L1");
        first.addFrom(S88IdRef.step("BOX_HEAT"));
        first.addTo(S88IdRef.transition("T_TEMP_OK"));
        chart.addLink(first);
        S88ProcedureLink second = new S88ProcedureLink("L2");
        second.addFrom(S88IdRef.transition("T_TEMP_OK"));
        second.addTo(S88IdRef.step("BOX_END"));
        second.setLinkType(S88LinkType.CONTROL_LINK);
        second.setEvaluationOrder(2);
        chart.addLink(second);
        procedure.setProcedureLogic(chart);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        S88ProcedureLogic readChart = read.findElement("PROC").orElseThrow().getProcedureLogic();
        assertNotNull(readChart, "the chart hangs off the step it was drawn under");
        assertEquals(2, readChart.getSteps().size());
        assertEquals(2, readChart.getLinks().size());
        assertEquals(1, readChart.getTransitions().size());
        assertEquals("HEAT", readChart.findStep("BOX_HEAT").orElseThrow().getRecipeElementId());

        S88ProcedureTransition readBar = readChart.findTransition("T_TEMP_OK").orElseThrow();
        assertTrue(readBar.isGuarded());
        assertEquals("Reports/STATE#=#COMPLETE", readBar.getCondition(),
                "the comparison a bar waits on travels exactly as it was written, because a bar is"
                        + " the only place in the file that says what has to be true");
        S88ConditionExpression expression = readBar.expression();
        assertNotNull(expression, "and it reads back as a comparison rather than as a bare name");
        assertEquals("COMPLETE", expression.getLiteral());
        assertSame(S88ConditionOperator.EQUALS, expression.getOperator());

        S88ProcedureLink readLink = readChart.findLink("L1").orElseThrow();
        assertEquals(S88IdRefType.STEP, readLink.getFrom().get(0).getType());
        assertEquals(S88IdRefType.TRANSITION, readLink.getTo().get(0).getType(),
                "which side of the bar a line points at is said on each end, so a chart that is"
                        + " box to bar and bar to box comes back knowing which is which");
        assertEquals(2, readChart.findLink("L2").orElseThrow().getEvaluationOrder());
    }

    // ========== Values and nesting ==========

    @Test
    void theValuesOfAStepComeBackWithTheirTypeAndUnit() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement heat = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        heat.setEquipmentClassId("HEATER");
        heat.addParameter(S88RecipeParameter.of("Reports/STATE", "IDLE", DataType.ENUMERATION, null));
        heat.addParameter(S88RecipeParameter.of("Parameters/TARGET_TEMPERATURE", "75.5", DataType.REAL, "degC"));
        master.addRecipeElement(heat);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88RecipeElement read = repository(storage).loadRecipe().findElement("HEAT").orElseThrow();

        assertEquals(2, read.getParameters().size());
        assertEquals("IDLE", read.findParameter("Reports/STATE").orElseThrow()
                .getFirstValue().getFirstValueString());
        assertEquals(DataType.ENUMERATION, read.findParameter("Reports/STATE").orElseThrow()
                .getFirstValue().getDataType());
        assertEquals("75.5", read.findParameter("Parameters/TARGET_TEMPERATURE").orElseThrow()
                .getFirstValue().getFirstValueString());
        assertEquals("degC", read.findParameter("Parameters/TARGET_TEMPERATURE").orElseThrow()
                .getFirstValue().getUnitOfMeasure());
    }

    @Test
    void theFormulaOfARecipeComesBackWithItsNames() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeParameter volume = S88RecipeParameter.of("F_VOLUMEN_TOTAL", "1000", DataType.REAL, "L");
        volume.setDescription("Volumen total dosificado");
        S88RecipeParameter factor = S88RecipeParameter.of("F_FACTOR", "1.05", DataType.REAL, null);
        volume.addParameter(factor);
        master.addFormulaParameter(volume);
        S88RecipeElement heat = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        heat.setEquipmentClassId("HEATER");
        master.addRecipeElement(heat);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertEquals(1, read.getFormula().size());
        S88RecipeParameter readVolume = read.getFormula().get(0);
        assertEquals("F_VOLUMEN_TOTAL", readVolume.getId(),
                "a calculation is a set of named things, and a formula that lost its names would be"
                        + " a list of numbers with nothing to refer to them by");
        assertEquals("Volumen total dosificado", readVolume.getDescription());
        assertEquals("1000", readVolume.getFirstValue().getFirstValueString());
        assertEquals(1, readVolume.getParameters().size());
        assertEquals("F_FACTOR", readVolume.getParameters().get(0).getId(),
                "and one parameter can hold others, which is what lets a formula be built up in"
                        + " parts rather than only written whole");
    }

    @Test
    void stepsNestedInsideOtherStepsComeBackAtTheSameDepth() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        S88RecipeElement operation = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        S88RecipeElement phase = new S88RecipeElement("PHASE_ONE", S88RecipeElementKind.PHASE);
        phase.setEquipmentClassId("HEATER");
        operation.addRecipeElement(phase);
        procedure.addRecipeElement(operation);
        master.addRecipeElement(procedure);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertEquals(3, read.getAllElements().size());
        assertEquals(S88RecipeElementKind.PHASE, read.findElement("PHASE_ONE").orElseThrow().getKind());
        assertEquals("HEATER", read.findElement("PHASE_ONE").orElseThrow().getEquipmentClassId());
    }

    @Test
    void aDateOnARecipeComesBackAsItWasWritten() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        master.setVersion("2");
        master.setVersionDate("2026-10-01T09:00:00Z");
        master.addDescription("Calentamiento del tanque 1");

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertEquals("2", read.getVersion());
        assertEquals("2026-10-01T09:00:00Z", read.getVersionDate());
        assertEquals(List.of("Calentamiento del tanque 1"), read.getDescriptions());
    }

    // ========== The edges ==========

    @Test
    void aRecipeWithNothingInItIsStillWrittenAsAFile() throws Exception {
        S88MasterRecipe empty = new S88MasterRecipe("REC_VACIA", S88RecipeKind.CLASS);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(empty);

assertEquals(BatchMLRecipeRepositoryImpl.ROOT, rootOf(storage),
                "a recipe that has just been created has to be a file on disk before anything can"
                        + " be edited in it, and a file has to have a root");
        assertEquals(1, readable(storage).getBatchInformation().sizeOfMasterRecipeArray(),
                "and a recipe with nothing in it still has to satisfy the format, because the file"
                        + " it is has to open");
        S88MasterRecipe read = repository(storage).loadRecipe();
        assertEquals("REC_VACIA", read.getId());
        assertTrue(read.getRecipeElements().isEmpty());
    }

    @Test
    void nothingToReadIsNoRecipeRatherThanAFailure() {
        assertNull(repository(new InMemory()).loadRecipe(),
                "a file that was never written is not a recipe that is broken, and asking for one"
                        + " should not throw");
    }

    @Test
    void writingNothingIsNotAFailureEither() {
        InMemory storage = new InMemory();

        repository(storage).saveRecipe(null);

        assertEquals(0, storage.bytes().length);
    }

    @Test
    void aFileThatIsNotARecipeReadsAsNoRecipeRatherThanAsABrokenOne() {
        InMemory storage = new InMemory();
        storage.given("<BatchList><BatchListEntry/></BatchList>");

        assertNull(repository(storage).loadRecipe(),
                "XML that opens and is simply not a recipe has nothing to read into, and saying"
                        + " nothing is what lets a caller carry on looking somewhere else");
    }

    @Test
    void aFileHoldingMoreThanOneRecipeSaysSoRatherThanPickingOne() {
        InMemory storage = new InMemory();
        storage.given("""
                <?xml version="1.0" encoding="UTF-8"?>
                <b2m:BatchInformation xmlns:b2m="http://www.mesa.org/xml/B2MML">
                  <b2m:MasterRecipe><b2m:ID>UNA</b2m:ID><b2m:RecipeElement><b2m:ID>STEP</b2m:ID>
                    <b2m:RecipeElementType>Operation</b2m:RecipeElementType></b2m:RecipeElement>
                  </b2m:MasterRecipe>
                  <b2m:MasterRecipe><b2m:ID>DOS</b2m:ID><b2m:RecipeElement><b2m:ID>STEP</b2m:ID>
                    <b2m:RecipeElementType>Operation</b2m:RecipeElementType></b2m:RecipeElement>
                  </b2m:MasterRecipe>
                </b2m:BatchInformation>
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository(storage).loadRecipe());
        assertTrue(failure.getMessage().contains("2"),
                "the format allows several recipes in one file because the format is not about one"
                        + " file per recipe. This one is, so how many there are has to be said"
                        + " rather than resolved by quietly taking the first");
    }

    // ========== Against the recipe format itself ==========

/**
     * The root of the file is read off the parsed document rather than out of the text.
     * <p>
     * The plant file is written with the {@code b2m} prefix on every element, so a test that looked
     * for {@code "<BatchInformation"} would be testing the prefix and not the document.
     */
    @Test
    void theFileIsRootedAtBatchInformationRatherThanAtTheRecipeItself() throws Exception {
        InMemory storage = new InMemory();
        repository(storage).saveRecipe(completeRecipe());

        assertEquals(BatchMLRecipeRepositoryImpl.ROOT, rootOf(storage),
                "the document is BatchInformation, which is to recipes what EquipmentInformation"
                        + " is to a plant");
        assertEquals(0, readable(storage).getBatchInformation().sizeOfControlRecipeArray(),
                "and it holds no control recipe: a control recipe is runtime state, so there is no"
                        + " writing one here to be done");
    }

    private static String rootOf(InMemory storage) throws Exception {
        XmlObject parsed = XmlObject.Factory.parse(new ByteArrayInputStream(storage.bytes()));
        try (org.apache.xmlbeans.XmlCursor cursor = parsed.newCursor()) {
            return cursor.toFirstChild() ? cursor.getName().getLocalPart() : null;
        }
    }

    @Test
    void aFileThisWritesCanBeReadBySomethingThatOnlyKnowsTheFormat() throws Exception {
        InMemory storage = new InMemory();
        repository(storage).saveRecipe(completeRecipe());

        BatchInformationType information = readable(storage).getBatchInformation();
        assertEquals(1, information.sizeOfMasterRecipeArray(),
                "what is written has to be a recipe file and not something that only this"
                        + " repository can read");
        MasterRecipeType xml = information.getMasterRecipeArray(0);
        assertEquals("REC_COMPLETA", xml.getID().getStringValue());
        assertEquals(2, xml.sizeOfRecipeElementArray());
        assertTrue(xml.isSetFormula(), "the formula is there to be read back");
        assertTrue(xml.getRecipeElementArray(0).isSetProcedureLogic(),
                "and the chart hangs off the step it was drawn under");
        assertEquals(1, xml.getRecipeElementArray(0).getProcedureLogic().sizeOfLinkArray());
        assertEquals(1, xml.getRecipeElementArray(0).getProcedureLogic().sizeOfStepArray());
        assertEquals(1, xml.getRecipeElementArray(0).getProcedureLogic().sizeOfTransitionArray());
        assertEquals(1, xml.sizeOfOtherInformationArray(),
                "the only free-form entry a recipe carries is the one saying how it addresses its"
                        + " equipment, which the format has no field of its own for");
        assertEquals(S88OtherInformation.RECIPE_KIND, xml.getOtherInformationArray(0).getID().getStringValue());
    }

    @Test
    void aBoxThatSaysNoVersionNamesTheFirstVersionOfTheStepItPointsAt() throws Exception {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        master.addRecipeElement(procedure);
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep("BOX", "HEAT"));
        procedure.setProcedureLogic(chart);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);

        StepType xml = readable(storage).getBatchInformation().getMasterRecipeArray(0)
                .getRecipeElementArray(0).getProcedureLogic().getStepArray(0);
        assertEquals("BOX", xml.getID().getStringValue());
        assertEquals("HEAT", xml.getRecipeElementID().getStringValue(),
                "a box has to name the step it points at, and the format makes it do so in full"
                        + " rather than leaving it implied");
        assertEquals("1", xml.getRecipeElementVersion().getStringValue(),
                "and the version with it. One is the first version of anything, so a recipe drawn"
                        + " before its steps have been given versions names the one they are at");
        assertEquals("1", repository(storage).loadRecipe().findElement("PROC").orElseThrow()
                .getProcedureLogic().findStep("BOX").orElseThrow().getRecipeElementVersion(),
                "and it comes back, so a box read out of the file and a box written into it agree");
    }

    @Test
    void aBoxThatNamesItsOwnVersionKeepsIt() throws Exception {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        master.addRecipeElement(procedure);
        S88ProcedureLogic chart = new S88ProcedureLogic();
        S88ProcedureStep box = new S88ProcedureStep("BOX", "HEAT");
        box.setRecipeElementVersion("7");
        chart.addStep(box);
        procedure.setProcedureLogic(chart);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);

        assertEquals("7", readable(storage).getBatchInformation().getMasterRecipeArray(0)
                .getRecipeElementArray(0).getProcedureLogic().getStepArray(0)
                .getRecipeElementVersion().getStringValue(),
                "the default is only for a box that says nothing, and one that does say is obeyed");
    }

    @Test
    void aStepThatDoesNotSayWhatItIsIsStillAFileThatOpens() throws Exception {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        master.addRecipeElement(new S88RecipeElement("SIN_TIPO", null));

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);

        RecipeElementType xml = readable(storage).getBatchInformation()
                .getMasterRecipeArray(0).getRecipeElementArray(0);
        assertEquals("Other", xml.getRecipeElementType().getStringValue(),
                "what a step is, is mandatory in the format, so a step being written and not yet"
                        + " finished says the last thing the vocabulary names rather than saying"
                        + " nothing");
        assertEquals(S88RecipeElementKind.OTHER,
                repository(storage).loadRecipe().findElement("SIN_TIPO").orElseThrow().getKind(),
                "and the step that does not say comes back as whatever the vocabulary names last,"
                        + " which is what the conformance rules are for pointing at");
    }

    // ========== Where the chart is drawn ==========

    @Test
    void theDrawingOfTheChartOfTheRecipeItselfSurvivesTheFile() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep("BOX_A", "HEAT"));
        chart.addStep(new S88ProcedureStep("BOX_B", "COOL"));
        chart.addTransition(new S88ProcedureTransition("T1", null));
        master.setProcedureLogic(chart);

        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX_A", 120, 80);
        layout.place("BOX_B", 360, 80);
        layout.place("T1", 240, 80);
        master.setLayout(layout);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertEquals(layout, read.getLayout(),
                "a box that is dragged and saved has to come back where it was left, or the drawing"
                        + " is lost every time the recipe is opened");
    }

    @Test
    void theDrawingOfTheChartUnderAStepSurvivesTheFile() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        master.addRecipeElement(procedure);
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep("BOX", "HEAT"));
        procedure.setProcedureLogic(chart);

        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 60.5, 40.25);
        procedure.setLayout(layout);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertEquals(layout, read.findElement("PROC").orElseThrow().getLayout());
    }

    @Test
    void theDrawingAndTheKindOfTheRecipeAreBothCarried() throws Exception {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.INSTANCE);
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep("BOX", "HEAT"));
        master.setProcedureLogic(chart);
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 10, 20);
        master.setLayout(layout);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);

        assertEquals(2, readable(storage).getBatchInformation().getMasterRecipeArray(0)
                .sizeOfOtherInformationArray());
        S88MasterRecipe read = repository(storage).loadRecipe();
        assertSame(S88RecipeKind.INSTANCE, read.getKind());
        assertEquals(layout, read.getLayout());
    }

    @Test
    void theDrawingOfOneChartSaysNothingAboutTheDrawingOfAnother() {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        master.addRecipeElement(procedure);

        S88ChartLayout onTheStep = new S88ChartLayout();
        onTheStep.place("BOX", 10, 20);
        procedure.setLayout(onTheStep);

        S88ChartLayout onTheRecipe = new S88ChartLayout();
        onTheRecipe.place("BOX", 900, 900);
        master.setLayout(onTheRecipe);

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);
        S88MasterRecipe read = repository(storage).loadRecipe();

        assertEquals(onTheRecipe, read.getLayout());
        assertEquals(onTheStep, read.findElement("PROC").orElseThrow().getLayout(),
                "two charts, two drawings, and a box called BOX in each of them");
    }

    @Test
    void aChartWithNothingDrawnOnItCarriesNoDrawing() throws Exception {
        S88MasterRecipe master = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        master.setProcedureLogic(new S88ProcedureLogic());

        InMemory storage = new InMemory();
        repository(storage).saveRecipe(master);

        assertTrue(readable(storage).getBatchInformation().getMasterRecipeArray(0)
                .isSetProcedureLogic(), "the chart is written");
        assertTrue(repository(storage).loadRecipe().getLayout().isEmpty(),
                "and it carries nothing about being drawn, because it was not");
    }

    // ========== Fixtures ==========

    private static BatchMLRecipeRepositoryImpl repository(S88Storage storage) {
        return new BatchMLRecipeRepositoryImpl(storage);
    }

/**
 * Asserts the file against the recipe format itself.
     * <p>
     * Reading it back through the factory of the format, and not through this repository, so that
     * what is checked is the file and not the code that wrote it. The factory parse is followed by
     * a validation against the compiled schema, which is what catches a mandatory element left out
     * and a value outside an enumeration.
     * <p>
     * The validation has to be done on the typed parse. Parsing generically first and validating
     * that gives {@code "Invalid type"} for every document, because a generic parse does not bind
     * the element at the root to its declaration and so leaves the validator with nothing to type
     * it by. That error is about the parse and not about the file, and reading it as a defect in
     * the file is how a real missing element nearly went unnoticed.
     */
    private static BatchInformationDocument readable(InMemory storage) {
        BatchInformationDocument document;
        try {
            document = BatchInformationDocument.Factory.parse(
                    new ByteArrayInputStream(storage.bytes()));
        } catch (Exception e) {
            throw new AssertionError("what is written does not read back as a recipe file: " + e, e);
        }
        assertNotNull(document.getBatchInformation(),
                "what is written has to be readable as a recipe file and not as something only"
                        + " this repository can read");

        List<XmlError> errors = new java.util.ArrayList<>();
        if (!document.validate(new XmlOptions().setErrorListener(errors))) {
            StringBuilder message = new StringBuilder("what is written has to satisfy the recipe"
                    + " format, and the schema objected:");
            for (XmlError error : errors) {
                message.append("\n  ").append(error.getErrorCode()).append(' ')
                        .append(error.getMessage());
            }
            throw new AssertionError(message.toString());
        }
        return document;
    }

    /** A recipe with something of everything in it, for the tests that check it as a whole. */
    private static S88MasterRecipe completeRecipe() {
        S88MasterRecipe master = new S88MasterRecipe("REC_COMPLETA", S88RecipeKind.CLASS);
        master.setVersion("3");
        master.setVersionDate("2026-10-01T09:00:00Z");
        master.addDescription("Receta completa para la prueba de ida y vuelta");

        S88RecipeElement procedure = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        master.addRecipeElement(procedure);
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep("BOX_HEAT", "HEAT"));
        chart.addTransition(new S88ProcedureTransition("T_OK",
                S88ConditionExpression.of(S88VariableAddress.parse("Reports/STATE"),
                        S88ConditionOperator.EQUALS, "COMPLETE").toText()));
        S88ProcedureLink link = new S88ProcedureLink("L1");
        link.addFrom(S88IdRef.step("BOX_HEAT"));
        link.addTo(S88IdRef.transition("T_OK"));
        link.setLinkType(S88LinkType.CONTROL_LINK);
        chart.addLink(link);
        procedure.setProcedureLogic(chart);

        S88RecipeElement heat = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        heat.setEquipmentClassId("HEATER");
        heat.addParameter(S88RecipeParameter.of("Reports/STATE", "IDLE", DataType.ENUMERATION, null));
        heat.addOtherInformation(S88OtherInformation.of("NOTE", "something to remember"));
        master.addRecipeElement(heat);

        S88RecipeParameter formula = S88RecipeParameter.of("F_TOTAL", "42", DataType.INTEGER, null);
        master.addFormulaParameter(formula);
        return master;
    }

    /**
     * Keeps the bytes of a recipe in memory so that the same recipe can be written and then read
     * without going near a disk, which is what a recipe being edited does while it is not well
     * formed.
     */
    private static final class InMemory implements S88Storage {
        private byte[] bytes = new byte[0];

        @Override
        public InputStream openInput() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public OutputStream openOutput() throws IOException {
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            return new OutputStream() {
                @Override
                public void write(int b) {
                    sink.write(b);
                }

                @Override
                public void close() {
                    bytes = sink.toByteArray();
                }
            };
        }

/** Puts bytes in the storage as though something had written them, for files written elsewhere. */
        void given(String text) {
            bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        byte[] bytes() {
            return bytes.clone();
        }
}
}


