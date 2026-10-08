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
package org.apache.plc4x.malbec.s88.recipes.panels;

/**
 * A change the recipe would not take.
 * <p>
 * Carries what went wrong in a form that can be shown to the operator: an exception from a button
 * press otherwise reaches nobody at all, and the operator is left with a button that did nothing
 * and no reason why.
 */
public class RecipeEditException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String editName;

    /**
     * @param editName how the edit was named, such as "Add step"
     * @param cause    what went wrong, which has the detail
     */
    public RecipeEditException(String editName, RuntimeException cause) {
        super(editName + " could not be done: " + reason(cause), cause);
        this.editName = editName;
    }

    /**
     * @return how the edit was named, for a message
     */
    public String getEditName() {
        return editName;
    }

    /**
     * What a failure says, without dragging in the stack trace the operator cannot read.
     */
    private static String reason(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }
}
