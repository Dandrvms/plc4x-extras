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

    private CreateElementUseCase useCase;
    private S88PlantModel model;
    private S88Element root;

    @BeforeEach
    void setUp() {
        useCase = new CreateElementUseCase();
        model = mock(S88PlantModel.class);
        root = new S88Element()
                .setId("RootArea")
                .setLevel(S88Level.AREA);
        
        when(model.getRoot()).thenReturn(root);
        when(model.findById(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void testExecuteCreatesChildWithCorrectLevel() {
        useCase.execute(model, root, "NEW_PC", null);

        assertEquals(1, root.getChildren().size());
        S88Element child = root.getChildren().get(0);
        assertEquals("NEW_PC", child.getId());
        assertEquals(S88Level.PROCESSCELL, child.getLevel());
    }

    @Test
    void testExecuteFiresEvent() {
        useCase.execute(model, root, "NEW_PC", null);

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
            useCase.execute(model, root, "EXISTING", null);
        });
    }

    @Test
    void testExecuteThrowsOnEmptyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            useCase.execute(model, root, "", null);
        });
    }

    @Test
    void testExecuteThrowsOnLeafParent() {
        S88Element em = new S88Element()
                .setId("EM1")
                .setLevel(S88Level.EQUIPMENTMODULE);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(model, em, "CHILD", null));
    }

    @Test
    void testExecuteRejectsIdWithInvalidCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(model, root, "tanque 1", null));
    }

    @Test
    void testExecuteRejectsIdOverTheLengthLimit() {
        String tooLong = "A".repeat(NameValidator.MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(model, root, tooLong, null));
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

        useCase.execute(model, root, "FIRST", elementClass);
        useCase.execute(model, root, "SECOND", elementClass);

        S88Element first = root.getChildren().get(0);
        S88Element second = root.getChildren().get(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstParameter =
                (Map<String, Object>) first.getStructuredProperty("Parameters").get("TEMPERATURA_SP");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondParameter =
                (Map<String, Object>) second.getStructuredProperty("Parameters").get("TEMPERATURA_SP");

        assertNotSame(firstParameter, secondParameter,
                "Instances of the same class must not share the same nested map");

        firstParameter.put("Reference", 99);

        assertEquals(1, secondParameter.get("Reference"),
                "Editing one instance must not mutate a sibling created from the same class");
    }

    @Test
    void testCreatingAnElementGivesItTheVariableNamesOfTheClass() {
        // A class holds the names its variables are published under, and the element gets exactly
        // those: one name per variable, written to the plant file as they are.
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

        useCase.execute(model, unit, "CALENTAMIENTO_TANQUE_1", elementClass);

        S88Element created = unit.getChildren().get(0);
        assertEquals(50, created.getStructuredProperty("Parameters").get("TEMPERATURA"));
        assertEquals(48.5f, created.getStructuredProperty("Reports").get("TEMPERATURA"));
    }

    @Test
    void testCreatingAnElementDoesNotMutateTheClassProperties() {
        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName("CALENTAMIENTO");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("TEMPERATURA_SP", 50);
        elementClass.setProperty("Parameters", parameters);

        useCase.execute(model, root, "FIRST", elementClass);

        S88Element created = root.getChildren().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> createdParameters = (Map<String, Object>) created.getProperty("Parameters");
        createdParameters.put("TIEMPO", 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> classParameters = (Map<String, Object>) elementClass.getProperty("Parameters");
        assertFalse(classParameters.containsKey("TIEMPO"),
                "Editing an instance must not leak into the class it was created from");
    }
}
