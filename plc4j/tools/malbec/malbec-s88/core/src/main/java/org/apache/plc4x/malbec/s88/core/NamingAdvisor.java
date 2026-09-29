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
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Naming advice, the counterpart of {@link NameValidator}.
 * <p>
 * The two classes split on a single question: is a broken name something the model simply
 * cannot represent, or merely something the ISA-88 software model suggests?
 * <ul>
 *   <li>{@link NameValidator} <b>rejects</b>. An empty name, a name holding accents, spaces or
 *       other characters outside {@code A-Z 0-9 _}, or one over
 *       {@link NameValidator#MAX_LENGTH} characters produces a key the downstream consumers
 *       cannot accept, so there is nothing to negotiate about.</li>
 *   <li>This class <b>advises</b>. The structural convention that names an element after its
 *       parent is a guideline, not a constraint: plants imported from other tools, such as a
 *       Rockwell FT Batch export, routinely break it, and refusing to load them would be worse
 *       than loading them imperfectly.</li>
 * </ul>
 * Nothing here throws. Every method answers, so an editor can show the consequence of a name
 * while it is being typed and let the user decide, and a plant that predates any of these
 * conventions keeps loading.
 */
public final class NamingAdvisor {

    private NamingAdvisor() {
        /* This utility class should not be instantiated */
    }

    /**
     * Severity of an observation, kept so a caller can filter instead of parsing messages.
     */
    public enum Severity {
        /** Worth a look: the result works, but something may be surprising. */
        INFO,
        /** Something is already ambiguous or duplicated and may need a decision. */
        WARNING
    }

    /**
     * @param severity  how much attention the observation deserves
     * @param message   message to show the user
     * @param suggestion name offered as an alternative, {@code null} when nothing better is known
     */
    public record Advice(Severity severity, String message, String suggestion) {

        public static Advice info(String message) {
            return new Advice(Severity.INFO, message, null);
        }

        public static Advice info(String message, String suggestion) {
            return new Advice(Severity.INFO, message, suggestion);
        }

        public static Advice warning(String message) {
            return new Advice(Severity.WARNING, message, null);
        }

        public static Advice warning(String message, String suggestion) {
            return new Advice(Severity.WARNING, message, suggestion);
        }
    }

    /**
     * Everything an imported plant gets wrong with its variable names, gathered in one place so
     * that a caller can show it all at once instead of asking about each kind in turn.
     *
     * @param conflicts    variable names published by more than one property
     * @param overlong     variable names longer than {@link NameValidator#MAX_LENGTH}
     * @param duplicateIds element ids used by more than one element
     */
    public record ModelReport(List<VariableKeySupport.VariableKeyConflict> conflicts,
                              List<VariableKeySupport.VariableKey> overlong,
                              Set<String> duplicateIds) {

        public ModelReport {
            conflicts = List.copyOf(conflicts);
            overlong = List.copyOf(overlong);
            // A LinkedHashSet so that the report lists the ids in the order the model holds them,
            // which Set.copyOf would not guarantee.
            duplicateIds = Collections.unmodifiableSet(new LinkedHashSet<>(duplicateIds));
        }

        /**
         * Tells whether the plant carries anything at all worth telling the user about.
         *
         * @return {@code true} when at least one defect was found
         */
        public boolean hasProblems() {
            return !conflicts.isEmpty() || !overlong.isEmpty() || !duplicateIds.isEmpty();
        }
    }

    /**
     * Suggests the name an EquipmentModule would get if the structural convention were followed:
     * <code>{name}_{parentUnitId}</code>, for instance {@code CALENTAMIENTO_TANQUE_1}.
     * <p>
     * Only EquipmentModules take part. A ProcessCell or a Unit is named freely by the plant, and
     * appending its parent id to it would fight the user's own scheme without any downstream
     * benefit, since their variable keys do not read the parent id. The suggestion is also left
     * out when the last segment of the name already carries the unit's id, so a conforming name is
     * not told to append what it already has.
     *
     * @param element element being named, may be {@code null}
     * @param typedId the name the user typed, may be {@code null}
     * @return the advice, {@code null} when the element is not an EquipmentModule, when the typed
     *         name already follows the convention, or when there is no parent to derive it from
     */
    public static Advice suggestConventionalId(S88Element element, String typedId) {
        if (element == null || typedId == null || typedId.isBlank()) {
            return null;
        }
        if (element.getLevel() != S88Level.EQUIPMENTMODULE) {
            return null;
        }

        String normalized = typedId.trim().toUpperCase(Locale.ROOT);
        if (NameValidator.check(normalized, "Element ID").isPresent()) {
            // A name the blocking validator would reject needs fixing first, not a second
            // opinion on top of an invalid name.
            return null;
        }

        S88Element parent = element.getParent();
        if (parent == null || !isFilled(parent.getId())) {
            return null;
        }

        String parentId = parent.getId().trim().toUpperCase(Locale.ROOT);
        if (endsWith(normalized, parentId)) {
            return null;
        }

        String suggested = normalized + "_" + parentId;
        if (suggested.length() > NameValidator.MAX_LENGTH) {
            return Advice.warning("'" + typedId.trim() + "' does not follow the naming convention of the "
                    + "ISA-88 model, and appending the parent id would exceed "
                    + NameValidator.MAX_LENGTH + " characters. It will still be accepted.");
        }
        return Advice.warning("'" + typedId.trim() + "' does not follow the naming convention of the "
                + "ISA-88 model. You can keep it, or use the conventional name.", suggested);
    }

    /**
     * Suggests the conventional name for a variable, without ever applying it.
     * <p>
     * The convention qualifies a variable with the id of the element that owns it:
     * {@code TEMPERATURA_SP_CALENTAMIENTO_TANQUE_1} for a variable of an EquipmentModule and
     * {@code NIVEL_TANQUE_1} for a variable of a Unit. The suggestion is offered and the user decides,
     * because the name is theirs.
     * <p>
     * Nothing is suggested when the name already ends with the qualifying part, or when appending it
     * would not be a legal name.
     *
     * @param element   element that will own the variable, may be {@code null}
     * @param typedName name as typed, may be {@code null}
     * @return the advice carrying the conventional name, or {@code null} when there is nothing to
     *         suggest
     */
    public static Advice suggestConventionalVariableName(S88Element element, String typedName) {
        if (element == null || typedName == null || typedName.isBlank()) {
            return null;
        }
        String normalized = typedName.trim().toUpperCase(Locale.ROOT);
        if (NameValidator.check(normalized, "Name").isPresent()) {
            // A name the blocking validator would reject needs fixing first, not a second opinion
            // on top of an invalid name.
            return null;
        }

        String qualification = qualificationOf(element);
        if (endsWith(normalized, qualification)) {
            return null;
        }

        String suggested = normalized + "_" + qualification;
        if (suggested.length() > NameValidator.MAX_LENGTH) {
            return Advice.warning("'" + typedName.trim() + "' plus the conventional suffix would exceed "
                    + NameValidator.MAX_LENGTH + " characters. It will still be accepted as typed.");
        }
        return Advice.info("The conventional name is '" + suggested + "'. Press the button to use it, "
                + "or leave it as typed.", suggested);
    }

    /**
     * The part that identifies the variable beyond its own name: the id of the element that owns
     * it, which an EquipmentModule carries already fully qualified ({@code CALENTAMIENTO_TANQUE_1})
     * and a Unit carries as itself ({@code TANQUE_1}). Appending anything else would repeat the
     * element's own name, so nothing but the element id is ever suggested.
     */
    private static String qualificationOf(S88Element element) {
        return element.getId().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Reports that a variable name is already taken by another property.
     *
     * @param model plant to search, may be {@code null}
     * @param name  name the user is about to publish, may be {@code null}
     * @return the advice, {@code null} when the name is still free
     */
    public static Advice warnIfKeyTaken(S88PlantModel model, String name) {
        return warnIfKeyTaken(model, name, null, null);
    }

    /**
     * Reports that a variable name is already taken, ignoring one known property.
     * <p>
     * Editing a property is not a clash with itself: a property that already exists is in the model
     * publishing exactly the name being previewed. Without naming that property, every edit would
     * report the name as taken by itself, pointing at its own element, which is the one place a
     * clash is impossible.
     *
     * @param model           plant to search, may be {@code null}
     * @param name            name the user is about to publish, may be {@code null}
     * @param editingElement  element owning the property under edit, {@code null} to ignore nothing
     * @param editingProperty name of the property under edit, {@code null} to ignore nothing
     * @return the advice, {@code null} when no other property uses the name
     */
    public static Advice warnIfKeyTaken(S88PlantModel model, String name,
                                         S88Element editingElement, String editingProperty) {
        List<String> owners = VariableKeySupport.findOwnersOf(model, name, editingElement, editingProperty);
        if (owners.isEmpty()) {
            return null;
        }
        return Advice.warning("The variable name '" + name.trim().toUpperCase(Locale.ROOT)
                + "' is already used by " + owners.size() + " propert"
                + (owners.size() == 1 ? "y" : "ies") + ": " + String.join(", ", owners)
                + ". Change the name to keep both variables addressable.");
    }

    /**
     * Reports that a variable name would exceed the maximum accepted length.
     *
     * @param name name about to be published, may be {@code null}
     * @return the advice, {@code null} when the name fits
     */
    public static Advice warnIfKeyTooLong(String name) {
        if (name == null || name.length() <= NameValidator.MAX_LENGTH) {
            return null;
        }
        return Advice.warning("The variable name '" + name + "' is " + name.length() + " characters long, the "
                + "maximum is " + NameValidator.MAX_LENGTH + ". Use a shorter name so the variable "
                + "stays addressable.");
    }

    /**
     * Describes what a rename does to the variables of the element, so the user knows before
     * confirming.
     * <p>
     * Every variable of the subtree that follows the naming convention is re-suffixed: a variable
     * called {@code TEMPERATURA_SP_OLLA_1} of element {@code OLLA_1} is re-suffixed to
     * {@code TEMPERATURA_SP_OLLA_2} wherever the subtree publishes it, and the descendants whose
     * own id carries the element's id are renamed along. Hand-typed names never move.
     *
     * @param element element being renamed, may be {@code null}
     * @return the advice, {@code null} when the rename would not touch any variable
     */
    public static Advice describeRenameImpact(S88Element element) {
        if (element == null || element.getId() == null || element.getId().isBlank()) {
            return null;
        }
        String discriminator = "_" + element.getId().trim().toUpperCase(Locale.ROOT);
        List<VariableKeySupport.VariableKey> affected = new ArrayList<>();
        for (VariableKeySupport.VariableKey key : VariableKeySupport.listSubtree(element)) {
            if (key.key().endsWith(discriminator)) {
                affected.add(key);
            }
        }
        if (affected.isEmpty()) {
            return null;
        }
        String example = affected.getFirst().key();
        String base = example.endsWith(discriminator)
                ? example.substring(0, example.length() - discriminator.length()) : example;
        return Advice.info("'" + element.getId() + "' names " + affected.size() + " variable"
                + (affected.size() == 1 ? "" : "s") + " of its own and of its children after it,"
                + " such as '" + example + "'. Renaming the element re-suffixes those names with the"
                + " new id, e.g. '" + base + "_NEW_ID', and renames the elements below it that carry"
                + " the id too.");
    }

    /**
     * Inspects a whole plant and reports every defect that makes its variable names harder to
     * consume, so a model arriving from another tool can be shown what it carries before the
     * user starts working on it.
     * <p>
     * The three categories are independent and routinely coexist: one duplicated element id both
     * breaks the model index and is usually what makes several variables collide. Reporting the
     * ids separately is what lets the user see that single root cause instead of a long list of
     * collisions that all look unrelated.
     * <p>
     * Nothing here throws and nothing is repaired: the report is information, and a defective
     * plant still loads.
     *
     * @param model plant to inspect, may be {@code null}
     * @return the defects found, empty when the plant is clean
     */
    public static ModelReport describeModel(S88PlantModel model) {
        if (model == null) {
            return new ModelReport(List.of(), List.of(), Set.of());
        }
        return new ModelReport(VariableKeySupport.findConflicts(model),
                VariableKeySupport.findOverlongKeys(model),
                model.getDuplicateIds());
    }

    private static boolean endsWith(String value, String suffix) {
        return value.equals(suffix) || value.endsWith("_" + suffix);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
