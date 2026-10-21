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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.ChartNormalizer;
import org.apache.plc4x.malbec.s88.core.ChartSoundness;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.data.impl.BatchMLRecipeRepositoryImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A recipe written and read again keeps its normalized chart. */
class RecipeGraphRoundTripTest {

    @Test
    void aChainKeepsItsGraph() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("ROUND_TRIP_CHAIN",
                S88RecipeKind.CLASS);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP");

        roundTrip(recipe);
    }

    @Test
    void aParallelBlockKeepsItsGraph() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("ROUND_TRIP_PARALLEL",
                S88RecipeKind.CLASS);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP");

        roundTrip(recipe);
    }

    @Test
    void aSelectiveBlockKeepsItsGraph() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("ROUND_TRIP_SELECTIVE",
                S88RecipeKind.CLASS);
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "STEP");

        roundTrip(recipe);
    }

    private static void roundTrip(S88MasterRecipe recipe) {
        assertEquals(List.of(), ChartNormalizer.normalize(recipe.getProcedureLogic()),
                "the recipe starts well formed");
        String before = GraphSnapshot.of(recipe.getProcedureLogic());

        InMemory storage = new InMemory();
        new BatchMLRecipeRepositoryImpl(storage).saveRecipe(recipe);
        S88MasterRecipe read = new BatchMLRecipeRepositoryImpl(storage).loadRecipe();

        assertNotNull(read, "the written recipe comes back");
        assertEquals(List.of(), ChartNormalizer.normalize(read.getProcedureLogic()),
                "the read recipe is well formed");
        assertEquals(before, GraphSnapshot.of(read.getProcedureLogic()),
                "the chart survives the file unchanged");
        assertEquals(List.of(), ChartSoundness.check(read.getProcedureLogic()),
                "the read chart can run");
    }

    /** A stable text form of a chart, so that two graphs can be compared after a round trip. */
    private static final class GraphSnapshot {

        private GraphSnapshot() {
        }

        static String of(S88ProcedureLogic chart) {
            StringBuilder text = new StringBuilder();
            chart.getSteps().stream()
                    .map(S88ProcedureStep::getId)
                    .sorted()
                    .forEach(id -> text.append("step ").append(id).append('\n'));
            chart.getTransitions().stream()
                    .map(S88ProcedureTransition::getId)
                    .sorted()
                    .forEach(id -> text.append("bar ").append(id).append('\n'));
            chart.getLinks().stream()
                    .map(GraphSnapshot::link)
                    .sorted()
                    .forEach(line -> text.append(line).append('\n'));
            chart.getDanglingReferences().stream()
                    .sorted()
                    .forEach(id -> text.append("dangling ").append(id).append('\n'));
            return text.toString();
        }

        private static String link(S88ProcedureLink link) {
            List<String> from = new ArrayList<>();
            for (S88IdRef ref : link.getFrom()) {
                from.add(ref.getValue());
            }
            from.sort(Comparator.naturalOrder());
            List<String> to = new ArrayList<>();
            for (S88IdRef ref : link.getTo()) {
                to.add(ref.getValue());
            }
            to.sort(Comparator.naturalOrder());
            return "link " + link.getId() + " " + from + " -> " + to + " " + link.getLinkType();
        }
    }

    /** Keeps the bytes of a recipe in memory for one write and one read. */
    private static final class InMemory implements S88Storage {

        private byte[] bytes = new byte[0];

        @Override
        public InputStream openInput() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public OutputStream openOutput() {
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            return new OutputStream() {
                @Override
                public void write(int b) {
                    sink.write(b);
                }

                @Override
                public void close() throws IOException {
                    bytes = sink.toByteArray();
                }
            };
        }
    }
}
