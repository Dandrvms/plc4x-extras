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
 * What a bar reads, how it compares it, and what it compares it against.
 * <p>
 * A bar between two steps exists to answer one question: may the flow cross? To answer it something
 * has to be read off the equipment and compared against something.
 * <p>
 * The text lives in {@link S88ProcedureTransition#getCondition()}. The condition of a transition is a
 * string, which is enough to hold a comparison. This is the form that string is taken apart into, so
 * that whoever draws the bar can show what it waits on and whoever runs the recipe can read it without
 * taking the text apart again.
 * <p>
 * <b>Only one comparison.</b> Two things that have to happen at once are two bars and a join, not two
 * comparisons joined by an "and" inside one bar.
 */
public final class S88ConditionExpression {

    /** What separates the address, the operator and the literal when the three are written together. */
    public static final char SEPARATOR = '#';

    private final S88VariableAddress variable;
    private final S88ConditionOperator operator;
    private final String literal;

    private S88ConditionExpression(S88VariableAddress variable, S88ConditionOperator operator,
                                   String literal) {
        this.variable = variable;
        this.operator = operator;
        this.literal = literal;
    }

    /**
     * A comparison of one variable against a literal.
     *
     * @param variable what to read, may not be {@code null}
     * @param operator how to compare, may not be {@code null}
     * @param literal  what to compare it against, may not be {@code null}
     * @return the comparison
     */
    public static S88ConditionExpression of(S88VariableAddress variable, S88ConditionOperator operator,
                                           String literal) {
        if (variable == null) {
            throw new IllegalArgumentException("A comparison has to say what it reads.");
        }
        if (operator == null) {
            throw new IllegalArgumentException("A comparison has to say how it compares.");
        }
        if (literal == null) {
            throw new IllegalArgumentException("A comparison has to say what it compares against.");
        }
        return new S88ConditionExpression(variable, operator, literal);
    }

    /**
     * Reads a comparison out of the text a recipe carries.
     * <p>
     * Returns {@code null} for text that is not a comparison. It shows the reader what was there.
     *
     * @param text the text, may be {@code null}
     * @return the comparison, or {@code null} when the text is not one
     */
    public static S88ConditionExpression parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int at = text.indexOf(SEPARATOR);
        if (at < 0) {
            return null;
        }
        S88VariableAddress variable = S88VariableAddress.parse(text.substring(0, at).trim());
        if (variable == null) {
            return null;
        }
        String rest = text.substring(at + 1);
        if (rest.isEmpty()) {
            return null;
        }

        int second = rest.indexOf(SEPARATOR);
        String operatorText = second < 0 ? rest : rest.substring(0, second);
        S88ConditionOperator operator = S88ConditionOperator.fromSymbol(operatorText);
        if (operator == null) {
            return null;
        }
        String literal = second < 0 ? "" : rest.substring(second + 1);
        if (literal.isEmpty()) {
            return null;
        }
        return new S88ConditionExpression(variable, operator, literal);
    }

    /** What the comparison reads. Never {@code null}. */
    public S88VariableAddress getVariable() {
        return variable;
    }

    /** How the two are compared. Never {@code null}. */
    public S88ConditionOperator getOperator() {
        return operator;
    }

    /** What the value is compared against, kept as written. Never {@code null}. */
    public String getLiteral() {
        return literal;
    }

    /** The comparison as the text a recipe carries. */
    public String toText() {
        return variable.toText() + SEPARATOR + operator.getSymbol() + SEPARATOR + literal;
    }

    /**
     * Whether this comparison reads the same address the text names.
     *
     * @param address address to ask about, may be {@code null}
     * @return true when this comparison reads that address
     */
    public boolean reads(S88VariableAddress address) {
        return variable.equals(address);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof S88ConditionExpression that)) {
            return false;
        }
        return variable.equals(that.variable) && operator == that.operator
                && literal.equals(that.literal);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(variable, operator, literal);
    }

    @Override
    public String toString() {
        return toText();
    }
}