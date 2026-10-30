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
package org.apache.plc4x.malbec.s88.batch.model;

/**
 * A report that follows a setpoint at a limited rate.
 *
 * @param moduleId       module the rule belongs to, {@code null} for any module that publishes both variables
 * @param reportBase     base name of the reported variable
 * @param setpointBase   base name of the setpoint variable
 * @param ratePerSecond  largest change per simulated second
 * @param tolerance      distance from the setpoint that counts as reached
 * @param initial        value the report starts from when the module has no value
 */
public record FollowRule(String moduleId, String reportBase, String setpointBase,
                         double ratePerSecond, double tolerance, double initial) {

    public FollowRule {
        if (reportBase == null || reportBase.isBlank()) {
            throw new IllegalArgumentException("A follow rule has to name the report it reads.");
        }
        if (setpointBase == null || setpointBase.isBlank()) {
            throw new IllegalArgumentException("A follow rule has to name the setpoint it follows.");
        }
        if (ratePerSecond < 0) {
            throw new IllegalArgumentException("A follow rule cannot move at a negative rate.");
        }
        if (tolerance < 0) {
            throw new IllegalArgumentException("A follow rule cannot have a negative tolerance.");
        }
        reportBase = reportBase.trim();
        setpointBase = setpointBase.trim();
        moduleId = moduleId == null || moduleId.isBlank() ? null : moduleId.trim();
    }

    /** A rule for any module, moving at one unit per second and accepting half a unit. */
    public FollowRule(String reportBase, String setpointBase) {
        this(null, reportBase, setpointBase, 1.0, 0.5, 0.0);
    }
}
