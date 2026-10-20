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

/** Writes a route as text or as CSV. */
public final class RecipeFlowFormatter {

    private RecipeFlowFormatter() {
        /* This utility class should not be instantiated */
    }

    public static String toText(RecipeFlowAnalyzer.Route route) {
        StringBuilder text = new StringBuilder();
        for (RecipeFlowAnalyzer.Row row : route.rows()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(row.index()).append("  ");
            text.append("  ".repeat(row.depth() + branchIndent(row.flow())));
            if (row.flow() != null && !row.flow().isBlank()) {
                text.append('[').append(row.flow()).append("] ");
            }
            text.append(row.label());
            if (row.detail() != null && !row.detail().isBlank()) {
                text.append("  [").append(row.detail()).append(']');
            }
            if (row.note() != null && !row.note().isBlank()) {
                text.append("  (").append(row.note()).append(')');
            }
        }
        return text.toString();
    }

    public static String toCsv(RecipeFlowAnalyzer.Route route) {
        StringBuilder csv = new StringBuilder(
                "index,depth,chart,kind,id,elementId,label,detail,flow,note");
        for (RecipeFlowAnalyzer.Row row : route.rows()) {
            csv.append('\n');
            csv.append(row.index()).append(',')
                    .append(row.depth()).append(',')
                    .append(quote(row.chartPath())).append(',')
                    .append(row.kind()).append(',')
                    .append(quote(row.id())).append(',')
                    .append(quote(row.elementId())).append(',')
                    .append(quote(row.label())).append(',')
                    .append(quote(row.detail())).append(',')
                    .append(quote(row.flow())).append(',')
                    .append(quote(row.note()));
        }
        return csv.toString();
    }

    private static int branchIndent(String flow) {
        if (flow == null || flow.isBlank()) {
            return 0;
        }
        return Math.max(0, flow.split("\\.").length - 1);
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
