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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a bar waits on, written as one parseable string and read as a sentence.
 */
class S88TransitionConditionTest {

    private static S88ConditionExpression comparison(String text) {
        return S88ConditionExpression.parse(text);
    }

    @Test
    void oneComparisonIsWrittenTheWayARecipeAlwaysWroteIt() {
        S88TransitionCondition condition =
                S88TransitionCondition.comparison(comparison("Reports/STATE#=#COMPLETE"));

        assertEquals("Reports/STATE#=#COMPLETE", condition.toText(),
                "because an old recipe still has to read, and the shape of one comparison is not"
                        + " changed by there now being more than one");
        assertEquals("STATE = COMPLETE", condition.toDisplay());
    }

    @Test
    void twoComparisonsJoinedAreWrappedAndReadAsASentence() {
        S88TransitionCondition condition = S88TransitionCondition.and(List.of(
                S88TransitionCondition.comparison(comparison("Reports/STATE#=#COMPLETE")),
                S88TransitionCondition.comparison(comparison("Reports/TEMP#>#80"))));

        assertEquals("AND([Reports/STATE#=#COMPLETE];[Reports/TEMP#>#80])", condition.toText());
        assertEquals("STATE = COMPLETE and TEMP > 80", condition.toDisplay());
    }

    @Test
    void aJoiningInsideAJoiningOfAnotherKindKeepsItsBracketsWhenRead() {
        S88TransitionCondition condition = S88TransitionCondition.or(List.of(
                S88TransitionCondition.comparison(comparison("Reports/STATE#=#COMPLETE")),
                S88TransitionCondition.and(List.of(
                        S88TransitionCondition.comparison(comparison("Reports/TEMP#>#80")),
                        S88TransitionCondition.wait(5)))));

        assertEquals("OR([Reports/STATE#=#COMPLETE];AND([Reports/TEMP#>#80];WAIT(5)))",
                condition.toText());
        assertEquals("STATE = COMPLETE or (TEMP > 80 and after 5 s)", condition.toDisplay(),
                "because without the brackets the sentence would say something else");
    }

    @Test
    void aWaitIsWrittenAsSecondsAndReadAsASentence() {
        assertEquals("WAIT(30)", S88TransitionCondition.wait(30).toText());
        assertEquals("after 30 s", S88TransitionCondition.wait(30).toDisplay());
    }

    @Test
    void theTextSurvivesBeingWrittenAndReadAgain() {
        S88TransitionCondition condition = S88TransitionCondition.or(List.of(
                S88TransitionCondition.and(List.of(
                        S88TransitionCondition.comparison(comparison(
                                "CALENTAMIENTO_2#TEMPERATURA_ACTUAL_2#>#80")),
                        S88TransitionCondition.comparison(comparison("Reports/STATE#=#COMPLETE")))),
                S88TransitionCondition.wait(5)));

        assertEquals(condition, S88TransitionCondition.parse(condition.toText()),
                "because a recipe stored in a file is read back and has to mean the same thing");
    }

    @Test
    void aLiteralHoldingASeparatorStaysOneOperand() {
        S88TransitionCondition condition = S88TransitionCondition.and(List.of(
                S88TransitionCondition.comparison(comparison("Reports/STATE#=#A;B")),
                S88TransitionCondition.comparison(comparison("Reports/TEMP#>#80"))));

        assertEquals(condition, S88TransitionCondition.parse(condition.toText()));
    }

    @Test
    void nothingToWaitOnReadsAsNothing() {
        assertNull(S88TransitionCondition.parse(null));
        assertNull(S88TransitionCondition.parse("   "));
        assertNull(S88TransitionCondition.parse("just some words"));
        assertNull(S88TransitionCondition.parse("AND(Reports/STATE#=#COMPLETE)"),
                "because a joining of one is not a joining");
        assertNull(S88TransitionCondition.parse("WAIT(later)"));
    }
}
