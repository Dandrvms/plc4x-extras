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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a chart is drawn, as a thing on its own.
 * <p>
 * The layout is presentation, so the only thing that can go wrong with it is losing it or reading it
 * back as something else. That is what these check: that numbers survive a trip through text, and
 * that text which is not a position is left out rather than allowed to take the recipe down with it.
 */
class S88ChartLayoutTest {

    @Test
    void aLayoutWithNothingInItIsEmpty() {
        assertTrue(new S88ChartLayout().isEmpty());
    }

    @Test
    void aThingThatHasBeenPlacedIsWhereItWasPut() {
        S88ChartLayout layout = new S88ChartLayout();

        layout.place("BOX", 120.5, 80);

        assertEquals(new S88Position(120.5, 80), layout.positionOf("BOX").orElseThrow());
        assertEquals(1, layout.size());
    }

    @Test
    void aThingThatHasNotBeenPlacedIsNowhereRatherThanSomewhereWrong() {
        assertTrue(new S88ChartLayout().positionOf("BOX").isEmpty());
    }

    @Test
    void placingTheSameThingTwiceMovesIt() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 10, 10);

        layout.place("BOX", 20, 30);

        assertEquals(new S88Position(20, 30), layout.positionOf("BOX").orElseThrow(),
                "which is what dragging a box across a chart does");
        assertEquals(1, layout.size());
    }

    @Test
    void aWholeNumberSurvivesWithoutGrowingADecimalPart() {
        S88ChartLayout layout = new S88ChartLayout();

        layout.place("BOX", 120, 80);

        assertEquals(List.of("BOX|120|80"), layout.toLines());
    }

    @Test
    void aFractionalNumberSurvives() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 120.5, 80.25);

        S88ChartLayout read = S88ChartLayout.of(layout.toLines());

        assertEquals(new S88Position(120.5, 80.25), read.positionOf("BOX").orElseThrow());
    }

    @Test
    void aNegativeNumberSurvives() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", -40.5, -10);

        assertEquals(new S88Position(-40.5, -10),
                S88ChartLayout.of(layout.toLines()).positionOf("BOX").orElseThrow(),
                "a chart can be scrolled, so a position can be behind the origin");
    }

    @Test
    void aPositionIsWrittenInTheSameWayWhateverTheLanguageOfTheEquipment() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 1200.5, 80);

        assertEquals(List.of("BOX|1200.5|80"), layout.toLines());
    }

    @Test
    void everyThingThatWasPlacedComesBack() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX_A", 10, 20);
        layout.place("T1", 100, 40);
        layout.place("BOX_B", 200, 60);

        assertEquals(layout, S88ChartLayout.of(layout.toLines()),
                "a layout that goes out and comes back is the layout that went out");
        assertEquals(3, S88ChartLayout.of(layout.toLines()).size());
    }

    @Test
    void twoLayoutsAreTheSameWhateverOrderTheyWereDrawnIn() {
        S88ChartLayout one = new S88ChartLayout();
        one.place("BOX_A", 10, 20);
        one.place("BOX_B", 30, 40);
        S88ChartLayout other = new S88ChartLayout();
        other.place("BOX_B", 30, 40);
        other.place("BOX_A", 10, 20);

        assertEquals(one, other,
                "the order is how the chart was drawn and not what it says");
    }

    @Test
    void theOrderThingsWerePlacedInIsKept() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("T1", 1, 1);
        layout.place("BOX_A", 2, 2);

        assertEquals(List.of("T1", "BOX_A"), S88ChartLayout.of(layout.toLines()).placedIds(),
                "so a chart that is drawn again reads the way it was drawn");
    }

    @Test
    void aLineThatIsNotAPositionIsLeftOut() {
        S88ChartLayout read = S88ChartLayout.of(List.of(
                "BOX_A|10|20",
                "this is not a position",
                "BOX_B|30",
                "|40|50",
                ""));

        assertEquals(1, read.size());
        assertEquals(new S88Position(10, 20), read.positionOf("BOX_A").orElseThrow(),
                "one line that does not parse loses where one box is drawn and nothing else");
    }

    @Test
    void aLineFromAnotherToolIsNotAllowedToStopTheRestFromBeingRead() {
        S88ChartLayout read = S88ChartLayout.of(List.of("BOX|ten|twenty", "T1|1|2"));

        assertEquals(new S88Position(1, 2), read.positionOf("T1").orElseThrow());
    }

    @Test
    void nothingToReadIsAnEmptyLayoutRatherThanAFailure() {
        assertTrue(S88ChartLayout.of(null).isEmpty());
        assertTrue(S88ChartLayout.of(List.of()).isEmpty());
    }

    @Test
    void aThingCanBeForgotten() {
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 10, 20);

        layout.forget("BOX");

        assertTrue(layout.isEmpty(), "which is what taking a box off the chart means");
    }

    @Test
    void aNameOrAPositionThatIsMissingIsNotPlacedRatherThanPlacedWrong() {
        S88ChartLayout layout = new S88ChartLayout();

        layout.place(null, 10, 20);
        layout.place("  ", 10, 20);
        layout.place("BOX", (S88Position) null);

        assertTrue(layout.isEmpty());
    }

    @Test
    void twoPositionsAreEqualWhenTheirNumbersAre() {
        assertEquals(new S88Position(1, 2), new S88Position(1, 2));
        assertEquals(new S88Position(1, 2).hashCode(), new S88Position(1, 2).hashCode());
        assertNotEquals(new S88Position(1, 2), new S88Position(2, 1));
    }

    @Test
    void aPositionMovesByHowFarItWasToldToAndNotToSomewhereElse() {
        assertEquals(new S88Position(15, 25), new S88Position(10, 20).moved(5, 5));
    }

    // ========== Where the layout lives ==========

    @Test
    void aLayoutIsCarriedByWhoeverOwnsTheChart() {
        S88MasterRecipe recipe = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 10, 20);

        recipe.setLayout(layout);

        assertEquals(new S88Position(10, 20), recipe.getLayout().positionOf("BOX").orElseThrow(),
                "a recipe carries the layout of its own chart");
    }

    @Test
    void aLayoutIsCarriedByTheStepWhenTheChartIsUnderAStep() {
        S88RecipeElement step = new S88RecipeElement("PROC", S88RecipeElementKind.PROCEDURE);
        step.setEquipmentClassId("AREA");
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 10, 20);

        step.setLayout(layout);

        assertEquals(new S88Position(10, 20), step.getLayout().positionOf("BOX").orElseThrow(),
                "and a step carries the layout of the chart under it");
    }

    @Test
    void theClassOfTheEquipmentAndTheLayoutOfTheChartSitSideBySide() {
        S88RecipeElement step = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        step.setEquipmentClassId("HEATER");
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 10, 20);
        step.setLayout(layout);

        assertEquals("HEATER", step.getEquipmentClassId(),
                "two different things in the same place, and neither of them disturbs the other");
        assertEquals(1, step.getLayout().size());
    }

    @Test
    void aLayoutReplacesTheOneItReplacesAndLeavesOneBehind() {
        S88MasterRecipe recipe = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88ChartLayout first = new S88ChartLayout();
        first.place("BOX_A", 1, 1);
        first.place("BOX_B", 2, 2);
        recipe.setLayout(first);

        S88ChartLayout second = new S88ChartLayout();
        second.place("BOX_A", 9, 9);
        recipe.setLayout(second);

        assertEquals(1, recipe.getLayout().size(),
                "and the one that was there is gone rather than merged into the new one");
        assertEquals(new S88Position(9, 9), recipe.getLayout().positionOf("BOX_A").orElseThrow());
    }

    @Test
    void aLayoutWithNothingInItIsNotCarriedAtAll() {
        S88MasterRecipe recipe = new S88MasterRecipe("REC", S88RecipeKind.CLASS);

        recipe.setLayout(new S88ChartLayout());

        assertTrue(recipe.getLayout().isEmpty());
        assertEquals(1, recipe.getOtherInformation().size(),
                "the only free-form entry a recipe carries is the one saying how it addresses its"
                        + " equipment, and a chart that was never drawn adds nothing to that");
    }

    @Test
    void aRecipeThatWasNeverDrawnOnHasAnEmptyLayoutRatherThanNone() {
        S88MasterRecipe recipe = new S88MasterRecipe("REC", S88RecipeKind.CLASS);

        assertTrue(recipe.getLayout().isEmpty());
        assertEquals(new S88ChartLayout(), recipe.getLayout());
    }

    @Test
    void anEmptyLayoutTakesAStoredOneAway() {
        S88MasterRecipe recipe = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88ChartLayout layout = new S88ChartLayout();
        layout.place("BOX", 1, 1);
        recipe.setLayout(layout);

        recipe.setLayout(null);

        assertTrue(recipe.getLayout().isEmpty());
    }
}
