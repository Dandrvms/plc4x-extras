/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.batch.simulator.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.core.BaseNameResolver;

/** Infers follow rules from the usual report and setpoint names of a module. */
public final class FollowRuleHeuristic {

    private static final List<String> REPORT_SUFFIXES = List.of("_ACTUAL", "_PV", "_ACT");
    private static final List<String> SETPOINT_SUFFIXES = List.of("_SP", "_SETPOINT", "_OBJETIVO");

    private FollowRuleHeuristic() {
        /* This utility class should not be instantiated */
    }

    public static List<FollowRule> infer(String moduleId, S88Element element) {
        if (element == null) {
            return List.of();
        }
        Map<String, String> reports = BaseNameResolver.resolveAll(element, S88PlantModel.REPORTS);
        Map<String, String> setpoints = BaseNameResolver.resolveAll(element, S88PlantModel.PARAMETERS);
        return infer(moduleId, reports.keySet(), setpoints.keySet());
    }

    public static List<FollowRule> infer(String moduleId, Collection<String> reportBases,
                                         Collection<String> setpointBases) {
        Map<String, String> reportsByPrefix = new LinkedHashMap<>();
        if (reportBases != null) {
            for (String report : reportBases) {
                String prefix = prefixOf(report, REPORT_SUFFIXES);
                if (prefix != null) {
                    reportsByPrefix.putIfAbsent(prefix, report);
                }
            }
        }
        List<FollowRule> rules = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        if (setpointBases != null) {
            for (String setpoint : setpointBases) {
                String prefix = prefixOf(setpoint, SETPOINT_SUFFIXES);
                if (prefix == null || !used.add(prefix)) {
                    continue;
                }
                String report = reportsByPrefix.get(prefix);
                if (report != null) {
                    rules.add(new FollowRule(moduleId, report, setpoint, 1.0, 0.5, 0.0));
                }
            }
        }
        return List.copyOf(rules);
    }

    public static Optional<FollowRule> between(String moduleId, String reportBase,
                                               String setpointBase) {
        String reportPrefix = prefixOf(reportBase, REPORT_SUFFIXES);
        String setpointPrefix = prefixOf(setpointBase, SETPOINT_SUFFIXES);
        if (reportPrefix == null || !reportPrefix.equals(setpointPrefix)) {
            return Optional.empty();
        }
        return Optional.of(new FollowRule(moduleId, reportBase, setpointBase, 1.0, 0.5, 0.0));
    }

    private static String prefixOf(String name, List<String> suffixes) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (upper.endsWith(suffix) && upper.length() > suffix.length()) {
                return upper.substring(0, upper.length() - suffix.length());
            }
        }
        return null;
    }
}
