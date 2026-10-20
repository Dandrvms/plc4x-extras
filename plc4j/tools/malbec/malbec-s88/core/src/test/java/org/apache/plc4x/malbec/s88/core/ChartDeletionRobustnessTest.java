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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Takes every box and every bar off a chart, one at a time, and asks what is left.
 *
 * <p>An edit either finishes with a sound chart or refuses before it touches anything. A chart
 * that is already irregular may still be edited, so a refusal is only wrong when it leaves the
 * chart changed.
 */
class ChartDeletionRobustnessTest {

    @Test
    void everyNodeOfEveryFixtureCanBeTakenOffOrRefusedCleanly() {
        List<Supplier<S88MasterRecipe>> fixtures = List.of(
                () -> chain(3),
                ChartDeletionRobustnessTest::parallelWithThreeBranches,
                ChartDeletionRobustnessTest::alternativeWithThreeBranches,
                ChartDeletionRobustnessTest::optionalBranch,
                ChartDeletionRobustnessTest::loop,
                ChartDeletionRobustnessTest::loopOverParallel,
                ChartDeletionRobustnessTest::partialSynchronization);

        for (Supplier<S88MasterRecipe> fixture : fixtures) {
            S88MasterRecipe names = fixture.get();
            List<String> nodes = new ArrayList<>(namesOfSteps(names));
            nodes.addAll(namesOfTransitions(names));
            for (String node : nodes) {
                if (node.equals(CreateMasterRecipeUseCase.BEGIN_ID)
                        || node.equals(CreateMasterRecipeUseCase.END_ID)) {
                    continue;
                }
                S88MasterRecipe recipe = fixture.get();
                Set<String> before = signature(recipe);
                boolean tookItOff = true;
                try {
                    if (recipe.getProcedureLogic().findStep(node).isPresent()) {
                        ChartDeletionUseCase.deleteBox(recipe, null, node);
                    } else {
                        ChartDeletionUseCase.deleteTransition(recipe, null, node);
                    }
                } catch (IllegalStateException refused) {
                    tookItOff = false;
                    assertTrue(refused.getMessage().contains("Nothing has been changed."),
                            "and a refusal says that nothing was changed: "
                                    + refused.getMessage());
                    assertEquals(before, signature(recipe),
                            "and the chart is exactly as it was after refusing '" + node + "'");
                }
                if (tookItOff) {
                    assertFalse(recipe.getProcedureLogic().findStep(node).isPresent()
                                    || recipe.getProcedureLogic().findTransition(node).isPresent(),
                            "and '" + node + "' is gone");
                    assertEquals(List.of(), ChartSoundness.check(recipe.getProcedureLogic()),
                            "and the chart is sound after taking off '" + node + "'");
                }
            }
        }
    }

    private static S88MasterRecipe chain(int steps) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("R", S88RecipeKind.CLASS);
        String previous = CreateMasterRecipeUseCase.BEGIN_ID;
        for (int i = 0; i < steps; i++) {
            previous = BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, previous).getId();
        }
        return recipe;
    }

    private static S88MasterRecipe parallelWithThreeBranches() {
        S88MasterRecipe recipe = chain(2);
        ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP_2");
        return recipe;
    }

    private static S88MasterRecipe alternativeWithThreeBranches() {
        S88MasterRecipe recipe = chain(2);
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "STEP");
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "STEP_2");
        return recipe;
    }

    private static S88MasterRecipe optionalBranch() {
        S88MasterRecipe recipe = chain(3);
        ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_1");
        return recipe;
    }

    private static S88MasterRecipe loop() {
        S88MasterRecipe recipe = chain(3);
        EditProcedureLogicUseCase.joinStepsThroughATransition(recipe, null, "STEP_2", "STEP");
        return recipe;
    }

    private static S88MasterRecipe loopOverParallel() {
        S88MasterRecipe recipe = chain(3);
        EditProcedureLogicUseCase.joinStepsThroughATransition(recipe, null, "STEP_2", "STEP");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP_1");
        return recipe;
    }

    private static S88MasterRecipe partialSynchronization() {
        S88MasterRecipe recipe = ChartFixtures.master("A", "B", "C", "D", "E");
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        ChartFixtures.transition(chart, "T_OPEN");
        ChartFixtures.transition(chart, "T_MID");
        ChartFixtures.transition(chart, "T_CLOSE");
        ChartFixtures.link(chart, "L_BEGIN", ChartFixtures.step("BEGIN"),
                ChartFixtures.transition("T_OPEN"));
        ChartFixtures.link(chart, "L_OPEN", List.of(ChartFixtures.transition("T_OPEN")),
                ChartFixtures.steps("A", "B", "C"), S88LinkType.PARALLEL_DIVERGENT);
        ChartFixtures.link(chart, "L_MID", ChartFixtures.steps("A", "B", "C"),
                List.of(ChartFixtures.transition("T_MID")), S88LinkType.PARALLEL_CONVERGENT);
        ChartFixtures.link(chart, "L_SPLIT", List.of(ChartFixtures.transition("T_MID")),
                ChartFixtures.steps("D", "E"), S88LinkType.PARALLEL_DIVERGENT);
        ChartFixtures.link(chart, "L_CLOSE", ChartFixtures.steps("D", "E"),
                List.of(ChartFixtures.transition("T_CLOSE")), S88LinkType.PARALLEL_CONVERGENT);
        ChartFixtures.link(chart, "L_END", ChartFixtures.transition("T_CLOSE"),
                ChartFixtures.step("END"));
        return recipe;
    }

    private static List<String> namesOfSteps(S88MasterRecipe recipe) {
        return recipe.getProcedureLogic().getSteps().stream().map(step -> step.getId()).toList();
    }

    private static List<String> namesOfTransitions(S88MasterRecipe recipe) {
        return recipe.getProcedureLogic().getTransitions().stream()
                .map(bar -> bar.getId()).toList();
    }

    private static Set<String> signature(S88MasterRecipe recipe) {
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        Set<String> found = new TreeSet<>();
        chart.getSteps().forEach(step -> found.add("STEP " + step.getId()));
        chart.getTransitions().forEach(bar -> found.add("BAR " + bar.getId()));
        for (S88ProcedureLink link : chart.getLinks()) {
            found.add("LINK " + link.getId() + " " + link.getLinkType() + " "
                    + names(link.getFrom()) + " -> " + names(link.getTo()));
        }
        return found;
    }

    private static String names(List<org.apache.plc4x.malbec.s88.api.S88IdRef> ends) {
        return ends.stream()
                .map(end -> end.getValue())
                .collect(Collectors.joining(","));
    }
}
