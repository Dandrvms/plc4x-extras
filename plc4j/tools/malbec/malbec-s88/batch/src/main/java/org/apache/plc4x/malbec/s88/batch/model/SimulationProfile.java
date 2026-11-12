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

import java.util.List;

/**
 * Timing and process values used by the simulated PLC.
 *
 * @param transientTicks ticks a transient module state lasts
 * @param tickSeconds    simulated seconds advanced by one tick
 * @param followRules    rules that move reported values towards setpoints
 */
public record SimulationProfile(int transientTicks, double tickSeconds,
                                List<FollowRule> followRules) {

    public SimulationProfile {
        if (transientTicks < 0) {
            throw new IllegalArgumentException("A transient state cannot last a negative number of ticks.");
        }
        if (tickSeconds <= 0) {
            throw new IllegalArgumentException("A tick has to advance more than no simulated time.");
        }
        followRules = followRules == null ? List.of() : List.copyOf(followRules);
    }

    /** The profile used when the batch request names none. */
    public static SimulationProfile defaults() {
        return new SimulationProfile(3, 0.1, List.of());
    }

    public SimulationProfile withFollowRules(List<FollowRule> rules) {
        return new SimulationProfile(transientTicks, tickSeconds, rules);
    }
}
