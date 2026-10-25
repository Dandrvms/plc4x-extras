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

import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.junit.jupiter.api.Test;

import static org.apache.plc4x.malbec.s88.api.S88RecipeElementKind.OPERATION;
import static org.apache.plc4x.malbec.s88.api.S88RecipeElementKind.PHASE;
import static org.apache.plc4x.malbec.s88.api.S88RecipeElementKind.PROCEDURE;
import static org.apache.plc4x.malbec.s88.api.S88RecipeElementKind.UNIT_PROCEDURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which step goes in which chart. */
class RecipeLevelsTest {

    @Test
    void aUnitProcedureHoldsPhasesAndTheProcedureHoldsUnitProcedures() {
        assertEquals(UNIT_PROCEDURE, RecipeLevels.holds(PROCEDURE));
        assertEquals(UNIT_PROCEDURE, RecipeLevels.holds(null));
        assertEquals(PHASE, RecipeLevels.holds(UNIT_PROCEDURE));
        assertNull(RecipeLevels.holds(PHASE));
    }

    @Test
    void anOperationOfTheOlderKindStillHoldsPhases() {
        assertEquals(PHASE, RecipeLevels.holds(OPERATION));
    }

    @Test
    void aPhaseIsTheLastLevelAndHasNoChart() {
        assertTrue(RecipeLevels.allowsSubChart(PROCEDURE));
        assertTrue(RecipeLevels.allowsSubChart(UNIT_PROCEDURE));
        assertTrue(RecipeLevels.allowsSubChart(OPERATION));
        assertFalse(RecipeLevels.allowsSubChart(PHASE));
        assertFalse(RecipeLevels.allowsSubChart(null));
    }

    @Test
    void aPhaseBelongsToTheChartOfAUnitProcedure() {
        S88RecipeElement unit = new S88RecipeElement("UNIT", UNIT_PROCEDURE);
        S88RecipeElement phase = new S88RecipeElement("PHASE", PHASE);

        assertTrue(RecipeLevels.belongsIn(null, UNIT_PROCEDURE));
        assertFalse(RecipeLevels.belongsIn(null, OPERATION));
        assertTrue(RecipeLevels.belongsIn(unit, PHASE));
        assertFalse(RecipeLevels.belongsIn(unit, OPERATION));
        assertFalse(RecipeLevels.belongsIn(phase, PHASE));
    }

    @Test
    void thePlantLevelFollowsTheStepKind() {
        assertEquals(S88Level.UNIT, RecipeLevels.boundTo(UNIT_PROCEDURE));
        assertEquals(S88Level.EQUIPMENTMODULE, RecipeLevels.boundTo(PHASE));
        assertNull(RecipeLevels.boundTo(PROCEDURE));
    }
}
