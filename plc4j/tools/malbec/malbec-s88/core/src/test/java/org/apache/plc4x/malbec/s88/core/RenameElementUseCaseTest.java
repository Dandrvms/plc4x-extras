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
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenameElementUseCaseTest {

    private S88PlantModel model;
    private S88Element element;

    @BeforeEach
    void setUp() {
        model = mock(S88PlantModel.class);
        element = new S88Element()
                .setId("OldName")
                .setLevel(S88Level.UNIT);
        when(model.findById(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void testRenamesElementAndFiresReloadedEvent() {
        RenameElementUseCase.execute(model, element, "NEW_NAME");

        assertEquals("NEW_NAME", element.getId());

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.RELOADED, captor.getValue().type());
        assertSame(element, captor.getValue().element());
    }

    @Test
    void testThrowsOnEmptyId() {
        assertThrows(IllegalArgumentException.class,
                () -> RenameElementUseCase.execute(model, element, "   "));
        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testThrowsOnNullId() {
        assertThrows(IllegalArgumentException.class,
                () -> RenameElementUseCase.execute(model, element, null));
    }

    @Test
    void testThrowsOnDuplicateId() {
        when(model.findById("TAKEN")).thenReturn(Optional.of(mock(S88Element.class)));

        assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "TAKEN"));
        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testImpactIsReportedForAConformingVariable() {
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1));

        NamingAdvisor.Advice advice = RenameElementUseCase.impactOf(element);

        assertNotNull(advice);
        assertTrue(advice.message().contains("1 variable"), advice.message());
        assertTrue(advice.message().contains("NIVEL"), advice.message());
    }

    @Test
    void testImpactOfALeafIsWorthAWarning() {
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1));

        assertNotNull(RenameElementUseCase.impactOf(element),
                "an element with variables must always warn, even a leaf");
    }

    @Test
    void testNoImpactWhenNothingIsPublished() {
        assertNull(RenameElementUseCase.impactOf(element));
    }

    @Test
    void testNoImpactWhenOnlyFreeNamesArePublished() {
        element.setProperty("NIVEL", 1);

        assertNull(RenameElementUseCase.impactOf(element),
                "a hand-typed name carries no element id, so a rename cannot re-suffix it");
    }

    @Test
    void testAskingForTheImpactDoesNotRenameAnything() {
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1));

        RenameElementUseCase.impactOf(element);

        assertEquals("OldName", element.getId());
        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testRenameReSuffixesConformingVariablesAndRemembersTheirBaseName() {
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1));

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        assertTrue(element.getProperties().get("Parameters") instanceof Map);
        Map<?, ?> parameters = (Map<?, ?>) element.getProperties().get("Parameters");
        assertTrue(parameters.containsKey("NIVEL_NEW_NAME"), parameters.toString());
        assertFalse(parameters.containsKey("NIVEL_OLDNAME"));
        assertEquals("Parameters/NIVEL", element.getBaseName("Parameters", "NIVEL_NEW_NAME"),
                "the recipe keeps addressing the base name after the element is renamed");
    }

    @Test
    void testRenameKeepsAnExistingBaseNamePointerWhileMovingTheVariable() {
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1));
        element.setBaseName("Parameters", "NIVEL_OLDNAME", "Parameters/NIVEL");

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        assertEquals("Parameters/NIVEL", element.getBaseName("Parameters", "NIVEL_NEW_NAME"));
    }

    @Test
    void testRenameLeavesHandTypedVariablesAlone() {
        element.setProperty("Parameters", Map.of("TURBIDEZ", 1));

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        Map<?, ?> parameters = (Map<?, ?>) element.getProperties().get("Parameters");
        assertTrue(parameters.containsKey("TURBIDEZ"), parameters.toString());
        assertNull(element.getBaseName("Parameters", "TURBIDEZ"));
    }

    @Test
    void testRenameCascadesToTheVariablesAndIdsOfTheSubtree() {
        S88Element child = new S88Element().setId("CALENTAMIENTO_OLDNAME").setLevel(S88Level.EQUIPMENTMODULE);
        child.setProperty("Parameters", Map.of("TEMPERATURA_SP_CALENTAMIENTO_OLDNAME", 50));
        element.addChild(child);

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        // the child is renamed chained off its parent, and its variable follows the new id
        assertEquals("CALENTAMIENTO_NEW_NAME", child.getId());
        Map<?, ?> parameters = (Map<?, ?>) child.getProperties().get("Parameters");
        assertTrue(parameters.containsKey("TEMPERATURA_SP_CALENTAMIENTO_NEW_NAME"), parameters.toString());
        assertFalse(parameters.containsKey("TEMPERATURA_SP_CALENTAMIENTO_OLDNAME"));
        assertEquals("Parameters/TEMPERATURA_SP",
                child.getBaseName("Parameters", "TEMPERATURA_SP_CALENTAMIENTO_NEW_NAME"),
                "the variable of the child is tied to the base name its type expects, derived from "
                        + "the id of the module that owns it");
    }

    @Test
    void testRenameCascadesToFreeShapedDescendantsOnlyAlongTheirIds() {
        // A descendant with a hand-typed id keeps it, and its variables keep their own names when
        // they do not carry the id of the element being renamed.
        S88Element free = new S88Element().setId("BOMBA").setLevel(S88Level.EQUIPMENTMODULE);
        free.setProperty("Parameters", Map.of("FLUJO_BOMBA", 10));
        element.addChild(free);

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        assertEquals("BOMBA", free.getId());
        Map<?, ?> parameters = (Map<?, ?>) free.getProperties().get("Parameters");
        assertTrue(parameters.containsKey("FLUJO_BOMBA"), parameters.toString());
    }

    @Test
    void testRenameReSuffixesTopLevelScalarVariablesNamedAfterTheElement() {
        element.setProperty("NIVEL_OLDNAME", 3.2f);

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        assertEquals(3.2f, element.getProperty("NIVEL_NEW_NAME"));
        assertFalse(element.getProperties().containsKey("NIVEL_OLDNAME"));
    }

    @Test
    void testRenameMovesTheBaseNameOfATopLevelAttribute() {
        // A recipe addresses the attribute by base name, so the pointer has to travel with the
        // attribute to its new name instead of being left behind on the old one.
        element.setProperty("PRESION_OLDNAME", 1.5f);
        element.setBaseName(null, "PRESION_OLDNAME", "Parameters/PRESION");

        RenameElementUseCase.execute(model, element, "NEW_NAME");

        assertEquals("Parameters/PRESION", element.getBaseName(null, "PRESION_NEW_NAME"),
                "the renamed attribute answers to the base name the recipe addresses");
        assertNull(element.getBaseName(null, "PRESION_OLDNAME"),
                "the pointer of the old name is not left behind");
    }

    @Test
    void testRenameRefusesWhenAVariableWouldCollide() {
        // One variable re-suffixes onto a name another variable of the same container already uses.
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1, "NIVEL_NEW_NAME", 2));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "NEW_NAME"));

        assertTrue(ex.getMessage().contains("NIVEL_NEW_NAME"), ex.getMessage());
        assertEquals("OldName", element.getId(),
                "a refused rename must not rename the element anyway");
    }

    @Test
    void testRenameRefusesWhenATopLevelAttributeWouldCollide() {
        element.setProperty("NIVEL_OLDNAME", 3.2f);
        element.setProperty("NIVEL_NEW_NAME", 4.2f);

        assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "NEW_NAME"));
    }

    @Test
    void testRenameRefusesWhenARenamedDescendantWouldClash() {
        // The renamed child lands on an id that a sibling element of the plant already has.
        S88Element child = new S88Element().setId("CALENTAMIENTO_OLDNAME").setLevel(S88Level.EQUIPMENTMODULE);
        element.addChild(child);
        S88Element sibling = new S88Element().setId("CALENTAMIENTO_NEW_NAME").setLevel(S88Level.EQUIPMENTMODULE);
        element.addChild(sibling);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "NEW_NAME"));

        assertTrue(ex.getMessage().contains("clash"), ex.getMessage());
        assertEquals("CALENTAMIENTO_OLDNAME", child.getId(),
                "a refused rename must not rename the descendants either");
        assertEquals("OldName", element.getId());
    }

    @Test
    void testARefusedRenameLeavesTheVariablesOfTheSubtreeUntouched() {
        // The id clash below is only discovered after the variables have been worked out, so this
        // pins that a refused rename leaves every variable under its old published name.
        S88Element child = new S88Element().setId("CALENTAMIENTO_OLDNAME").setLevel(S88Level.EQUIPMENTMODULE);
        child.setProperty("Parameters", Map.of("TEMPERATURA_SP_CALENTAMIENTO_OLDNAME", 50));
        element.addChild(child);
        element.addChild(new S88Element().setId("CALENTAMIENTO_NEW_NAME").setLevel(S88Level.EQUIPMENTMODULE));
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1));

        assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "NEW_NAME"));

        Map<?, ?> own = (Map<?, ?>) element.getProperties().get("Parameters");
        assertTrue(own.containsKey("NIVEL_OLDNAME"), own.toString());
        assertFalse(own.containsKey("NIVEL_NEW_NAME"), own.toString());
        Map<?, ?> childParameters = (Map<?, ?>) child.getProperties().get("Parameters");
        assertTrue(childParameters.containsKey("TEMPERATURA_SP_CALENTAMIENTO_OLDNAME"),
                childParameters.toString());
        assertFalse(childParameters.containsKey("TEMPERATURA_SP_CALENTAMIENTO_NEW_NAME"));
        assertNull(element.getBaseName("Parameters", "NIVEL_NEW_NAME"),
                "no base name pointer may be left behind for a variable that never moved");
    }

    @Test
    void testARefusedRenameLeavesEarlierElementsOfTheSubtreeUntouched() {
        // A collision deep in the subtree must not leave the elements above it already renamed.
        S88Element child = new S88Element().setId("CALENTAMIENTO_OLDNAME").setLevel(S88Level.EQUIPMENTMODULE);
        child.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1, "NIVEL_NEW_NAME", 2));
        element.addChild(child);
        element.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 7));

        assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "NEW_NAME"));

        Map<?, ?> own = (Map<?, ?>) element.getProperties().get("Parameters");
        assertTrue(own.containsKey("NIVEL_OLDNAME"), own.toString());
        assertEquals(7, own.get("NIVEL_OLDNAME"));
    }

    @Test
    void testARefusedRenameLeavesTopLevelAttributesUntouched() {
        element.setProperty("NIVEL_OLDNAME", 3.2f);
        S88Element child = new S88Element().setId("CALENTAMIENTO_OLDNAME").setLevel(S88Level.EQUIPMENTMODULE);
        child.setProperty("Parameters", Map.of("NIVEL_OLDNAME", 1, "NIVEL_NEW_NAME", 2));
        element.addChild(child);
        element.addChild(new S88Element().setId("CALENTAMIENTO_NEW_NAME").setLevel(S88Level.EQUIPMENTMODULE));

        assertThrows(IllegalStateException.class,
                () -> RenameElementUseCase.execute(model, element, "NEW_NAME"));

        assertEquals(3.2f, element.getProperty("NIVEL_OLDNAME"));
        assertFalse(element.getProperties().containsKey("NIVEL_NEW_NAME"));
    }
}
