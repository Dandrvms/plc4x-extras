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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.util.List;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the panel under the chart says, which is everything the operator has to be told at once.
 */
class RecipeProblemsTest {

    @Test
    void whatTheChartCannotArrangeIsShownWithWhatTheRecipeCannotRun() {
        String fromTheChart = "Line 'L2' leaves the chart, so what is here is drawn and the rest"
                + " is not.";
        String fromTheEditor = "Pick a step first.";

        List<String> said = RecipeProblems.toShow(
                RecipeConformance.of(SfcFixtures.lineal()),
                List.of(fromTheChart),
                fromTheEditor);

        assertTrue(said.contains(fromTheChart),
                "a chart the layout could not arrange is something the operator has to be told about,"
                        + " and it is not among " + said);
        assertTrue(said.contains(fromTheEditor),
                "and so is what the editor is warning about, beside the recipe's own findings: "
                        + said);
    }
}
