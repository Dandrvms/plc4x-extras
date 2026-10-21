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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The layout matrix: every fixture and every rule that makes a drawing readable.
 * <p>
 * The checks work on the arithmetic alone. No window is opened and nothing is painted.
 */
class SfcLayoutMatrixTest {

    @Test
    void everyNodeHasAPosition() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                SfcLayoutAssertions.everyNodeHasAPosition(recipe)));
    }

    @Test
    void boxesDoNotOverlap() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                SfcLayoutAssertions.boxesDoNotOverlap(recipe)));
    }

    @Test
    void everySegmentIsOrthogonal() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                SfcLayoutAssertions.everySegmentIsOrthogonal(recipe)));
    }

    @Test
    void noSegmentCrossesAnotherBox() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                SfcLayoutAssertions.noSegmentCrossesAnotherBox(recipe)));
    }

    @Test
    void linesLeaveBelowAndArriveAbove() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                SfcLayoutAssertions.linesLeaveBelowAndArriveAbove(recipe)));
    }

    @Test
    void layoutIsDeterministic() {
        assertAll(SfcFixtures.all().stream().map(recipe -> () ->
                assertEquals(SfcFixtures.arranged(recipe), SfcFixtures.arranged(recipe),
                        recipe.getId() + " is drawn differently on a second run")));
    }
}
