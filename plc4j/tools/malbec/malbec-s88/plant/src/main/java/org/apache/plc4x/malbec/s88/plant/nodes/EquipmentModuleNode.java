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
package org.apache.plc4x.malbec.s88.plant.nodes;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.netbeans.api.project.Project;
import org.openide.nodes.Children;

/**
 * Specialized node for ISA-88 Equipment Module.
 */
public class EquipmentModuleNode extends PlantElementNode {

    public EquipmentModuleNode(Project project, S88Element element) {
        super(project, element);
        this.setChildren(Children.LEAF);
    }

    @Override
    protected String getDefaultIconResource() {
        return "org/apache/plc4x/malbec/s88/plant/nodes/EquipmentModule.png";
    }

    // A module takes no children, so it keeps the actions every element has - including Duplicate,
    // which fits a module into a unit - and none of the ones that would create something under it.
    // hasChildren() is what draws that line now that the level filter lives in the parent.
    @Override
    protected boolean hasChildren() {
        return false;
    }
}
