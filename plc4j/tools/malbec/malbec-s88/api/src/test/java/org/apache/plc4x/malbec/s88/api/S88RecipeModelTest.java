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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the recipe model has to hold, and what it has to refuse to decide.
 * <p>
 * The point of most of these is a limit rather than a feature: that the chart carries no notion of
 * what runs next, that a recipe is readable without a plant, and that a file which is broken
 * arrives open with its problems named.
 */
class S88RecipeModelTest {

    // ========== Enumerations ==========

    @Test
    void everyEnumeratedValueIsReadBackFromTheLabelTheRecipeCarries() {
        for (S88RecipeElementKind kind : S88RecipeElementKind.values()) {
            assertSame(kind, S88RecipeElementKind.fromString(kind.getXmlName()),
                    "element kind " + kind + " is not read back from its own label");
        }
        for (S88LinkType type : S88LinkType.values()) {
            assertSame(type, S88LinkType.fromString(type.getXmlName()),
                    "link type " + type + " is not read back from its own label");
        }
        for (S88Depiction depiction : S88Depiction.values()) {
            assertSame(depiction, S88Depiction.fromString(depiction.getXmlName()));
        }
        for (S88IdRefType type : S88IdRefType.values()) {
            assertSame(type, S88IdRefType.fromString(type.getXmlName()));
        }
        for (S88IdScope scope : S88IdScope.values()) {
            assertSame(scope, S88IdScope.fromString(scope.getXmlName()));
        }
        for (S88DataInterpretation interpretation : S88DataInterpretation.values()) {
            assertSame(interpretation, S88DataInterpretation.fromString(interpretation.getXmlName()));
        }
        for (S88RecipeParameterType type : S88RecipeParameterType.values()) {
            assertSame(type, S88RecipeParameterType.fromString(type.getXmlName()));
        }
        for (S88RecipeKind kind : S88RecipeKind.values()) {
            assertSame(kind, S88RecipeKind.fromString(kind.name()));
        }
    }

    @Test
    void aLabelNoVocabularyNamesIsReadAsNothingRatherThanGuessed() {
        assertNull(S88RecipeElementKind.fromString("Wingdings"));
        assertNull(S88LinkType.fromString("maybe"));
        assertNull(S88IdRefType.fromString(""));
        assertNull(S88DataInterpretation.fromString(null));
    }

    @Test
    void theHandWrittenFormOfAKindIsUnderstoodToo() {
        assertSame(S88RecipeElementKind.UNIT_PROCEDURE, S88RecipeElementKind.fromString("UNIT_PROCEDURE"),
                "a value typed by hand as the name of the constant is as readable as the label");
        assertSame(S88RecipeElementKind.UNIT_PROCEDURE, S88RecipeElementKind.fromString("UnitProcedure"));
    }

    @Test
    void startAndEndAndTheTwoBranchesArePartOfTheVocabulary() {
        assertTrue(S88RecipeElementKind.BEGIN.isTerminal());
        assertTrue(S88RecipeElementKind.END.isTerminal());
        assertFalse(S88RecipeElementKind.PHASE.isTerminal());

        assertTrue(S88LinkType.PARALLEL_DIVERGENT.isDivergent());
        assertTrue(S88LinkType.PARALLEL_DIVERGENT.isParallel());
        assertTrue(S88LinkType.SERIAL_DIVERGENT.isDivergent());
        assertFalse(S88LinkType.SERIAL_DIVERGENT.isParallel());
        assertTrue(S88LinkType.PARALLEL_CONVERGENT.isConvergent());
    }

    // ========== Variable address ==========

    @Test
    void aClassAddressIsTakenApartIntoContainerAndName() {
        S88VariableAddress address = S88VariableAddress.parse("Reports/STATE");

        assertNotNull(address);
        assertEquals(S88PlantModel.REPORTS, address.getContainer());
        assertEquals("STATE", address.getName());
        assertEquals("Reports/STATE", address.toText());
    }

    @Test
    void anInstanceAddressIsOneNameAndCarriesNoContainerOfItsOwn() {
        S88VariableAddress address = S88VariableAddress.parse("STATE_CALENTAMIENTO_TANQUE_1");

        assertNotNull(address);
        assertFalse(address.hasContainer(),
                "the name a module published is already unique in the plant, so naming the container"
                        + " again would be saying the same thing twice");
        assertEquals("STATE_CALENTAMIENTO_TANQUE_1", address.getName());
    }

    @Test
    void anAddressSurvivesBeingWrittenAndReadAgain() {
        for (String text : List.of("Reports/STATE", "Parameters/TARGET_TEMPERATURE", "PLAIN_NAME")) {
            assertEquals(text, S88VariableAddress.parse(text).toText(),
                    "an address that does not read back the way it was written cannot be stored and"
                            + " recovered without quietly changing what a recipe points at");
        }
    }

    @Test
    void textThatNamesNoVariableIsNoAddress() {
        assertNull(S88VariableAddress.parse(null));
        assertNull(S88VariableAddress.parse("   "));
        assertNull(S88VariableAddress.parse("Reports/"),
                "a container with no variable in it is not an address to anything");
    }

    @Test
    void onlyTheFirstSeparatorSplits() {
        S88VariableAddress address = S88VariableAddress.parse("Reports/A/B");

        assertEquals("Reports", address.getContainer());
        assertEquals("A/B", address.getName(),
                "a name is allowed to contain a slash; it is the first one that separates the"
                        + " container from the name");
    }

    // ========== Addressing, the point of the two kinds of recipe ==========

    @Test
    void theSameParameterMeansTwoDifferentThingsInTheTwoKindsOfRecipe() {
        S88RecipeParameter parameter = S88RecipeParameter.of(
                "Reports/STATE", "IDLE", DataType.ENUMERATION, null);

        S88MasterRecipe byClass = new S88MasterRecipe("R1", S88RecipeKind.CLASS);
        S88MasterRecipe byInstance = new S88MasterRecipe("R2", S88RecipeKind.INSTANCE);

        S88VariableAddress classAddress = byClass.addressOf(parameter);
        S88VariableAddress instanceAddress = byInstance.addressOf(parameter);

        assertEquals(S88PlantModel.REPORTS, classAddress.getContainer());
        assertEquals("STATE", classAddress.getName());
        assertNull(instanceAddress.getContainer(),
                "read as if written for particular equipment, the same text is one whole name, not a"
                        + " container and a name inside it");
        assertEquals("Reports/STATE", instanceAddress.getName());
    }

    @Test
    void aParameterWithNoIdHasNoAddress() {
        assertNull(new S88MasterRecipe("R", S88RecipeKind.CLASS).addressOf(new S88RecipeParameter()));
        assertNull(new S88MasterRecipe("R", S88RecipeKind.CLASS).addressOf(null));
    }

    @Test
    void theKindIsWrittenIntoTheRecipeBecauseTheFormatHasNoFieldForIt() {
        S88MasterRecipe recipe = new S88MasterRecipe("R1", S88RecipeKind.INSTANCE);

        assertEquals(1, recipe.getOtherInformation().size());
        S88OtherInformation stored = S88OtherInformation.find(
                recipe.getOtherInformation(), S88OtherInformation.RECIPE_KIND);
        assertNotNull(stored, "the recipe format has no field of its own for this, so the entry is"
                + " the only place the answer survives being saved");
        assertEquals("INSTANCE", stored.getFirstValue());
        assertSame(S88RecipeKind.INSTANCE, S88MasterRecipe.readStoredKind(recipe));
    }

    @Test
    void theKindIsWrittenDownOnceAndReplacedRatherThanAddedTo() {
        S88MasterRecipe recipe = new S88MasterRecipe("R1", S88RecipeKind.CLASS);
        recipe.setKind(S88RecipeKind.INSTANCE);

        assertEquals(1, recipe.getOtherInformation().size());
        assertSame(S88RecipeKind.INSTANCE, S88MasterRecipe.readStoredKind(recipe));
    }

    @Test
    void aRecipeThatSaysHowItAddressesNothingIsReadAsOneThatHasToBeBoundFirst() {
        // The no-argument constructor never states a kind, which is the state a file that does not
        // mention one arrives in.
        S88MasterRecipe silent = new S88MasterRecipe();

        assertTrue(silent.getOtherInformation().isEmpty());
        assertSame(S88RecipeKind.CLASS, S88MasterRecipe.readStoredKind(silent),
                "a file that does not say is read the way that needs the most from the plant, so the"
                        + " reader is told it has to be bound rather than handed a guess that happens"
                        + " to work for a recipe written for one particular tank");
        assertNull(S88MasterRecipe.readStoredKind(null));
    }

    @Test
    void aControlRecipeIsForParticularEquipmentByDefault() {
        assertSame(S88RecipeKind.INSTANCE, new S88ControlRecipe().getKind());
        assertSame(S88RecipeKind.INSTANCE, new S88ControlRecipe("C1", "LOTE_2026_014").getKind());
    }

    @Test
    void aControlRecipeRemembersTheMasterItWasSetFrom() {
        S88ControlRecipe control = new S88ControlRecipe("C1", "LOTE_2026_014");

        assertNull(control.getSourceRecipeId());
        control.setSourceRecipeId("REC_MAESTRA");
        assertEquals("REC_MAESTRA", control.getSourceRecipeId());

        control.setSourceRecipeId("REC_MAESTRA_V2");
        assertEquals("REC_MAESTRA_V2", control.getSourceRecipeId());
        long sourceEntries = control.getOtherInformation().stream()
                .filter(info -> S88ControlRecipe.SOURCE_RECIPE_ID.equalsIgnoreCase(info.getId()))
                .count();
        assertEquals(1, sourceEntries,
                "naming the master again replaces the old name rather than leaving both to be read"
                        + " and one of them guessed at");
    }

    // ========== The chart ==========

    @Test
    void theChartIsReachableById() {
        S88ProcedureLogic logic = chart();
        logic.addStep(new S88ProcedureStep("HEAT", "RE_HEAT"));
        logic.addTransition(new S88ProcedureTransition("T1", "TEMP_OK"));
        logic.addLink(S88ProcedureLink.betweenSteps("L1", "START", "HEAT"));

        assertEquals("RE_HEAT", logic.findStep("HEAT").orElseThrow().getRecipeElementId());
        assertEquals("TEMP_OK", logic.findTransition("T1").orElseThrow().getCondition());
        assertEquals(1, logic.findLink("L1").orElseThrow().getFrom().size());
        assertTrue(logic.findStep("NOPE").isEmpty());
    }

    @Test
    void aLineReportsWhatTheRecipeSaysItMeansRatherThanWhatItsShapeLooksLike() {
        S88ProcedureLogic logic = new S88ProcedureLogic();
        // Marked as splitting, but written with a single destination. The recipe is wrong, and the
        // honest thing is to say so rather than to correct it into something that looks sensible.
        S88ProcedureLink split = S88ProcedureLink.betweenSteps("L1", "START", "HEAT");
        split.setLinkType(S88LinkType.PARALLEL_DIVERGENT);
        logic.addLink(split);

        assertEquals(1, logic.getDivergentLinks().size());
        assertTrue(logic.getDivergentLinks().get(0).isDivergent());
        assertEquals(1, logic.getDivergentLinks().get(0).getTo().size(),
                "and the mistake stays visible, because quietly repairing it would hide the fact"
                        + " that a branch of the process is missing");
        assertTrue(logic.getConvergentLinks().isEmpty());
    }

    @Test
    void aLineCanPointAtABarAndNotOnlyAtABox() {
        S88ProcedureLink link = new S88ProcedureLink("L1");
        link.addFrom(S88IdRef.step("HEAT"));
        link.addTo(S88IdRef.transition("T1"));

        assertTrue(link.touches(S88IdRef.transition("T1")));
        assertFalse(link.touches(S88IdRef.step("T1")),
                "a bar and a box may share a name, and confusing the two would join the flow to the"
                        + " wrong thing");
    }

    @Test
    void aLinePointingAtSomethingTheChartDoesNotCarryIsReportedRatherThanRejected() {
        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("HEAT", "RE_HEAT"));
        logic.addLink(S88ProcedureLink.betweenSteps("L1", "START", "HEAT"));

        assertEquals(List.of("START"), logic.getDanglingReferences());
        assertFalse(logic.isDanglingFree());

        logic.addStep(new S88ProcedureStep("START", "RE_START"));
        assertTrue(logic.isDanglingFree(), "and the report clears once the missing step is added");
    }

    @Test
    void aReferenceToSomethingOutsideTheChartIsNotCalledDangling() {
        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("HEAT", "RE_HEAT"));
        S88ProcedureLink link = new S88ProcedureLink("L1");
        link.addFrom(new S88IdRef("STEP_IN_OTHER_PROCEDURE", S88IdRefType.STEP, S88IdScope.EXTERNAL));
        link.addTo(S88IdRef.step("HEAT"));
        logic.addLink(link);

        assertTrue(logic.isDanglingFree(),
                "a name the recipe points outside of itself is not a name this chart forgot");
    }

    @Test
    void twoBoxesWithTheSameNameAreRefused() {
        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("HEAT", "RE_HEAT"));

        assertThrowsIllegalState(() -> logic.addStep(new S88ProcedureStep("HEAT", "RE_OTHER")),
                "a chart that indexed both would let a line point at either and choose for the"
                        + " reader");
    }

    // ========== Steps ==========

    @Test
    void aStepIsReachableFromTheTopOfTheRecipe() {
        S88MasterRecipe recipe = new S88MasterRecipe("R1", S88RecipeKind.CLASS);
        S88RecipeElement procedure = new S88RecipeElement("RE_PROC", S88RecipeElementKind.PROCEDURE);
        S88RecipeElement operation = new S88RecipeElement("RE_OP", S88RecipeElementKind.OPERATION);
        S88RecipeElement phase = new S88RecipeElement("RE_PHASE", S88RecipeElementKind.PHASE);
        procedure.addRecipeElement(operation);
        operation.addRecipeElement(phase);
        recipe.addRecipeElement(procedure);

        assertSame(phase, recipe.findElement("RE_PHASE").orElseThrow(),
                "a deep step is reachable without walking the tree, which is what the chart needs"
                        + " to resolve what a step points at");
        assertSame(phase, procedure.findDescendant("RE_PHASE").orElseThrow());
        assertEquals(3, recipe.getAllElements().size());
    }

    @Test
    void twoStepsWithTheSameNameAreRecordedRatherThanRejected() {
        S88MasterRecipe recipe = new S88MasterRecipe("R1", S88RecipeKind.CLASS);
        S88RecipeElement first = new S88RecipeElement("RE_OP", S88RecipeElementKind.OPERATION);
        S88RecipeElement second = new S88RecipeElement("RE_OP", S88RecipeElementKind.OPERATION);
        recipe.addRecipeElement(first);
        recipe.addRecipeElement(second);

        assertEquals(java.util.Set.of("RE_OP"), recipe.getDuplicateIds());
        assertSame(first, recipe.findElement("RE_OP").orElseThrow(),
                "and the first one stays the one that is reached, so what a reader sees does not"
                        + " change when the second is added");
    }

    @Test
    void aClassRecipeKeepsTheClassItAppliesToAndReadsItBack() {
        S88RecipeElement element = new S88RecipeElement("RE_OP", S88RecipeElementKind.OPERATION);
        element.setEquipmentClassId("HEATER");

        assertEquals("HEATER", element.getEquipmentClassId());
        assertEquals(1, element.getOtherInformation().size());

        element.setEquipmentClassId("PUMP");
        assertEquals("PUMP", element.getEquipmentClassId());
        assertEquals(1, element.getOtherInformation().size(),
                "naming a different class replaces the entry rather than adding a second one for a"
                        + " reader to choose between");
    }

    @Test
    void aParameterIsFoundAmongTheOnesNestedInsideOthers() {
        S88RecipeElement element = new S88RecipeElement("RE_OP", S88RecipeElementKind.OPERATION);
        S88RecipeParameter outer = S88RecipeParameter.of("OUTER", "1", DataType.INTEGER, null);
        S88RecipeParameter inner = S88RecipeParameter.of("INNER", "2", DataType.INTEGER, null);
        outer.addParameter(inner);
        element.addParameter(outer);

        assertSame(inner, element.findParameter("INNER").orElseThrow());
        assertSame(outer, element.findParameter("OUTER").orElseThrow());
        assertTrue(element.findParameter("ABSENT").isEmpty());
    }

    @Test
    void aParameterValueIsReadAsWrittenAndWithoutGuessing() {
        S88ParameterValue value = new S88ParameterValue();
        value.addValueString("75.5");
        value.setDataType(DataType.REAL);
        value.setUnitOfMeasure("degC");

        assertEquals(75.5, value.asDouble());
        assertNull(value.asInteger(), "seventy five and a half is not a whole number, and reading it"
                + " as one would silently drop what is after the point");

        S88ParameterValue words = new S88ParameterValue();
        words.addValueString("abc");
        assertNull(words.asDouble(), "text that is not a number is not read as one");
        assertNull(words.asInteger());
    }

    @Test
    void aWholeNumberIsReadableAsEitherKind() {
        S88ParameterValue value = new S88ParameterValue();
        value.addValueString("75");

        assertEquals(75, value.asInteger());
        assertEquals(75.0, value.asDouble(),
                "the text is a whole number whichever way it is asked for, and the parsers read the"
                        + " text rather than consulting the type it was declared with");
    }

    @Test
    void aValueThatNamesAnEnumerationSaysSoEitherWay() {
        S88ParameterValue byType = new S88ParameterValue();
        byType.setDataType(DataType.ENUMERATION);
        assertTrue(byType.isEnumerated());

        S88ParameterValue bySet = new S88ParameterValue();
        bySet.addEnumerationSetId("STATE");
        assertTrue(bySet.isEnumerated(), "a recipe from another tool may carry only one of the two,"
                + " and the check has to see it either way");
    }

    private static S88ProcedureLogic chart() {
        return new S88ProcedureLogic();
    }

    private static void assertThrowsIllegalState(Runnable action, String because) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(because);
    }
}
