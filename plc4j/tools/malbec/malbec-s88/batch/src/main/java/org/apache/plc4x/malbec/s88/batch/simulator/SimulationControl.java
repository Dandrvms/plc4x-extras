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
package org.apache.plc4x.malbec.s88.batch.simulator;

import org.apache.plc4x.malbec.s88.batch.model.DecisionMode;

/**
 * Controls the simulated plant. It is available only from a simulated session.
 */
public interface SimulationControl {

    void setSpeed(double factor);

    void play();

    /** Stops the simulated clock; it is not a PAUSE command of the batch. */
    void pauseClock();

    void stepTicks(int ticks);

    /** Advances until the next significant change, at most ten thousand ticks. */
    void stepToNextEvent();

    boolean isClockRunning();

    double speed();

    /** Enables or disables the automatic process rules; the clock is separate. */
    void setAutomatic(boolean automatic);

    /** Completes the phase running on the given module, as an operator confirmation. */
    void completeStep(String moduleId);

    void setVariable(String publishedName, Object value);

    void injectFailure(String moduleId, int code);

    void clearFailure(String moduleId);

    /** Simulates loss of the link between the runtime and the plant. */
    void setLinkUp(boolean up);

    void setDecisionMode(DecisionMode mode);

    void decide(String transitionId);
}
