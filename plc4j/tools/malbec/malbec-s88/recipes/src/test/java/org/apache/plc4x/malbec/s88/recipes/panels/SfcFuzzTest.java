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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities.ChartOp;
import org.apache.plc4x.malbec.s88.core.ChartSoundness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A fixed-seed walk through the edit orders.
 *
 * <p>Each order either changes the chart and leaves it readable, or refuses and leaves the chart
 * untouched. The seed and the history make a failure repeatable.
 */
class SfcFuzzTest {

    private static final long SEED = 20261021L;
    private static final int SEQUENCES = 500;

    @Test
    void randomEditsEitherHoldOrChangeNothing() {
        Random random = new Random(SEED);
        List<String> history = new ArrayList<>();
        for (int sequence = 0; sequence < SEQUENCES; sequence++) {
            S88MasterRecipe recipe =
                    SfcFixtures.Buttons.lineal("FUZZ_" + sequence);
            int length = 10 + random.nextInt(21);
            for (int step = 0; step < length; step++) {
                ChartOp op = ChartOp.values()[random.nextInt(ChartOp.values().length)];
                Set<String> selection = pick(random, recipe.getProcedureLogic(), op);
                String before = GraphSnapshot.of(recipe.getProcedureLogic());
                ChartCapabilities.Capability answer =
                        ChartCapabilities.can(recipe, null, op, selection);
                try {
                    String made = ChartCapabilities.perform(recipe, null, op, selection);
                    if (!answer.enabled()) {
                        fail("The pick was refused but the order ran: " + op + " " + selection
                                + " made " + made);
                    }
                    assertTrue(ChartSoundness.check(recipe.getProcedureLogic()).isEmpty(),
                            sequence + "/" + step + " " + op + " " + selection
                                    + " left an unsound chart: "
                                    + ChartSoundness.check(recipe.getProcedureLogic()));
                    SfcLayoutAssertions.assertLayout(recipe);
                    history.add(op + " " + selection + " -> " + made);
                } catch (IllegalArgumentException | IllegalStateException refusal) {
                    if (answer.enabled()) {
                        fail("The pick was allowed but the order refused: " + op + " "
                                + selection + " said " + answer.reason(), refusal);
                    }
                    assertEquals(before, GraphSnapshot.of(recipe.getProcedureLogic()),
                            sequence + "/" + step + " " + op + " " + selection
                                    + " changed the chart before it refused: "
                                    + refusal.getMessage());
                    history.add(op + " " + selection + " -> refused: " + refusal.getMessage());
                } catch (AssertionError failure) {
                    throw new AssertionError("seed " + SEED + ", sequence " + sequence
                            + ", step " + step + ", history " + history, failure);
                }
            }
        }
    }

    private static Set<String> pick(Random random, S88ProcedureLogic chart, ChartOp op) {
        List<String> all = allIds(chart);
        List<String> relevant = relevantIds(chart, op);
        boolean guided = !relevant.isEmpty() && random.nextInt(10) < 7;
        List<String> pool = guided ? relevant : all;
        int size;
        if (op == ChartOp.DELETE) {
            size = 1 + random.nextInt(3);
        } else if (op == ChartOp.JOIN_BRANCHES && guided) {
            size = 2 + random.nextInt(2);
        } else {
            size = 1 + random.nextInt(guided ? 1 : 3);
        }
        size = Math.min(size, pool.size());
        Set<String> picked = new LinkedHashSet<>();
        while (picked.size() < size && !pool.isEmpty()) {
            picked.add(pool.get(random.nextInt(pool.size())));
        }
        return picked;
    }

    private static List<String> relevantIds(S88ProcedureLogic chart, ChartOp op) {
        return switch (op) {
            case DELETE -> allIds(chart);
            case REMOVE_BRANCH, MOVE_BRANCH_LEFT, MOVE_BRANCH_RIGHT, JOIN_BRANCHES,
                 LOOP_STEP, INSERT_BEFORE, INSERT_AFTER -> steps(chart);
            case REMOVE_SKIP -> transitionsAndLinks(chart);
            case INSERT_IN_BRANCH -> transitions(chart);
            case INSERT_BEFORE_FORK, INSERT_AFTER_JOIN -> transitionsAndLinks(chart);
        };
    }

    private static List<String> allIds(S88ProcedureLogic chart) {
        List<String> ids = new ArrayList<>(steps(chart));
        ids.addAll(transitionsAndLinks(chart));
        return ids;
    }

    private static List<String> steps(S88ProcedureLogic chart) {
        return chart.getSteps().stream().map(S88ProcedureStep::getId).toList();
    }

    private static List<String> transitions(S88ProcedureLogic chart) {
        return chart.getTransitions().stream().map(S88ProcedureTransition::getId).toList();
    }

    private static List<String> transitionsAndLinks(S88ProcedureLogic chart) {
        List<String> ids = new ArrayList<>(transitions(chart));
        ids.addAll(chart.getLinks().stream().map(S88ProcedureLink::getId).toList());
        return ids;
    }
}
