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

import java.util.List;

/** Reserved for the simulator. */
public interface SimulationDecisionProvider {

    /**
     * Chooses one of the transitions a step can take.
     *
     * @param stepId              step that has to choose
     * @param enabledTransitions transitions the step can take
     * @return the chosen transition
     */
    String chooseBranch(String stepId, List<String> enabledTransitions);

    /**
     * Says whether a transition fires.
     *
     * @param transitionId transition to evaluate
     * @return whether the transition fires
     */
    boolean evaluate(String transitionId);
}
