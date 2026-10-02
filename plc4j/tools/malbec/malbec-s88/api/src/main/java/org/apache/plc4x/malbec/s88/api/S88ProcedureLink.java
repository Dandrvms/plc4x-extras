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
import java.util.List;

/**
 * The line between two things on the chart, and what it means.
 * <p>
 * The {@link S88LinkType} is the part that matters: it says whether the flow splits, rejoins, and
 * whether the two sides happen at once or in turn. That is written down rather than left to be
 * worked out from how many links meet at a point, because a reader that has to infer it can get it
 * wrong in exactly the cases where it matters, and a recipe is not a place to be ambiguous.
 * <p>
 * Both ends hold any number of names, which is what lets a single link be the shared edge of a
 * split or a join.
 */
public class S88ProcedureLink {

    private String id;
    private final List<S88IdRef> from = new ArrayList<>();
    private final List<S88IdRef> to = new ArrayList<>();
    private S88LinkType linkType = S88LinkType.CONTROL_LINK;
    private S88Depiction depiction = S88Depiction.NONE;
    private Integer evaluationOrder;
    private final List<String> descriptions = new ArrayList<>();

    public S88ProcedureLink() {
    }

    public S88ProcedureLink(String id) {
        this.id = id;
    }

    /** A line from one step to another, which is the ordinary case. */
    public static S88ProcedureLink betweenSteps(String id, String fromStepId, String toStepId) {
        S88ProcedureLink link = new S88ProcedureLink(id);
        link.addFrom(S88IdRef.step(fromStepId));
        link.addTo(S88IdRef.step(toStepId));
        return link;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<S88IdRef> getFrom() {
        return Collections.unmodifiableList(from);
    }

    public void addFrom(S88IdRef ref) {
        if (ref != null) {
            from.add(ref);
        }
    }

    public void addFrom(S88IdRef... refs) {
        if (refs != null) {
            for (S88IdRef ref : refs) {
                addFrom(ref);
            }
        }
    }

    public List<S88IdRef> getTo() {
        return Collections.unmodifiableList(to);
    }

    /**
     * Detaches one end of the line.
     */
    public void removeFrom(S88IdRef ref) {
        from.remove(ref);
    }

    /** Detaches every end of this line, for when the line is going to be discarded. */
    public void clearFrom() {
        from.clear();
    }

    public void removeTo(S88IdRef ref) {
        to.remove(ref);
    }

    public void clearTo() {
        to.clear();
    }

    public void addTo(S88IdRef ref) {
        if (ref != null) {
            to.add(ref);
        }
    }

    public void addTo(S88IdRef... refs) {
        if (refs != null) {
            for (S88IdRef ref : refs) {
                addTo(ref);
            }
        }
    }

    public S88LinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(S88LinkType linkType) {
        this.linkType = linkType != null ? linkType : S88LinkType.CONTROL_LINK;
    }

    public S88Depiction getDepiction() {
        return depiction;
    }

    public void setDepiction(S88Depiction depiction) {
        this.depiction = depiction != null ? depiction : S88Depiction.NONE;
    }

    /**
     * When the two sides of this link are to be read, when the order between them matters. Optional
     * and left as written, because a recipe that does not say keeps its own order.
     */
    public Integer getEvaluationOrder() {
        return evaluationOrder;
    }

    public void setEvaluationOrder(Integer evaluationOrder) {
        this.evaluationOrder = evaluationOrder;
    }

    public List<String> getDescriptions() {
        return Collections.unmodifiableList(descriptions);
    }

    public void addDescription(String description) {
        if (description != null) {
            descriptions.add(description);
        }
    }

    /** True when the flow leaves this link down more than one side. */
    public boolean isDivergent() {
        return linkType != null && linkType.isDivergent();
    }

    /** True when the flow arrives at this link from more than one side. */
    public boolean isConvergent() {
        return linkType != null && linkType.isConvergent();
    }

    /**
     * True when this link names the given endpoint, on either end and whatever kind of thing it is
     * said to be. Used to answer which lines meet at a point without caring yet what that means.
     */
    public boolean touches(S88IdRef ref) {
        return from.contains(ref) || to.contains(ref);
    }

    @Override
    public String toString() {
        return "S88ProcedureLink[" + id + ", " + linkType + ", " + from + " -> " + to + "]";
    }
}
