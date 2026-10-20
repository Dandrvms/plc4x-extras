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

import java.util.Arrays;
import java.util.List;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;

/** Charts built by hand, with every line written out. */
final class ChartFixtures {

    private ChartFixtures() {
    }

    static S88MasterRecipe master(String... stepIds) {
        S88MasterRecipe recipe = new S88MasterRecipe("R", S88RecipeKind.CLASS);
        recipe.addRecipeElement(new S88RecipeElement(
                CreateMasterRecipeUseCase.BEGIN_ID, S88RecipeElementKind.BEGIN));
        recipe.addRecipeElement(new S88RecipeElement(
                CreateMasterRecipeUseCase.END_ID, S88RecipeElementKind.END));
        for (String id : stepIds) {
            recipe.addRecipeElement(new S88RecipeElement(id, S88RecipeElementKind.OPERATION));
        }
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep(
                CreateMasterRecipeUseCase.BEGIN_ID, CreateMasterRecipeUseCase.BEGIN_ID));
        chart.addStep(new S88ProcedureStep(
                CreateMasterRecipeUseCase.END_ID, CreateMasterRecipeUseCase.END_ID));
        for (String id : stepIds) {
            chart.addStep(new S88ProcedureStep(id, id));
        }
        recipe.setProcedureLogic(chart);
        return recipe;
    }

    static void step(S88ProcedureLogic chart, String id) {
        chart.addStep(new S88ProcedureStep(id, id));
    }

    static void transition(S88ProcedureLogic chart, String id) {
        chart.addTransition(new S88ProcedureTransition(id, null));
    }

    static S88IdRef step(String id) {
        return S88IdRef.step(id);
    }

    static S88IdRef transition(String id) {
        return S88IdRef.transition(id);
    }

    static void link(S88ProcedureLogic chart, String id, S88IdRef from, S88IdRef to) {
        link(chart, id, List.of(from), List.of(to), S88LinkType.CONTROL_LINK);
    }

    static void link(S88ProcedureLogic chart, String id, List<S88IdRef> from, List<S88IdRef> to,
                     S88LinkType type) {
        S88ProcedureLink link = new S88ProcedureLink(id);
        from.forEach(link::addFrom);
        to.forEach(link::addTo);
        link.setLinkType(type);
        chart.addLink(link);
    }

    static List<S88IdRef> steps(String... ids) {
        return Arrays.stream(ids).map(S88IdRef::step).toList();
    }

    static List<S88IdRef> transitions(String... ids) {
        return Arrays.stream(ids).map(S88IdRef::transition).toList();
    }
}
