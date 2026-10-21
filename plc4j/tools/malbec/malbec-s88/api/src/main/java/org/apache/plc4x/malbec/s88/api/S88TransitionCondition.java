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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a bar waits on as a whole: a comparison, several of them joined, or a wait for a time.
 * <p>
 * Kept in the transition as one string. {@link #toText()} is the parseable form and
 * {@link #toDisplay()} the sentence shown on the chart. In a joining, each comparison is wrapped in
 * {@code [...]} so that a literal holding a separator stays one operand.
 */
public final class S88TransitionCondition {

    /** What part of a condition this is. */
    public enum Kind {
        /** One comparison of a value read off equipment. */
        COMPARISON,
        /** A wait for a number of seconds, and nothing else. */
        WAIT,
        /** Every operand has to hold. */
        AND,
        /** At least one operand has to hold. */
        OR
    }

    private final Kind kind;
    private final S88ConditionExpression comparison;
    private final List<S88TransitionCondition> operands;
    private final int seconds;

    private S88TransitionCondition(Kind kind, S88ConditionExpression comparison,
                                   List<S88TransitionCondition> operands, int seconds) {
        this.kind = kind;
        this.comparison = comparison;
        this.operands = operands == null ? List.of() : List.copyOf(operands);
        this.seconds = seconds;
    }

    /**
     * A condition that is one comparison.
     *
     * @param comparison the comparison, may not be {@code null}
     */
    public static S88TransitionCondition comparison(S88ConditionExpression comparison) {
        if (comparison == null) {
            throw new IllegalArgumentException("A condition has to say what it compares.");
        }
        return new S88TransitionCondition(Kind.COMPARISON, comparison, List.of(), 0);
    }

    /**
     * A condition every one of whose operands has to hold.
     *
     * @param operands the operands, at least two
     */
    public static S88TransitionCondition and(List<S88TransitionCondition> operands) {
        return joining(Kind.AND, operands);
    }

    /**
     * A condition at least one of whose operands has to hold.
     *
     * @param operands the operands, at least two
     */
    public static S88TransitionCondition or(List<S88TransitionCondition> operands) {
        return joining(Kind.OR, operands);
    }

    private static S88TransitionCondition joining(Kind kind, List<S88TransitionCondition> operands) {
        if (operands == null || operands.size() < 2) {
            throw new IllegalArgumentException("A condition needs at least two things to join.");
        }
        List<S88TransitionCondition> checked = new ArrayList<>(operands.size());
        for (S88TransitionCondition operand : operands) {
            if (operand == null) {
                throw new IllegalArgumentException("A condition cannot join nothing.");
            }
            checked.add(operand);
        }
        return new S88TransitionCondition(kind, null, checked, 0);
    }

    /**
     * A condition that waits for a number of seconds.
     *
     * @param seconds how long to wait, which may not be negative
     */
    public static S88TransitionCondition wait(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("A wait cannot be for less than no time.");
        }
        return new S88TransitionCondition(Kind.WAIT, null, List.of(), seconds);
    }

    /**
     * Reads a condition out of the text a recipe carries.
     *
     * @param text the text, may be {@code null}
     * @return the condition, or {@code null} when the text says nothing or was not written this way
     */
    public static S88TransitionCondition parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        boolean joinsWithAnd = trimmed.startsWith("AND(") && trimmed.endsWith(")");
        boolean joinsWithOr = trimmed.startsWith("OR(") && trimmed.endsWith(")");
        if (joinsWithAnd || joinsWithOr) {
            int open = joinsWithAnd ? "AND(".length() : "OR(".length();
            List<S88TransitionCondition> operands =
                    parseOperands(trimmed.substring(open, trimmed.length() - 1));
            return operands == null ? null : joining(joinsWithAnd ? Kind.AND : Kind.OR, operands);
        }
        Integer waited = secondsOf(trimmed);
        if (waited != null) {
            return wait(waited);
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            S88ConditionExpression wrapped =
                    S88ConditionExpression.parse(trimmed.substring(1, trimmed.length() - 1).trim());
            return wrapped == null ? null : comparison(wrapped);
        }
        S88ConditionExpression single = S88ConditionExpression.parse(trimmed);
        return single == null ? null : comparison(single);
    }

    private static Integer secondsOf(String trimmed) {
        if (!trimmed.startsWith("WAIT(") || !trimmed.endsWith(")")) {
            return null;
        }
        String inner = trimmed.substring("WAIT(".length(), trimmed.length() - 1).trim();
        try {
            return Integer.valueOf(inner);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static List<S88TransitionCondition> parseOperands(String inner) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int at = 0; at < inner.length(); at++) {
            char c = inner.charAt(at);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
                if (depth < 0) {
                    return null;
                }
            } else if (c == ';' && depth == 0) {
                parts.add(inner.substring(start, at));
                start = at + 1;
            }
        }
        if (depth != 0) {
            return null;
        }
        parts.add(inner.substring(start));
        if (parts.size() < 2) {
            return null;
        }
        List<S88TransitionCondition> operands = new ArrayList<>(parts.size());
        for (String part : parts) {
            S88TransitionCondition operand = parse(part);
            if (operand == null) {
                return null;
            }
            operands.add(operand);
        }
        return operands;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * The comparison this condition is, when it is one.
     *
     * @return the comparison, or {@code null} when this is a wait or a joining
     */
    public S88ConditionExpression getComparison() {
        return comparison;
    }

    /**
     * The conditions this one joins.
     *
     * @return the operands, empty when this is a comparison or a wait
     */
    public List<S88TransitionCondition> getOperands() {
        return operands;
    }

    /** How long this condition waits; only meaningful when it is a wait. */
    public int getSeconds() {
        return seconds;
    }

    /** The condition as the text a recipe carries. */
    public String toText() {
        return switch (kind) {
            case COMPARISON -> comparison.toText();
            case WAIT -> "WAIT(" + seconds + ")";
            case AND -> "AND(" + joinedText() + ")";
            case OR -> "OR(" + joinedText() + ")";
        };
    }

    private String joinedText() {
        List<String> parts = new ArrayList<>(operands.size());
        for (S88TransitionCondition operand : operands) {
            parts.add(operand.operandText());
        }
        return String.join(";", parts);
    }

    private String operandText() {
        return kind == Kind.COMPARISON ? "[" + comparison.toText() + "]" : toText();
    }

    /** The condition the way a person reads it, for showing on the chart. */
    public String toDisplay() {
        return switch (kind) {
            case COMPARISON -> comparison.toDisplay();
            case WAIT -> "WAIT " + seconds + " s";
            case AND -> joinedDisplay(" AND ");
            case OR -> joinedDisplay(" OR ");
        };
    }

    private String joinedDisplay(String joiner) {
        List<String> parts = new ArrayList<>(operands.size());
        for (S88TransitionCondition operand : operands) {
            boolean needsBrackets = (operand.kind == Kind.AND || operand.kind == Kind.OR)
                    && operand.kind != kind;
            parts.add(needsBrackets ? "(" + operand.toDisplay() + ")" : operand.toDisplay());
        }
        return String.join(joiner, parts);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof S88TransitionCondition that)) {
            return false;
        }
        return kind == that.kind
                && seconds == that.seconds
                && Objects.equals(comparison, that.comparison)
                && operands.equals(that.operands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, comparison, operands, seconds);
    }

    @Override
    public String toString() {
        return toText();
    }
}
