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

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;

/** Evaluates a prepared transition condition against the current values. */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
        /* This utility class should not be instantiated */
    }

    public static boolean eval(PreparedCondition condition, VariableStore store, double simTime,
                               double enabledSince) {
        if (condition == null || store == null) {
            return false;
        }
        if (condition instanceof PreparedCondition.Wait wait) {
            return !Double.isNaN(enabledSince) && simTime - enabledSince >= wait.seconds();
        }
        if (condition instanceof PreparedCondition.StepComplete) {
            return true;
        }
        if (condition instanceof PreparedCondition.All all) {
            for (PreparedCondition operand : all.operands()) {
                if (!eval(operand, store, simTime, enabledSince)) {
                    return false;
                }
            }
            return true;
        }
        if (condition instanceof PreparedCondition.Any any) {
            for (PreparedCondition operand : any.operands()) {
                if (eval(operand, store, simTime, enabledSince)) {
                    return true;
                }
            }
            return false;
        }
        if (condition instanceof PreparedCondition.Comparison comparison) {
            return compare(comparison, store);
        }
        return false;
    }

    private static boolean compare(PreparedCondition.Comparison comparison, VariableStore store) {
        Object value = store.value(comparison.variable());
        if (value == null) {
            return false;
        }
        DataType type = comparison.dataType();
        if (type == DataType.INTEGER || type == DataType.REAL) {
            Double left = number(value);
            Double right = number(comparison.literal());
            return left != null && right != null
                    && compareNumbers(left, right, comparison.operator());
        }
        String left = text(value);
        String right = comparison.literal();
        if (left == null || right == null) {
            return false;
        }
        return switch (comparison.operator()) {
            case EQUALS -> left.equalsIgnoreCase(right);
            case NOT_EQUALS -> !left.equalsIgnoreCase(right);
            default -> false;
        };
    }

    private static boolean compareNumbers(double left, double right, S88ConditionOperator operator) {
        return switch (operator) {
            case EQUALS -> Double.compare(left, right) == 0;
            case NOT_EQUALS -> Double.compare(left, right) != 0;
            case GREATER -> left > right;
            case GREATER_OR_EQUAL -> left >= right;
            case LESS -> left < right;
            case LESS_OR_EQUAL -> left <= right;
            case OTHER -> false;
        };
    }

    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof Enum<?> enumeration ? enumeration.name() : value.toString();
    }
}
