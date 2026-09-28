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
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

/**
 * Use Case for renaming an S88 Element.
 */
public class RenameElementUseCase {
    private RenameElementUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Reports what a rename would do to the derived variable keys, without performing it.
     * <p>
     * Keys are derived from the element names, so renaming a Unit silently changes the key of
     * every EquipmentModule below it, while renaming an EquipmentModule changes its own. The
     * caller is expected to show this to the user and let them confirm. See {@link NamingAdvisor#describeRenameImpact}.
     *
     * @param element element about to be renamed
     * @return the advice, {@code null} when the element publishes no variable
     */
    public static NamingAdvisor.Advice impactOf(S88Element element) {
        return NamingAdvisor.describeRenameImpact(element);
    }

    public static void execute(S88PlantModel model, S88Element element, String newId) {
        NameValidator.validate(newId, "Element ID");

        if (model.findById(newId).isPresent()) {
            throw new IllegalStateException("Element with ID '" + newId + "' already exists.");
        }

        element.setId(newId);

        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.RELOADED, element));
    }
}
