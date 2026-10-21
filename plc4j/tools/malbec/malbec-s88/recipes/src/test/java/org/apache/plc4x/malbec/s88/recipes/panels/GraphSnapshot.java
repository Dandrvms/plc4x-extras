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
import java.util.Comparator;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;

/** A stable text form of a chart, so that two graphs can be compared after an edit. */
final class GraphSnapshot {

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
