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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CreateElementUseCaseTest {

    private S88PlantModel model;
    private S88Element root;

    @BeforeEach
    void setUp() {
        model = mock(S88PlantModel.class);
        root = new S88Element()
                .setId("RootArea")
                .setLevel(S88Level.AREA);
        
        when(model.getRoot()).thenReturn(root);
        when(model.findById(anyString())).thenReturn(Optional.empty());
        // A real plant attaches the child under its parent while indexing it; the mock does the
        // attaching part so the tests can read the tree they just created.
        doAnswer(invocation -> {
            S88Element parent = invocation.getArgument(0);
            S88Element child = invocation.getArgument(1);
            parent.addChild(child);
            return null;
        }).when(model).addChild(any(S88Element.class), any(S88Element.class));
    }

    @Test
    void testExecuteCreatesChildWithCorrectLevel() {
        CreateElementUseCase.execute(model, root, "NEW_PC", null);

        assertEquals(1, root.getChildren().size());
        S88Element child = root.getChildren().get(0);
        assertEquals("NEW_PC", child.getId());
        assertEquals(S88Level.PROCESSCELL, child.getLevel());
    }

    @Test
    void testExecuteFiresEvent() {
        CreateElementUseCase.execute(model, root, "NEW_PC", null);

        ArgumentCaptor<S88ChangeEvent> eventCaptor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(eventCaptor.capture());
        
        S88ChangeEvent event = eventCaptor.getValue();
        assertEquals(S88ChangeEvent.Type.ADDED, event.type());
        assertEquals("NEW_PC", event.element().getId());
    }

    @Test
    void testExecuteThrowsOnDuplicateId() {
        when(model.findById("EXISTING")).thenReturn(Optional.of(mock(S88Element.class)));

        assertThrows(IllegalStateException.class, () -> {
            CreateElementUseCase.execute(model, root, "EXISTING", null);
        });
    }

    @Test
    void testExecuteThrowsOnEmptyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            CreateElementUseCase.execute(model, root, "", null);
        });
    }

    @Test
    void testExecuteThrowsOnLeafParent() {
        S88Element em = new S88Element()
                .setId("EM1")
                .setLevel(S88Level.EQUIPMENTMODULE);

        assertThrows(IllegalStateException.class,
                () -> CreateElementUseCase.execute(model, em, "CHILD", null));
    }

    @Test
    void testExecuteRejectsIdWithInvalidCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> CreateElementUseCase.execute(model, root, "tanque 1", null));
    }

    @Test
    void testExecuteRejectsIdOverTheLengthLimit() {
        String tooLong = "A".repeat(NameValidator.MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> CreateElementUseCase.execute(model, root, tooLong, null));
    }

    @Test
    void testElementsCreatedFromTheSameClassDoNotShareNestedProperties() {
        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName("CALENTAMIENTO");
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("Reference", 1);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("TEMPERATURA_SP", parameter);
        elementClass.setProperty("Parameters", parameters);

        CreateElementUseCase.execute(model, root, "FIRST", elementClass);
        CreateElementUseCase.execute(model, root, "SECOND", elementClass);

        S88Element first = root.getChildren().get(0);
        S88Element second = root.getChildren().get(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstParameter =
                (Map<String, Object>) first.getStructuredProperty("Parameters").get("TEMPERATURA_SP_FIRST");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondParameter =
                (Map<String, Object>) second.getStructuredProperty("Parameters").get("TEMPERATURA_SP_SECOND");

        assertNotSame(firstParameter, secondParameter,
                "Instances of the same class must not share the same nested map");

        firstParameter.put("Reference", 99);

        assertEquals(1, secondParameter.get("Reference"),
                "Editing one instance must not mutate a sibling created from the same class");
    }

    @Test
    void testCreatingAnElementGivesItTheVariableNamesOfTheClass() {
        // An instance names its variables after itself, exactly like a duplicated copy: the class
        // base TEMPERATURA_SP is published as TEMPERATURA_SP_TANQUE_1 and tied back to its base
        // name, so the recipe keeps addressing the same setpoint in every instance of the type.
        S88Element unit = new S88Element()
                .setId("TANQUE_1")
                .setLevel(S88Level.UNIT);
        root.addChild(unit);

        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName("CALENTAMIENTO");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("TEMPERATURA", 50);
        elementClass.setProperty("Parameters", parameters);
        Map<String, Object> reports = new LinkedHashMap<>();
        reports.put("TEMPERATURA", 48.5f);
        elementClass.setProperty("Reports", reports);

        CreateElementUseCase.execute(model, unit, "CALENTAMIENTO_TANQUE_1", elementClass);

        S88Element created = unit.getChildren().get(0);
        assertEquals(50, created.getStructuredProperty("Parameters").get("TEMPERATURA_CALENTAMIENTO_TANQUE_1"));
        assertEquals(48.5f, created.getStructuredProperty("Reports").get("TEMPERATURA_CALENTAMIENTO_TANQUE_1"));
        assertEquals("Parameters/TEMPERATURA",
                created.getBaseName("Parameters", "TEMPERATURA_CALENTAMIENTO_TANQUE_1"),
                "the published name must be tied to the base name a recipe addresses");
        assertEquals("Reports/TEMPERATURA",
                created.getBaseName("Reports", "TEMPERATURA_CALENTAMIENTO_TANQUE_1"));
    }

    @Test
    void testCreatingAnElementDoesNotMutateTheClassProperties() {
        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName("CALENTAMIENTO");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("TEMPERATURA_SP", 50);
        elementClass.setProperty("Parameters", parameters);

        CreateElementUseCase.execute(model, root, "FIRST", elementClass);

        S88Element created = root.getChildren().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> createdParameters = (Map<String, Object>) created.getProperty("Parameters");
        createdParameters.put("TIEMPO", 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> classParameters = (Map<String, Object>) elementClass.getProperty("Parameters");
        assertFalse(classParameters.containsKey("TIEMPO"),
                "Editing an instance must not leak into the class it was created from");
    }

    @Test
    void testWithoutAClassTheTypeIsDerivedFromTheIdAndRegistered() {
        CreateElementUseCase.execute(model, root, "NEW_PC", null);

        S88Element created = root.getChildren().get(0);
        S88ElementClass elementClass = created.getElementClass();
        assertNotNull(elementClass);
        assertEquals("NEW_PC", elementClass.getName(),
                "the id without its numeric discriminator is the type name");
        assertEquals(S88Level.PROCESSCELL, elementClass.getTargetLevel());
        assertEquals(1, root.getElementClasses().size(),
                "the created type is attached where the children of that level are offered");

        ArgumentCaptor<S88ElementClass> captor = ArgumentCaptor.forClass(S88ElementClass.class);
        verify(model).registerClass(captor.capture());
        assertSame(elementClass, captor.getValue());
    }

    @Test
    void testWithoutAClassAReusableTypeIsUsedInsteadOfCreatingAnother() {
        S88Element unit = new S88Element()
                .setId("TANQUE_1")
                .setLevel(S88Level.UNIT);
        root.addChild(unit);
        S88ElementClass reusable = new S88ElementClass();
        reusable.setName("CALENTAMIENTO_TANQUE");
        reusable.setTargetLevel(S88Level.EQUIPMENTMODULE);
        when(model.findClass("CALENTAMIENTO_TANQUE")).thenReturn(reusable);

        CreateElementUseCase.execute(model, unit, "CALENTAMIENTO_TANQUE_2", null);

        S88Element created = unit.getChildren().get(0);
        assertSame(reusable, created.getElementClass(),
                "an element is created under the type the base of its id already names");
        verify(model, never()).registerClass(any(S88ElementClass.class));
    }

    @Test
    void testWithoutAClassAReusableTypeOfAnotherLevelIsRejected() {
        S88ElementClass wrongLevel = new S88ElementClass();
        wrongLevel.setName("NEW_PC");
        wrongLevel.setTargetLevel(S88Level.UNIT);
        when(model.findClass("NEW_PC")).thenReturn(wrongLevel);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CreateElementUseCase.execute(model, root, "NEW_PC_1", null));

        assertTrue(ex.getMessage().contains("cannot serve"), ex.getMessage());
        verify(model, never()).registerClass(any(S88ElementClass.class));
    }

    @Test
    void testAnEnumeratedClassCannotBePickedForAnElement() {
        // A class named with the reserved prefix is an enumeration by convention, so it can neither
        // serve a freshly derived type nor be chosen directly.
        S88ElementClass enumerated = new S88ElementClass();
        enumerated.setName("ENUM_CLASE_PROPIA");
        enumerated.setTargetLevel(S88Level.PROCESSCELL);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CreateElementUseCase.execute(model, root, "HIJA", enumerated));

        assertTrue(ex.getMessage().contains("cannot serve"), ex.getMessage());
        verify(model, never()).registerClass(any(S88ElementClass.class));
    }

    @Test
    void testDerivingATypeFromAnIdWithTheReservedPrefixIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CreateElementUseCase.execute(model, root, "ENUM_PELIGRO_1", null));

        assertTrue(ex.getMessage().contains("reserved"), ex.getMessage());
        verify(model, never()).registerClass(any(S88ElementClass.class));
    }
}
