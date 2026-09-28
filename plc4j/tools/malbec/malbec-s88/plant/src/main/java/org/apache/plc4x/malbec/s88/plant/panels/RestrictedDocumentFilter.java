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
package org.apache.plc4x.malbec.s88.plant.panels;

import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.core.NameValidator;

/**
 * The one text filter used by the plant editor dialogs.
 * <p>
 * Two kinds of field share it: numeric or free text values, whose only rule is the data type, and
 * identifiers, whose charset is the one {@link NameValidator} enforces. Identifiers are upper cased
 * as they are typed and reject anything else, so an id that the use cases would refuse is never
 * typed in the first place. Programmatic writes are untouched: {@code setText} and
 * {@code replace} bypass the filter, which is what lets already stored values load unchanged.
 */
final class RestrictedDocumentFilter extends DocumentFilter {

    /** Told about every rejected keystroke so the dialog can say why nothing happened. */
    interface RejectionListener {
        /**
         * @param message human readable reason, already naming the offending character
         */
        void rejected(String message);
    }

    private static final String RULE = "Only A-Z, 0-9 and _ can be used.";

    private DataType type = DataType.STRING;
    private final boolean identifier;
    private final int maxLength;
    private final RejectionListener listener;

    private RestrictedDocumentFilter(boolean identifier, int maxLength, RejectionListener listener) {
        this.identifier = identifier;
        this.maxLength = maxLength;
        this.listener = listener;
    }

    /**
     * Filter for a value field: unrestricted unless the type is numeric.
     */
    static RestrictedDocumentFilter forValue(RejectionListener listener) {
        return new RestrictedDocumentFilter(false, Integer.MAX_VALUE, listener);
    }

    /**
     * Filter for an identifier field: upper-cased, charset limited and capped in length.
     */
    static RestrictedDocumentFilter forIdentifier(RejectionListener listener) {
        return new RestrictedDocumentFilter(true, NameValidator.MAX_LENGTH, listener);
    }

    void setType(DataType type) {
        this.type = type;
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
            throws BadLocationException {
        String effective = normalize(text);
        if (accept(fb, offset, 0, effective)) {
            super.insertString(fb, offset, effective, attrs);
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        String effective = normalize(text);
        if (accept(fb, offset, length, effective)) {
            super.replace(fb, offset, length, effective, attrs);
        }
    }

    private String normalize(String text) {
        return identifier && text != null ? text.toUpperCase(Locale.ROOT) : text;
    }

    private boolean accept(FilterBypass fb, int offset, int length, String text) throws BadLocationException {
        if (text == null || text.isEmpty()) {
            return true;
        }
        Document document = fb.getDocument();
        String current = document.getText(0, document.getLength());
        String proposed = new StringBuilder(current).replace(offset, offset + length, text).toString();
        return identifier ? acceptIdentifier(proposed) : acceptValue(proposed);
    }

    private boolean acceptIdentifier(String proposed) {
        for (int i = 0; i < proposed.length(); i++) {
            char c = proposed.charAt(i);
            if (!isIdentifierChar(c)) {
                report(describeChar(c) + " is not allowed. " + RULE);
                return false;
            }
        }
        if (proposed.length() > maxLength) {
            report("A name cannot be longer than " + maxLength + " characters.");
            return false;
        }
        return true;
    }

    private static boolean isIdentifierChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }

    private boolean acceptValue(String proposed) {
        if (type != DataType.INTEGER && type != DataType.REAL) {
            return true;
        }
        boolean ok = type == DataType.REAL
                ? proposed.matches("-?\\d*\\.?\\d*")
                : proposed.matches("-?\\d*");
        if (!ok) {
            report("'" + describeType() + "' does not accept that value.");
        }
        return ok;
    }

    private String describeType() {
        return type == DataType.REAL ? "a real number" : "an integer";
    }

    private static String describeChar(char c) {
        if (Character.isWhitespace(c)) {
            return "A space";
        }
        if (Character.isLetterOrDigit(c)) {
            return "The character '" + c + "'";
        }
        return "The character '" + c + "'";
    }

    /**
     * A filter runs while the document is locked, so the notice is handed to the event queue
     * instead of touching Swing straight away.
     */
    private void report(String message) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.rejected(message));
        }
    }
}
