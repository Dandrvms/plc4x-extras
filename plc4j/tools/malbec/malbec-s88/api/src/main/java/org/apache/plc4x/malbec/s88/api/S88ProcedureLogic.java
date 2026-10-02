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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The chart of a recipe: the boxes, the lines between them, and the bars the flow has to cross.
 * <p>
 * What it does offer is lookup: given a name, find the step, the line, or the bar. That is enough
 * to draw the chart and enough to check it, and it commits to nothing about running it.
 * <p>
 * The three lists keep the order the recipe format requires, which is not the order they read best
 * in: links, then steps, then transitions.
 */
public class S88ProcedureLogic {

    private final List<S88ProcedureLink> links = new ArrayList<>();
    private final List<S88ProcedureStep> steps = new ArrayList<>();
    private final List<S88ProcedureTransition> transitions = new ArrayList<>();
    private final List<S88OtherInformation> otherInformation = new ArrayList<>();

    private final Map<String, S88ProcedureStep> stepById = new LinkedHashMap<>();
    private final Map<String, S88ProcedureLink> linkById = new LinkedHashMap<>();
    private final Map<String, S88ProcedureTransition> transitionById = new LinkedHashMap<>();
    private final List<String> danglingReferences = new ArrayList<>();

    public List<S88ProcedureLink> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public void addLink(S88ProcedureLink link) {
        if (link != null) {
            links.add(link);
            index(link);
        }
    }

    public void removeLink(S88ProcedureLink link) {
        if (link != null && links.remove(link)) {
            unindex(link);
        }
    }

    public List<S88ProcedureStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public void addStep(S88ProcedureStep step) {
        if (step != null) {
            steps.add(step);
            index(step);
        }
    }

    public void removeStep(S88ProcedureStep step) {
        if (step != null && steps.remove(step)) {
            unindex(step);
        }
    }

    public List<S88ProcedureTransition> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }

    public void addTransition(S88ProcedureTransition transition) {
        if (transition != null) {
            transitions.add(transition);
            index(transition);
        }
    }

    public void removeTransition(S88ProcedureTransition transition) {
        if (transition != null && transitions.remove(transition)) {
            unindex(transition);
        }
    }

    /**
     * Where the chart is drawn. Presentational only, and safe to leave empty: a chart with no
     * layout still describes the same process.
     */
    public List<S88OtherInformation> getOtherInformation() {
        return Collections.unmodifiableList(otherInformation);
    }

    public void addOtherInformation(S88OtherInformation info) {
        if (info != null) {
            otherInformation.add(info);
        }
    }

    public void removeOtherInformation(S88OtherInformation info) {
        otherInformation.remove(info);
    }

    public Optional<S88ProcedureStep> findStep(String id) {
        return Optional.ofNullable(stepById.get(id));
    }

    public Optional<S88ProcedureLink> findLink(String id) {
        return Optional.ofNullable(linkById.get(id));
    }

    public Optional<S88ProcedureTransition> findTransition(String id) {
        return Optional.ofNullable(transitionById.get(id));
    }

    /**
     * The lines that meet at the given endpoint, whichever end of each line it is.
     *
     * @param ref the endpoint to look for
     * @return the links touching it, in the order they were added
     */
    public List<S88ProcedureLink> linksTouching(S88IdRef ref) {
        if (ref == null) {
            return List.of();
        }
        List<S88ProcedureLink> touching = new ArrayList<>();
        for (S88ProcedureLink link : links) {
            if (link.touches(ref)) {
                touching.add(link);
            }
        }
        return touching;
    }

    /** The lines leaving the given step. */
    public List<S88ProcedureLink> linksFrom(String stepId) {
        return linksTouching(S88IdRef.step(stepId));
    }

    /**
     * The lines that split, marked by the recipe as sending the flow down more than one side.
     * <p>
     * This reports what the recipe says, not what the shape of the graph appears to be. A link
     * marked parallel divergent with a single destination is a mistake in the recipe, and it is
     * worth seeing as one rather than being quietly corrected into something that looks reasonable.
     *
     * @return the links the recipe marks as splitting
     */
    public List<S88ProcedureLink> getDivergentLinks() {
        List<S88ProcedureLink> divergent = new ArrayList<>();
        for (S88ProcedureLink link : links) {
            if (link.isDivergent()) {
                divergent.add(link);
            }
        }
        return divergent;
    }

    /** The lines that bring two sides of the flow back together. */
    public List<S88ProcedureLink> getConvergentLinks() {
        List<S88ProcedureLink> convergent = new ArrayList<>();
        for (S88ProcedureLink link : links) {
            if (link.isConvergent()) {
                convergent.add(link);
            }
        }
        return convergent;
    }

    /**
     * Endpoints of internal links that name a step or a bar this chart does not carry.
     * <p>
     * A chart whose line points at a box that is not there cannot be run, but it can still be opened
     * and read. So the names are collected instead of rejected at load time.
     *
     * @return the names that could not be resolved, in the order they were found
     */
    public List<String> getDanglingReferences() {
        return Collections.unmodifiableList(danglingReferences);
    }

    /** True when every endpoint of every line names something this chart carries. */
    public boolean isDanglingFree() {
        return danglingReferences.isEmpty();
    }

    private void index(S88ProcedureStep step) {
        put(stepById, step != null ? step.getId() : null, step, "step");
    }

    private void index(S88ProcedureLink link) {
        put(linkById, link != null ? link.getId() : null, link, "link");
    }

    private void index(S88ProcedureTransition transition) {
        put(transitionById, transition != null ? transition.getId() : null, transition, "transition");
    }

    private void unindex(S88ProcedureStep step) {
        take(stepById, step != null ? step.getId() : null, step);
    }

    private void unindex(S88ProcedureLink link) {
        take(linkById, link != null ? link.getId() : null, link);
    }

    private void unindex(S88ProcedureTransition transition) {
        take(transitionById, transition != null ? transition.getId() : null, transition);
    }

    private <T> void put(Map<String, T> index, String id, T item, String kind) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (index.putIfAbsent(id, item) != null) {
            throw new IllegalStateException("Procedure logic already carries a " + kind
                    + " with id '" + id + "'.");
        }
        checkReferences();
    }

    private <T> void take(Map<String, T> index, String id, T item) {
        if (id != null && index.get(id) == item) {
            index.remove(id);
        }
        checkReferences();
    }

    /**
     * Works out which internal endpoints do not name anything here. Recomputed whenever the chart
     * changes rather than kept up to date at every step, because the list is small next to the cost
     * of being wrong about it.
     */
    private void checkReferences() {
        danglingReferences.clear();
        for (S88ProcedureLink link : links) {
            collectDangling(link.getFrom());
            collectDangling(link.getTo());
        }
    }

    private void collectDangling(List<S88IdRef> refs) {
        for (S88IdRef ref : refs) {
            if (ref == null || !ref.isInternal() || ref.getValue() == null) {
                continue;
            }
            boolean known = switch (ref.getType()) {
                case STEP -> stepById.containsKey(ref.getValue());
                case TRANSITION -> transitionById.containsKey(ref.getValue());
                case LINK -> linkById.containsKey(ref.getValue());
                case OTHER -> false;
            };
            if (!known && !danglingReferences.contains(ref.getValue())) {
                danglingReferences.add(ref.getValue());
            }
        }
    }

    @Override
    public String toString() {
        return "S88ProcedureLogic[" + steps.size() + " step(s), " + links.size() + " link(s), "
                + transitions.size() + " transition(s)]";
    }
}
