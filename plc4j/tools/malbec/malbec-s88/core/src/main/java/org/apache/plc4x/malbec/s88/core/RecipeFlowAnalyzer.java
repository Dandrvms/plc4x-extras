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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;

/**
 * Reads a recipe as a route: the order of the steps, the branches, the loops and the subcharts.
 */
public final class RecipeFlowAnalyzer {

    /** What the route is allowed to show. */
    public record Options(boolean expandSubcharts, int maxRows, int maxDepth) {
        public Options {
            if (maxRows < 1) {
                throw new IllegalArgumentException("A route needs room for at least one row.");
            }
            if (maxDepth < 0) {
                throw new IllegalArgumentException("The depth cannot be negative.");
            }
        }

        public static Options defaults() {
            return new Options(true, 5000, 12);
        }
    }

    public enum RowKind {
        BEGIN, STEP, TRANSITION, FORK_PARALLEL, JOIN_PARALLEL, CHOICE, MERGE, SKIP, LOOP_BACK,
        SUBCHART_ENTER, SUBCHART_EXIT, END
    }

    public record Row(int index, int depth, String chartPath, RowKind kind, String id,
                      String elementId, String label, String detail, String flow, String note) {
        public Row withNote(String extra) {
            if (extra == null || extra.isBlank()) {
                return this;
            }
            String joined = note == null || note.isBlank() ? extra : note + "; " + extra;
            return new Row(index, depth, chartPath, kind, id, elementId, label, detail, flow, joined);
        }
    }

    public record Route(List<Row> rows, List<String> warnings) {
        public Route {
            rows = List.copyOf(rows);
            warnings = List.copyOf(warnings);
        }
    }

    private static final String BEGIN = CreateMasterRecipeUseCase.BEGIN_ID;
    private static final String END = CreateMasterRecipeUseCase.END_ID;

    private RecipeFlowAnalyzer() {
        /* This utility class should not be instantiated */
    }

    public static Route analyze(S88Recipe recipe) {
        return analyze(recipe, Options.defaults());
    }

    public static Route analyze(S88Recipe recipe, Options options) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(options, "options");
        Analysis analysis = new Analysis(recipe, options);
        S88ProcedureLogic root = recipe.getProcedureLogic();
        if (root == null) {
            analysis.warnings.add("The recipe has no chart.");
        } else {
            String path = recipe.getId() == null || recipe.getId().isBlank() ? "recipe" : recipe.getId();
            walkChart(analysis, root, path, 0, "");
        }
        return new Route(analysis.rows, analysis.warnings);
    }

    private static final class Analysis {
        private final S88Recipe recipe;
        private final Options options;
        private final List<Row> rows = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Set<String> elementStack = new LinkedHashSet<>();
        private int parallelGroups;
        private int choiceGroups;
        private boolean truncated;

        private Analysis(S88Recipe recipe, Options options) {
            this.recipe = recipe;
            this.options = options;
        }
    }

    private static final class ChartWalk {
        private final S88ProcedureLogic logic;
        private final ChartGraph graph;
        private final Map<String, String> ipdom;
        private final Set<ChartGraph.Edge> backEdges;
        private final String chartPath;
        private final int depth;
        private final Map<String, Integer> rowOfNode = new LinkedHashMap<>();

        private ChartWalk(S88ProcedureLogic logic, ChartGraph graph, Map<String, String> ipdom,
                          Set<ChartGraph.Edge> backEdges, String chartPath, int depth) {
            this.logic = logic;
            this.graph = graph;
            this.ipdom = ipdom;
            this.backEdges = backEdges;
            this.chartPath = chartPath;
            this.depth = depth;
        }
    }

    private static void walkChart(Analysis analysis, S88ProcedureLogic logic, String chartPath,
                                  int depth, String flow) {
        if (analysis.truncated) {
            return;
        }
        ChartSoundness.Report report = ChartSoundness.inspect(logic);
        for (String error : report.errors()) {
            analysis.warnings.add(chartPath + ": " + error);
        }
        for (String warning : report.warnings()) {
            analysis.warnings.add(chartPath + ": " + warning);
        }
        ChartGraph graph = ChartGraph.of(logic);
        if (graph.nodes().isEmpty()) {
            analysis.warnings.add(chartPath + ": the chart is empty.");
            return;
        }
        Set<ChartGraph.Edge> backEdges = graph.backEdges();
        ChartWalk walk = new ChartWalk(logic, graph, immediatePostDominators(graph, backEdges),
                backEdges, chartPath, depth);
        String start = graph.hasNode(BEGIN) ? BEGIN : graph.steps().stream().findFirst().orElse(null);
        if (start == null) {
            analysis.warnings.add(chartPath + ": the chart has no start step.");
            return;
        }
        walk(analysis, walk, start, null, flow);
    }

    private static void walk(Analysis analysis, ChartWalk chart, String node, String stopAt,
                             String flow) {
        if (analysis.truncated || node == null || node.equals(stopAt) || !chart.graph.hasNode(node)) {
            return;
        }
        if (chart.graph.isTransition(node)) {
            List<String> branches = parallelBranches(chart.graph, node);
            if (!branches.isEmpty()) {
                emitParallel(analysis, chart, node, branches, stopAt, flow);
                return;
            }
        }
        List<String> choices = selectiveBranches(chart.graph, node);
        if (!choices.isEmpty()) {
            if (chart.graph.isStep(node)) {
                int index = emitNode(analysis, chart, node, flow);
                if (index >= 0) {
                    chart.rowOfNode.putIfAbsent(node, index);
                    expandSubchart(analysis, chart, node, flow);
                }
            }
            emitChoice(analysis, chart, node, choices, stopAt, flow);
            return;
        }
        int index = emitNode(analysis, chart, node, flow);
        if (index >= 0) {
            chart.rowOfNode.putIfAbsent(node, index);
        }
        if (chart.graph.isStep(node) && !node.equals(BEGIN) && !node.equals(END)) {
            expandSubchart(analysis, chart, node, flow);
        }
        LinkedHashSet<String> next = successors(chart.graph, node);
        if (next.size() != 1) {
            if (next.size() > 1) {
                analysis.warnings.add(chart.chartPath + ": the flow branches at '" + node
                        + "' in a way the route cannot follow.");
            }
            return;
        }
        String target = next.iterator().next();
        if (chart.backEdges.contains(new ChartGraph.Edge(node, target))) {
            emitLoopBack(analysis, chart, node, target, flow);
            return;
        }
        walk(analysis, chart, target, stopAt, flow);
    }

    private static void emitParallel(Analysis analysis, ChartWalk chart, String node,
                                     List<String> branches, String stopAt, String flow) {
        String join = chart.ipdom.get(node);
        if (join == null || join.equals(node)) {
            join = stopAt;
        }
        int group = ++analysis.parallelGroups;
        String name = "P" + group;
        emit(analysis, chart, RowKind.FORK_PARALLEL, node, null,
                "in parallel (" + name + ")", branches.size() + " branches"
                        + detailSuffix(chart, node), flow, null);
        char letter = 'a';
        for (String branch : branches) {
            walk(analysis, chart, branch, join, branchFlow(flow, name + "." + letter));
            letter++;
        }
        if (analysis.truncated) {
            return;
        }
        if (join == null) {
            analysis.warnings.add(chart.chartPath + ": the branches at '" + node
                    + "' do not come back together.");
            emit(analysis, chart, RowKind.JOIN_PARALLEL, node, null, "join", null, flow,
                    "the branches do not come back together");
            return;
        }
        if (join.equals(stopAt)) {
            return;
        }
        emit(analysis, chart, RowKind.JOIN_PARALLEL, join, null, "join",
                detail(chart, join), flow, null);
        continueAfter(analysis, chart, join, stopAt, flow);
    }

    private static void emitChoice(Analysis analysis, ChartWalk chart, String node,
                                   List<String> choices, String stopAt, String flow) {
        String merge = chart.ipdom.get(node);
        if (merge == null || merge.equals(node)) {
            merge = stopAt;
        }
        int group = ++analysis.choiceGroups;
        String name = "A" + group;
        emit(analysis, chart, RowKind.CHOICE, node, null, "choice (" + name + ")",
                choices.size() + " paths" + detailSuffix(chart, node), flow, null);
        int number = 0;
        for (String choice : choices) {
            number++;
            String choiceFlow = branchFlow(flow, name + "." + number);
            if (chart.graph.isTransition(choice) && chart.graph.isSkip(node, choice)) {
                emit(analysis, chart, RowKind.SKIP, choice, null, "no steps",
                        "skips to " + choice, choiceFlow, null);
            } else if (!choice.equals(merge)) {
                walk(analysis, chart, choice, merge, choiceFlow);
            }
        }
        if (analysis.truncated || merge == null || merge.equals(stopAt)) {
            return;
        }
        emit(analysis, chart, RowKind.MERGE, merge, null, "join", detail(chart, merge), flow, null);
        continueAfter(analysis, chart, merge, stopAt, flow);
    }

    private static void continueAfter(Analysis analysis, ChartWalk chart, String node,
                                      String stopAt, String flow) {
        if (analysis.truncated || node == null || node.equals(stopAt)) {
            return;
        }
        if (node.equals(END)) {
            emitNode(analysis, chart, END, flow);
            return;
        }
        List<String> parallel = parallelBranches(chart.graph, node);
        if (!parallel.isEmpty()) {
            emitParallel(analysis, chart, node, parallel, stopAt, flow);
            return;
        }
        List<String> choices = selectiveBranches(chart.graph, node);
        if (!choices.isEmpty()) {
            emitChoice(analysis, chart, node, choices, stopAt, flow);
            return;
        }
        LinkedHashSet<String> next = successors(chart.graph, node);
        if (next.size() != 1) {
            return;
        }
        String target = next.iterator().next();
        if (chart.backEdges.contains(new ChartGraph.Edge(node, target))) {
            emitLoopBack(analysis, chart, node, target, flow);
            return;
        }
        walk(analysis, chart, target, stopAt, flow);
    }

    private static void emitLoopBack(Analysis analysis, ChartWalk chart, String node, String target,
                                     String flow) {
        emit(analysis, chart, RowKind.LOOP_BACK, target, null, "back to " + target,
                detail(chart, node), flow, null);
        Integer row = chart.rowOfNode.get(target);
        if (row != null && row >= 0 && row < analysis.rows.size()) {
            analysis.rows.set(row, analysis.rows.get(row).withNote("loop start"));
        }
    }

    private static int emitNode(Analysis analysis, ChartWalk chart, String node, String flow) {
        if (node.equals(BEGIN)) {
            return emit(analysis, chart, RowKind.BEGIN, node, null, "BEGIN", null, flow, null);
        }
        if (node.equals(END)) {
            return emit(analysis, chart, RowKind.END, node, null, "END", null, flow, null);
        }
        if (chart.graph.isStep(node)) {
            String elementId = elementId(chart, node);
            return emit(analysis, chart, RowKind.STEP, node, elementId, "Step " + node,
                    stepDetail(analysis, elementId), flow, null);
        }
        return emit(analysis, chart, RowKind.TRANSITION, node, null, "Transition " + node,
                detail(chart, node), flow, null);
    }

    private static int emit(Analysis analysis, ChartWalk chart, RowKind kind, String id,
                            String elementId, String label, String detail, String flow, String note) {
        if (analysis.truncated) {
            return -1;
        }
        if (analysis.rows.size() >= analysis.options.maxRows()) {
            analysis.truncated = true;
            analysis.warnings.add("The route is cut after " + analysis.options.maxRows() + " rows.");
            return -1;
        }
        int index = analysis.rows.size() + 1;
        analysis.rows.add(new Row(index, chart.depth, chart.chartPath, kind, id, elementId,
                label, detail, flow, note));
        return analysis.rows.size() - 1;
    }

    private static void expandSubchart(Analysis analysis, ChartWalk chart, String stepId, String flow) {
        if (!analysis.options.expandSubcharts()) {
            return;
        }
        String elementId = elementId(chart, stepId);
        if (elementId == null) {
            return;
        }
        S88RecipeElement element = analysis.recipe.findElement(elementId).orElse(null);
        if (element == null || element.getProcedureLogic() == null) {
            return;
        }
        S88ProcedureLogic child = element.getProcedureLogic();
        if (child.getSteps().isEmpty() && child.getTransitions().isEmpty() && child.getLinks().isEmpty()) {
            return;
        }
        if (analysis.elementStack.contains(elementId)) {
            analysis.warnings.add(chart.chartPath + ": recursive subchart '" + elementId
                    + "' not expanded.");
            return;
        }
        if (chart.depth + 1 > analysis.options.maxDepth()) {
            analysis.warnings.add(chart.chartPath + ": maximum depth reached at '"
                    + elementId + "'.");
            return;
        }
        String childPath = chart.chartPath + "/" + elementId;
        emit(analysis, chart, RowKind.SUBCHART_ENTER, elementId, elementId,
                "subchart " + elementId, null, flow, null);
        analysis.elementStack.add(elementId);
        walkChart(analysis, child, childPath, chart.depth + 1, flow);
        analysis.elementStack.remove(elementId);
        emit(analysis, chart, RowKind.SUBCHART_EXIT, elementId, elementId,
                "end of subchart " + elementId, null, flow, null);
    }

    private static List<String> parallelBranches(ChartGraph graph, String node) {
        if (!graph.isTransition(node)) {
            return List.of();
        }
        LinkedHashSet<String> branches = new LinkedHashSet<>();
        boolean parallel = false;
        for (ChartGraph.Junction junction : graph.outJunctions(node)) {
            if (junction.kind().isDivergent() && junction.kind().isParallel()) {
                parallel = true;
                for (String target : junction.tos()) {
                    if (graph.isStep(target)) {
                        branches.add(target);
                    }
                }
            }
        }
        return parallel && branches.size() >= 2 ? List.copyOf(branches) : List.of();
    }

    private static List<String> selectiveBranches(ChartGraph graph, String node) {
        LinkedHashSet<String> targets = successors(graph, node);
        if (targets.size() < 2) {
            return List.of();
        }
        for (ChartGraph.Junction junction : graph.outJunctions(node)) {
            if (junction.kind().isDivergent() && junction.kind().isParallel()) {
                return List.of();
            }
        }
        return List.copyOf(targets);
    }

    private static LinkedHashSet<String> successors(ChartGraph graph, String node) {
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (ChartGraph.Junction junction : graph.outJunctions(node)) {
            next.addAll(junction.tos());
        }
        return next;
    }

    private static Map<String, String> immediatePostDominators(ChartGraph graph,
                                                               Set<ChartGraph.Edge> backEdges) {
        Set<String> nodes = graph.nodes();
        Map<String, List<String>> successors = new LinkedHashMap<>();
        for (String node : nodes) {
            List<String> next = new ArrayList<>();
            for (ChartGraph.Junction junction : graph.outJunctions(node)) {
                for (String target : junction.tos()) {
                    if (!backEdges.contains(new ChartGraph.Edge(node, target))) {
                        next.add(target);
                    }
                }
            }
            successors.put(node, next);
        }
        Map<String, Set<String>> post = new LinkedHashMap<>();
        for (String node : nodes) {
            post.put(node, new LinkedHashSet<>(nodes));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String node : nodes) {
                List<String> next = successors.get(node);
                Set<String> found = new LinkedHashSet<>();
                found.add(node);
                if (!next.isEmpty()) {
                    Set<String> common = null;
                    for (String target : next) {
                        Set<String> targetPost = post.get(target);
                        if (common == null) {
                            common = new LinkedHashSet<>(targetPost);
                        } else {
                            common.retainAll(targetPost);
                        }
                    }
                    if (common != null) {
                        found.addAll(common);
                    }
                }
                if (!found.equals(post.get(node))) {
                    post.put(node, found);
                    changed = true;
                }
            }
        }
        Map<String, String> ipdom = new LinkedHashMap<>();
        for (String node : nodes) {
            Set<String> candidates = new LinkedHashSet<>(post.get(node));
            candidates.remove(node);
            String best = candidates.stream()
                    .max(Comparator.comparingInt(candidate -> post.get(candidate).size()))
                    .orElse(null);
            if (best != null) {
                ipdom.put(node, best);
            }
        }
        return ipdom;
    }

    private static String elementId(ChartWalk chart, String stepId) {
        S88ProcedureStep step = chart.logic.findStep(stepId).orElse(null);
        if (step == null || step.getRecipeElementId() == null
                || step.getRecipeElementId().isBlank()) {
            return null;
        }
        return step.getRecipeElementId();
    }

    private static String stepDetail(Analysis analysis, String elementId) {
        if (elementId == null) {
            return null;
        }
        return analysis.recipe.findElement(elementId)
                .map(RecipeFlowAnalyzer::stepDetail)
                .orElse(null);
    }

    private static String stepDetail(S88RecipeElement element) {
        StringBuilder detail = new StringBuilder();
        if (!element.getActualEquipmentIds().isEmpty()) {
            detail.append("equipment ").append(String.join(", ", element.getActualEquipmentIds()));
        } else if (element.getEquipmentClassId() != null && !element.getEquipmentClassId().isBlank()) {
            detail.append("class ").append(element.getEquipmentClassId());
        }
        if (!element.getParameters().isEmpty()) {
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append(element.getParameters().size()).append(" parameters");
        }
        return detail.length() == 0 ? null : detail.toString();
    }

    private static String detail(ChartWalk chart, String node) {
        if (chart.graph.isTransition(node)) {
            S88ProcedureTransition transition = chart.logic.findTransition(node).orElse(null);
            if (transition != null) {
                S88ConditionExpression expression = transition.expression();
                if (expression != null) {
                    return expression.toDisplay();
                }
                return transition.getCondition();
            }
        }
        return null;
    }

    private static String detailSuffix(ChartWalk chart, String node) {
        String detail = detail(chart, node);
        return detail == null || detail.isBlank() ? "" : "; " + detail;
    }

    private static String branchFlow(String parent, String branch) {
        return parent == null || parent.isBlank() ? branch : parent + "." + branch;
    }
}
