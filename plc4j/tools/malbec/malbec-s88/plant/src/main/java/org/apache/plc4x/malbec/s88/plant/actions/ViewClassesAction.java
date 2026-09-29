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

package org.apache.plc4x.malbec.s88.plant.actions;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.plant.panels.ClassDialogBuilder;
import org.apache.plc4x.malbec.s88.plant.panels.ClassFactory;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


@NbBundle.Messages({
        "CTL_ViewClassesAction=View equipment types",
})
public class ViewClassesAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public ViewClassesAction() {
        this(Lookup.EMPTY);
    }

    private ViewClassesAction(Lookup context) {
        super(Bundle.CTL_ViewClassesAction());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ViewClassesAction(actionContext);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        S88Element equipment = context.lookup(S88Element.class);

        DefaultListModel<S88ElementClass> classListModel = new DefaultListModel<>();

        for(S88ElementClass ec : equipment.getElementClasses()){
            classListModel.addElement(ec);
        }

        JList<S88ElementClass> list = new JList<>(classListModel);

        ClassDialogBuilder builder = new ClassDialogBuilder("Select Equipment Type", false);

        builder.withReadOnlyNameField(equipment.getId());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof S88ElementClass) {
                    setText(((S88ElementClass) value).getName());
                }
                return this;
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                        S88ElementClass ec = list.getModel().getElementAt(index);
                        ClassFactory.showClass(ec);
                    }
                }
            }
        });

        list.setVisibleRowCount(10);
        JScrollPane listScrollPane = new JScrollPane(list);
        listScrollPane.setPreferredSize(new Dimension(350, 220));

        builder.addComponentRow(listScrollPane);

        JDialog dlg = builder.build();
        dlg.setVisible(true);
    }
}
