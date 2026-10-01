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

/**
 * What an {@link S88ProcedureLink} means between the two things it joins.
 * <p>
 * This is where the shape of the flow is written down rather than left to be guessed: a link
 * marked {@link #PARALLEL_DIVERGENT} says the two sides run at the same time, and one marked
 * {@link #SERIAL_DIVERGENT} says they run one after the other. Whoever runs the recipe reads this
 * instead of working out forks and joins from how many links share an endpoint.
 */
public enum S88LinkType {
    /** The flow is waiting for a transition to fire. */
    CONTROL_LINK("ControlLink"),
    /** The flow is waiting for a transition to fire. */
    TRANSFER_LINK("TransferLink"),
    /** The two sides are held in step with each other. */
    SYNCHRONIZATION_LINK("SynchronizationLink"),
    /** The flow splits and both sides run at the same time. */
    PARALLEL_DIVERGENT("ParallelDivergent"),
    /** The flow rejoins and waits for both sides to arrive. */
    PARALLEL_CONVERGENT("ParallelConvergent"),
    /** The flow splits and the sides run one after the other. */
    SERIAL_DIVERGENT("SerialDivergent"),
    /** The flow rejoins and continues with whichever side arrives first. */
    SERIAL_CONVERGENT("SerialConvergent"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /** The label this type takes in the XML, written out rather than derived from the name. */
    private final String xmlName;

    S88LinkType(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /** True when the link sends the flow down more than one side at once. */
    public boolean isDivergent() {
        return this == PARALLEL_DIVERGENT || this == SERIAL_DIVERGENT;
    }

    /** True when the link brings two sides of the flow back together. */
    public boolean isConvergent() {
        return this == PARALLEL_CONVERGENT || this == SERIAL_CONVERGENT;
    }

    /**
     * True when the two sides of the link happen at the same time, which is what the recipe says
     * by naming the link parallel rather than serial.
     */
    public boolean isParallel() {
        return this == PARALLEL_DIVERGENT || this == PARALLEL_CONVERGENT;
    }

    /**
     * Reads a link type from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching type, or {@code null} when the text names none
     */
    public static S88LinkType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88LinkType type : values()) {
            if (type.xmlName.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return null;
    }
}
