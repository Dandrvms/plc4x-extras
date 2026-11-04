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
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;

/** A transition condition with its variables resolved against the plant. */
public sealed interface PreparedCondition {

    /** One comparison of a module variable against a literal. */
    record Comparison(String moduleId, String variable, DataType dataType, String unit,
                      S88ConditionOperator operator, String literal,
                      List<String> enumerationValues) implements PreparedCondition {

        public Comparison {
            enumerationValues = enumerationValues == null ? List.of() : List.copyOf(enumerationValues);
        }
    }

    /** A wait for a number of simulated seconds. */
    record Wait(int seconds) implements PreparedCondition {
    }

    /** Wait until the steps before the transition have completed. */
    record StepComplete() implements PreparedCondition {
    }

    /** Every operand has to hold. */
    record All(List<PreparedCondition> operands) implements PreparedCondition {

        public All {
            operands = List.copyOf(operands);
        }
    }

    /** At least one operand has to hold. */
    record Any(List<PreparedCondition> operands) implements PreparedCondition {

        public Any {
            operands = List.copyOf(operands);
        }
    }
}
