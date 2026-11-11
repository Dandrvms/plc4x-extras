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
import java.util.List;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.batch.DemoFixtures;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTest {

    @Test
    void comparesNumbersWithEveryOperator() {
        VariableStore store = store();
        store.set("TEMPERATURA_ACTUAL", 72.0, 0.0);

        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.EQUALS, "72"), store));
        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.NOT_EQUALS, "73"), store));
        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.GREATER, "71"), store));
        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.GREATER_OR_EQUAL, "72"), store));
        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.LESS, "73"), store));
        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.LESS_OR_EQUAL, "72"), store));
        assertFalse(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.OTHER, "72"), store));
    }

    @Test
    void greaterOrEqualAlsoHoldsWhenTheValueIsPastTheLiteral() {
        VariableStore store = store();
        store.set("TEMPERATURA_ACTUAL", 102.0, 0.0);

        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.GREATER_OR_EQUAL, "100"), store));
        assertFalse(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.LESS_OR_EQUAL, "100"), store));
    }

    @Test
    void lessOrEqualAlsoHoldsWhenTheValueIsPastTheLiteral() {
        VariableStore store = store();
        store.set("TEMPERATURA_ACTUAL", 18.0, 0.0);

        assertTrue(eval(comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.LESS_OR_EQUAL, "20"), store));
    }

    @Test
    void comparesIntegersEnumerationsAndText() {
        VariableStore store = store();
        store.set("DURACION", 30.0, 0.0);
        store.set("STATE_CALENTADOR_1", "COMPLETE", 0.0);

        assertTrue(eval(comparison("DURACION", DataType.INTEGER,
                S88ConditionOperator.EQUALS, "30"), store));
        assertFalse(eval(comparison("DURACION", DataType.INTEGER,
                S88ConditionOperator.GREATER, "30"), store));
        assertTrue(eval(comparison("STATE_CALENTADOR_1", DataType.ENUMERATION,
                S88ConditionOperator.EQUALS, "COMPLETE"), store));
        assertTrue(eval(comparison("STATE_CALENTADOR_1", DataType.ENUMERATION,
                S88ConditionOperator.NOT_EQUALS, "IDLE"), store));
        assertFalse(eval(comparison("STATE_CALENTADOR_1", DataType.ENUMERATION,
                S88ConditionOperator.GREATER, "IDLE"), store));
        assertTrue(eval(comparison("STATE_CALENTADOR_1", DataType.STRING,
                S88ConditionOperator.EQUALS, "complete"), store));
    }

    @Test
    void evaluatesWaitsAndNestedConditions() {
        VariableStore store = store();
        store.set("TEMPERATURA_ACTUAL", 72.0, 0.0);
        store.set("STATE_CALENTADOR_1", "COMPLETE", 0.0);

        assertTrue(eval(new PreparedCondition.Wait(3), store, 10.0, 7.0));
        assertFalse(eval(new PreparedCondition.Wait(4), store, 10.0, 7.0));
        assertFalse(eval(new PreparedCondition.Wait(1), store, 10.0, Double.NaN));

        PreparedCondition temperature = comparison("TEMPERATURA_ACTUAL", DataType.REAL,
                S88ConditionOperator.GREATER_OR_EQUAL, "70");
        PreparedCondition complete = comparison("STATE_CALENTADOR_1", DataType.ENUMERATION,
                S88ConditionOperator.EQUALS, "COMPLETE");
        PreparedCondition nested = new PreparedCondition.All(List.of(temperature,
                new PreparedCondition.Any(List.of(new PreparedCondition.Wait(2), complete))));
        assertTrue(eval(nested, store, 10.0, 7.0));

        PreparedCondition never = new PreparedCondition.All(List.of(temperature,
                new PreparedCondition.Any(List.of(new PreparedCondition.Wait(5),
                        comparison("STATE_CALENTADOR_1", DataType.ENUMERATION,
                                S88ConditionOperator.EQUALS, "IDLE")))));
        assertFalse(eval(never, store, 10.0, 7.0));
    }

    @Test
    void aMissingVariableIsFalse() {
        assertFalse(eval(comparison("NOT_PUBLISHED", DataType.REAL,
                S88ConditionOperator.EQUALS, "1"), store()));
    }

    private static boolean eval(PreparedCondition condition, VariableStore store) {
        return eval(condition, store, 0.0, 0.0);
    }

    private static boolean eval(PreparedCondition condition, VariableStore store, double simTime,
                                double enabledSince) {
        return ConditionEvaluator.eval(condition, store, simTime, enabledSince);
    }

    private static PreparedCondition comparison(String variable, DataType type,
                                                S88ConditionOperator operator, String literal) {
        return new PreparedCondition.Comparison("CALENTADOR_1", variable, type, null,
                operator, literal, List.of());
    }

    private static VariableStore store() {
        return VariableStore.of(DemoFixtures.plant(), List.of("CALENTADOR_1"),
                new ArrayList<>());
    }
}
