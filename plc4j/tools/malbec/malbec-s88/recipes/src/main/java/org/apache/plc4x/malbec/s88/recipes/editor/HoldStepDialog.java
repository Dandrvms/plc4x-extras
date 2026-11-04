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
package org.apache.plc4x.malbec.s88.recipes.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Asks how many seconds a hold step should last. */
final class HoldStepDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JSpinner seconds = new JSpinner(
            new SpinnerNumberModel(60, 1, 1_000_000, 1));
    private Integer result;

    HoldStepDialog(Window owner) {
        super(owner, "Add hold step", ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        line.add(new JLabel("Hold for"));
        line.add(seconds);
        line.add(new JLabel("seconds"));
        content.add(line, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        buttons.add(cancel);
        JButton accept = new JButton("Add");
        accept.addActionListener(event -> {
            result = ((Number) seconds.getValue()).intValue();
            dispose();
        });
        buttons.add(accept);
        content.add(buttons, BorderLayout.SOUTH);
        add(content);
        setSize(new Dimension(340, 130));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(accept);
    }

    Integer showDialog() {
        setVisible(true);
        return result;
    }
}
