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
 * has to be read off a piece of equipment and compared against something.
 * <p>
 * The text lives in {@link S88ProcedureTransition#getCondition()}. The condition of a transition is a
 * string, which is enough to hold a comparison. This is the form that string is taken apart into, so
 * that whoever draws the bar can show what it waits on and whoever runs the recipe can read it without
 * taking the text apart again.
 * <p>
 * <b>Only one comparison.</b> Two things that have to happen at once are two bars and a join, not two
 * comparisons joined by an "and" inside one bar.
 * <p>
 * <b>The equipment is written in front of the address, and only a recipe for particular equipment
 * writes one.</b> A recipe written by class names base names, which some piece of equipment is
 * resolved against later, and there is nothing to name yet. A recipe written for particular equipment
 * reads a particular piece of equipment, so the text says which one: two of them can publish a
 * variable of the same name, and reading the one off the other is a recipe that does the wrong thing.
 */
public final class S88ConditionExpression {

    /** What separates the equipment, the address, the operator and the literal. */
    public static final char SEPARATOR = '#';

    private final String equipment;
    private final S88VariableAddress variable;
    private final S88ConditionOperator operator;
    private final String literal;

    private S88ConditionExpression(String equipment, S88VariableAddress variable,
                                   S88ConditionOperator operator, String literal) {
        this.equipment = equipment;
        this.variable = variable;
        this.operator = operator;
        this.literal = literal;
    }

    /**
     * A comparison of one variable against a literal, naming no equipment.
     *
     * @param variable what to read, may not be {@code null}
     * @param operator how to compare, may not be {@code null}
     * @param literal  what to compare it against, may not be {@code null}
     * @return the comparison
     */
    public static S88ConditionExpression of(S88VariableAddress variable, S88ConditionOperator operator,
                                            String literal) {
        return on(null, variable, operator, literal);
    }

    /**
     * A comparison of one variable of one piece of equipment against a literal.
     *
     * @param equipment the equipment the variable is read off, {@code null} or empty for a base name
     *                  that will be resolved against equipment later
     * @param variable  what to read, may not be {@code null}
     * @param operator  how to compare, may not be {@code null}
     * @param literal   what to compare it against, may not be {@code null}
     * @return the comparison
     */
    public static S88ConditionExpression on(String equipment, S88VariableAddress variable,
                                            S88ConditionOperator operator, String literal) {
        if (variable == null) {
            throw new IllegalArgumentException("A comparison has to say what it reads.");
        }
        if (operator == null) {
            throw new IllegalArgumentException("A comparison has to say how it compares.");
        }
        if (literal == null) {
            throw new IllegalArgumentException("A comparison has to say what it compares against.");
        }
        String named = equipment == null || equipment.isBlank() ? null : equipment.trim();
        return new S88ConditionExpression(named, variable, operator, literal);
    }

    /**
     * Reads a comparison out of the text a recipe carries.
     * <p>
     * The text is three parts, or four when it names the equipment it reads off.
     *
     * @param text the text, may be {@code null}
     * @return the comparison, or {@code null} when the text is not one
     */
    public static S88ConditionExpression parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = split(text);
        if (parts.length < 3 || parts.length > 4) {
            return null;
        }
        String equipment = null;
        String addressText = parts[0];
        String operatorText = parts[parts.length - 2];
        String literal = parts[parts.length - 1];
        if (parts.length == 4) {
            equipment = parts[0].trim();
            addressText = parts[1];
            if (equipment.isEmpty()) {
                return null;
            }
        }

        S88VariableAddress variable = S88VariableAddress.parse(addressText.trim());
        if (variable == null) {
            return null;
        }
        S88ConditionOperator operator = S88ConditionOperator.fromSymbol(operatorText);
        if (operator == null || literal.isEmpty()) {
            return null;
        }
        return new S88ConditionExpression(equipment, variable, operator, literal);
    }

    /**
     * The text taken apart at the separators, keeping anything after the fourth as it was.
     * <p>
     * A literal is written by a person and may hold a separator, so the parts after the one that
     * says which piece of equipment are not divided any further.
     */
    private static String[] split(String text) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder rest = new StringBuilder(text);
        int at = rest.indexOf(String.valueOf(SEPARATOR));
        while (at >= 0 && parts.size() < 3) {
            parts.add(rest.substring(0, at));
            rest.delete(0, at + 1);
            at = rest.indexOf(String.valueOf(SEPARATOR));
        }
        parts.add(rest.toString());
        return parts.toArray(new String[0]);
    }

    /**
     * The equipment the variable is read off, {@code null} for a base name that will be resolved
     * against equipment later.
     */
    public String getEquipment() {
        return equipment;
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
        String read = equipment == null
                ? variable.toText()
                : equipment + SEPARATOR + variable.toText();
        return read + SEPARATOR + operator.getSymbol() + SEPARATOR + literal;
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
        return java.util.Objects.equals(equipment, that.equipment)
                && variable.equals(that.variable)
                && operator == that.operator
                && literal.equals(that.literal);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(equipment, variable, operator, literal);
    }

    @Override
    public String toString() {
        return toText();
    }
}