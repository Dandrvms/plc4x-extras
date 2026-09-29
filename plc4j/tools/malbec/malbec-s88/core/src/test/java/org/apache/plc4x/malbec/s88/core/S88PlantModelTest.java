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
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.Test;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S88PlantModelTest {

    @Test
    void registerClassAllowsNewNames() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88ElementClass ec = new S88ElementClass();
        ec.setName("MotorClass");

        model.registerClass(ec);

        assertSame(ec, model.findClass("MotorClass"));
    }

    @Test
    void registerClassThrowsOnDuplicateName() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88ElementClass first = new S88ElementClass();
        first.setName("MotorClass");
        model.registerClass(first);

        S88ElementClass second = new S88ElementClass();
        second.setName("MotorClass");

        assertThrows(IllegalStateException.class, () -> model.registerClass(second));
    }

    @Test
    void registerClassThrowsOnNullOrEmptyName() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88ElementClass ec = new S88ElementClass();

        assertThrows(IllegalArgumentException.class, () -> model.registerClass(ec));
        assertThrows(IllegalArgumentException.class, () -> model.registerClass(null));
    }

    @Test
    void registerEnumerationAllowsNewNames() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88Enumeration e = new S88Enumeration("EnumA");
        e.setValue("X", 0);
        e.setValue("Y", 1);

        model.registerEnumeration(e);

        assertNotNull(model.findEnumeration("EnumA"));
    }

    @Test
    void registerEnumerationThrowsOnDuplicateName() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        model.registerEnumeration(new S88Enumeration("EnumA"));

        assertThrows(IllegalStateException.class,
                () -> model.registerEnumeration(new S88Enumeration("EnumA")));
    }

    @Test
    void registerEnumerationThrowsOnNullOrEmptyName() {
        S88PlantModel model = new S88PlantModel(new S88Element());

        assertThrows(IllegalArgumentException.class, () -> model.registerEnumeration(null));
        assertThrows(IllegalArgumentException.class, () -> model.registerEnumeration(new S88Enumeration("")));
    }

    @Test
    void duplicatedIdIsReportedAndDoesNotBreakTheLoad() {
        S88Element root = new S88Element().setId("PLANTA");
        S88Element first = new S88Element().setId("TANQUE_1");
        S88Element second = new S88Element().setId("TANQUE_1");
        root.addChild(first);
        root.addChild(second);

        S88PlantModel model = new S88PlantModel(root);

        assertEquals(java.util.Set.of("TANQUE_1"), model.getDuplicateIds());
    }

    @Test
    void soundPlantReportsNoDuplicatedId() {
        S88Element root = new S88Element().setId("PLANTA");
        root.addChild(new S88Element().setId("TANQUE_1"));
        root.addChild(new S88Element().setId("TANQUE_2"));

        assertTrue(new S88PlantModel(root).getDuplicateIds().isEmpty());
    }

    @Test
    void firstElementWinsTheIndexAndStaysReachable() {
        S88Element root = new S88Element().setId("PLANTA");
        S88Element first = new S88Element().setId("TANQUE_1");
        S88Element second = new S88Element().setId("TANQUE_1");
        root.addChild(first);
        root.addChild(second);

        S88PlantModel model = new S88PlantModel(root);

        assertSame(first, model.findById("TANQUE_1").orElseThrow());
    }

    @Test
    void removingAClashingElementKeepsTheSurvivorReachable() {
        S88Element root = new S88Element().setId("PLANTA");
        S88Element first = new S88Element().setId("TANQUE_1");
        S88Element second = new S88Element().setId("TANQUE_1");
        root.addChild(first);
        root.addChild(second);
        S88PlantModel model = new S88PlantModel(root);

        // removing the element the index does not point at must not evict the one it does
        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.REMOVED, second));

        assertSame(first, model.findById("TANQUE_1").orElseThrow());
    }

    @Test
    void creatingAnElementWithAnAlreadyUsedIdIsRejected() {
        S88Element root = new S88Element().setId("PLANTA");
        S88PlantModel model = new S88PlantModel(root);
        S88Element existing = new S88Element().setId("TANQUE_1");
        root.addChild(existing);
        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.ADDED, existing));

        assertThrows(IllegalStateException.class,
                () -> CreateElementUseCase.execute(model, root, "TANQUE_1", null));
    }

    // ===== classes are global to the plant, gated by their target level =====

    @Test
    void aClassDefinedOnOneBranchIsOfferedOnEveryOtherOne() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element first = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        S88Element second = new S88Element().setId("TANQUE_2").setLevel(S88Level.UNIT);
        root.addChild(first);
        root.addChild(second);

        // created while building the first unit only
        S88ElementClass heating = new S88ElementClass();
        heating.setName("CALENTAMIENTO");
        first.addElementClass(heating);
        heating.setTargetLevel(S88Level.EQUIPMENTMODULE);

        S88PlantModel model = new S88PlantModel(root);
        model.registerClass(heating);

        List<S88ElementClass> offered = model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE);

        assertEquals(1, offered.size());
        assertSame(heating, offered.get(0));
    }

    @Test
    void onlyClassesTargetingTheRequestedLevelAreOffered() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);

        S88ElementClass emClass = classFor("CALENTAMIENTO", S88Level.EQUIPMENTMODULE);
        S88ElementClass unitClass = classFor("TANQUE_TIPO", S88Level.UNIT);
        S88ElementClass noLevel = classFor("SIN_NIVEL", null);
        for (S88ElementClass ec : List.of(emClass, unitClass, noLevel)) {
            model.registerClass(ec);
        }

        assertEquals(List.of(emClass), model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE));
        assertEquals(List.of(unitClass), model.getClassesForChildLevel(S88Level.UNIT));
    }

    @Test
    void enumerationsAreNeverOfferedAsElementClasses() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);

        S88ElementClass emClass = classFor("CALENTAMIENTO", S88Level.EQUIPMENTMODULE);
        model.registerClass(emClass);
        S88Enumeration enumeration = new S88Enumeration("PRESET");
        enumeration.setValue("ALTO", 1);
        model.registerEnumeration(enumeration);

        List<S88ElementClass> offered = model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE);

        assertEquals(List.of(emClass), offered);
    }

    @Test
    void askingForAnUnknownLevelYieldsNothingRatherThanFailing() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);
        model.registerClass(classFor("CALENTAMIENTO", S88Level.EQUIPMENTMODULE));

        assertTrue(model.getClassesForChildLevel(null).isEmpty());
    }

    @Test
    void aClassOnlyAttachedToTheTreeIsStillOfferedEverywhere() {
        // The loader hands the pool of a level to every element it walks, so a class can be reachable
        // only through those lists. Reading the registry alone used to hide it, which is what made a
        // class created under one unit disappear when creating a module under another.
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element first = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        S88Element second = new S88Element().setId("TANQUE_2").setLevel(S88Level.UNIT);
        root.addChild(first);
        root.addChild(second);

        S88ElementClass heating = classFor("CALENTAMIENTO", S88Level.EQUIPMENTMODULE);
        first.addElementClass(heating);
        second.addElementClass(heating);

        S88PlantModel model = new S88PlantModel(root);

        List<S88ElementClass> offered = model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE);

        assertEquals(List.of(heating), offered);
    }

    @Test
    void aClassOfferedByBothSourcesAppearsOnlyOnce() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        root.addChild(unit);

        S88ElementClass heating = classFor("CALENTAMIENTO", S88Level.EQUIPMENTMODULE);
        unit.addElementClass(heating);

        S88PlantModel model = new S88PlantModel(root);
        model.registerClass(heating);

        assertEquals(List.of(heating), model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE));
    }

    @Test
    void aTreeClassTargetingAnotherLevelIsNotOffered() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        root.addChild(unit);

        // attached to the unit, but it is a class of areas, not of equipment modules
        unit.addElementClass(classFor("PLANTA_TIPO", S88Level.AREA));

        S88PlantModel model = new S88PlantModel(root);

        assertTrue(model.getClassesForChildLevel(S88Level.EQUIPMENTMODULE).isEmpty());
    }

    // ===== attaching a child through the model =====

    @Test
    void addChildAttachesTheChildUnderItsParentAndIndexesIt() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);
        S88Element child = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);

        model.addChild(root, child);

        assertSame(child, root.getChildren().get(0));
        assertSame(root, child.getParent());
        assertSame(child, model.findById("TANQUE_1").orElseThrow(),
                "a newly added element is reachable by id without waiting for a reload");
    }

    @Test
    void addChildUnderANullParentAttachesToTheRoot() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);
        S88Element child = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);

        model.addChild(null, child);

        assertSame(root, child.getParent());
    }

    @Test
    void addChildCanBeRepeatedBecauseIndexingIsByIdentity() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88PlantModel model = new S88PlantModel(root);
        S88Element child = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);

        model.addChild(root, child);
        // the ADDED event the use cases fire after attaching re-indexes the very same element
        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.ADDED, child));

        assertTrue(model.getDuplicateIds().isEmpty(),
                "re-indexing the same element must not count it as a duplicate");
        assertSame(child, model.findById("TANQUE_1").orElseThrow());
    }

    @Test
    void addChildThrowsWhenThereIsNothingToAttachTo() {
        S88PlantModel model = new S88PlantModel(null);
        S88Element child = new S88Element().setId("TANQUE_1");

        assertThrows(IllegalArgumentException.class,
                () -> model.addChild(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> model.addChild(null, child),
                "without a parent and without a root there is nowhere to attach the child");
    }

    private static S88ElementClass classFor(String name, S88Level targetLevel) {
        S88ElementClass ec = new S88ElementClass();
        ec.setName(name);
        ec.setTargetLevel(targetLevel);
        return ec;
    }
}
