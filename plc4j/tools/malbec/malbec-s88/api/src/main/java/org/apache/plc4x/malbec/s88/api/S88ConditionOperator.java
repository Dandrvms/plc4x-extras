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
package org.apache.plc4x.malbec.s88.api;

/**
 * How two values are compared when a bar says whether the flow may cross it.
 * <p>
 * The text of the comparison is what travels in the recipe, and it is written the way a person
 * writes it: what to read, how to compare it, and what to compare it against. These are the six
 * ways it can be done. The last is for anything else, and it exists because a recipe written
 * elsewhere may have used a comparison that is not on this list.
 */
public enum S88ConditionOperator {
    /** Equal to. The usual way to say the process has got there. */
    EQUALS("="),
    /** Not equal to. */
    NOT_EQUALS("<>"),
    /** Greater than. */
    GREATER(">"),
    /** Greater than or equal to. */
    GREATER_OR_EQUAL(">="),
    /** Less than. */
    LESS("<"),
    /** Less than or equal to. */
    LESS_OR_EQUAL("<="),
    /** A comparison this list does not name. */
    OTHER("");

    private final String symbol;

    S88ConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    /** The symbol as it is written in the recipe. Empty for {@link #OTHER}. */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Reads an operator from the symbol a recipe carries.
     * <p>
     * The two-character symbols are looked for first, so that {@code >=} is not read as a
     * greater-than followed by something. Which is exactly the mistake a handwritten recipe would
     * otherwise turn an "at least" into a "more than" without anybody noticing.
     *
     * @param symbol text to read, may be {@code null}
     * @return the operator, or {@code null} when the text names none
     */
    public static S88ConditionOperator fromSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        String trimmed = symbol.trim();
        for (S88ConditionOperator operator : values()) {
            if (operator != OTHER && operator.symbol.equals(trimmed)) {
                return operator;
            }
        }
        return null;
    }
}