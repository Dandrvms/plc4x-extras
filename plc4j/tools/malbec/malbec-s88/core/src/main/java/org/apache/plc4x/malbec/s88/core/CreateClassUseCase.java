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
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.Map;

public class CreateClassUseCase {
    private CreateClassUseCase() {
        /* This utility class should not be instantiated */
    }

    public static void execute(S88PlantModel model, S88Element parent, String name, Map<String, Object> properties) {
        NameValidator.validate(name, "Class name");

        if (name.startsWith(S88PlantModel.ENUM_CLASS_PREFIX)) {
            throw new IllegalArgumentException("'" + S88PlantModel.ENUM_CLASS_PREFIX
                    + "' is a reserved prefix for global enumerations.");
        }

        if (model.getClasses().containsKey(name)) {
            throw new IllegalStateException("Class with ID '" + name + "' already exists.");
        }

        S88Element targetParent = parent != null ? parent : model.getRoot();

        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName(name);

        // The target level is what makes the class offerable to every element creating children of
        // that level, so it has to be set on every path. With no parent the class lands on the root,
        // which means it is a class of the root's children. Leaving it unset would file a class that
        // no element could ever pick, invisible in every list and unusable forever.
        S88Level targetLevel;
        if (parent != null) {
            targetLevel = parent.getLevel() != null ? parent.getLevel().getChildLevel() : null;
        } else {
            S88Element root = model.getRoot();
            targetLevel = root != null && root.getLevel() != null ? root.getLevel().getChildLevel() : null;
        }
        if (targetLevel == null) {
            throw new IllegalStateException("Cannot create a class under '"
                    + (parent != null ? parent.getLevel() : model.getRoot() != null ? model.getRoot().getLevel() : "unknown")
                    + "' (leaf level).");
        }
        elementClass.setTargetLevel(targetLevel);


        if (properties != null) {
            for (String key : properties.keySet()) {
                elementClass.setProperty(key, properties.get(key));
            }
        }

        targetParent.addElementClass(elementClass);
        model.registerClass(elementClass);
    }
    
}
