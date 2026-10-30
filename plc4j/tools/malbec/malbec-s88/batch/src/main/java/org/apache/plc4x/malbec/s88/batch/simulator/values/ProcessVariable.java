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

import java.util.List;
import org.apache.plc4x.malbec.s88.api.DataType;

/**
 * One variable of the simulated plant.
 *
 * @param value the value as the store holds it, {@code null} when it has none
 * @param lastChangeSimTime simulated time of the last value change
 */
public record ProcessVariable(String name, Object value, DataType type, String unit,
                              double lastChangeSimTime, List<String> enumerationValues) {

    public ProcessVariable {
        enumerationValues = enumerationValues == null ? List.of() : List.copyOf(enumerationValues);
    }

    public ProcessVariable withValue(Object newValue, double simTime) {
        return new ProcessVariable(name, newValue, type, unit, simTime, enumerationValues);
    }
}
