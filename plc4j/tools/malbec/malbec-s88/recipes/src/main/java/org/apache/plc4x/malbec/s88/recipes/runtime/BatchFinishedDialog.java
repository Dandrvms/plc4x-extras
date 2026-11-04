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
package org.apache.plc4x.malbec.s88.recipes.runtime;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;

/** Tells the operator that a batch finished and where its report is. */
final class BatchFinishedDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    BatchFinishedDialog(Window owner, BatchSnapshot snapshot, Path report, Path folder,
                        List<BatchEvent> events) {
        super(owner, "Batch finished", ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));

        JPanel summary = new JPanel(new BorderLayout(4, 4));
        JLabel title = new JLabel("Batch " + snapshot.batchId() + " finished: "
                + snapshot.batchState());
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        summary.add(title, BorderLayout.NORTH);
        summary.add(new JLabel("Report: " + (report == null ? "not available" : report)), BorderLayout.CENTER);
        body.add(summary, BorderLayout.NORTH);

        JTextArea eventText = new JTextArea(eventText(events), 12, 90);
        eventText.setEditable(false);
        eventText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        body.add(new JScrollPane(eventText), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton openReport = new JButton("Open report");
        openReport.setEnabled(report != null && Files.isRegularFile(report));
        openReport.addActionListener(event -> open(report));
        buttons.add(openReport);
        JButton openFolder = new JButton("Open folder");
        openFolder.setEnabled(folder != null && Files.isDirectory(folder));
        openFolder.addActionListener(event -> open(folder));
        buttons.add(openFolder);
        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        buttons.add(close);
        body.add(buttons, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(body, BorderLayout.CENTER);
        setSize(new Dimension(860, 520));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(close);
    }

    private static String eventText(List<BatchEvent> events) {
        if (events == null || events.isEmpty()) {
            return "No events.";
        }
        StringBuilder text = new StringBuilder();
        for (BatchEvent event : events) {
            text.append(event.wallTime()).append("  sim=")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", event.simTime()))
                    .append("  ").append(event.type())
                    .append("  ").append(event.elementId() == null ? "" : event.elementId())
                    .append("  ").append(event.details() == null ? "" : event.details())
                    .append('\n');
        }
        return text.toString();
    }

    private void open(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                JOptionPane.showMessageDialog(this, "The desktop cannot open files.",
                        "Open", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | UnsupportedOperationException failure) {
            JOptionPane.showMessageDialog(this, "Could not open " + path + "\n"
                    + failure.getMessage(), "Open", JOptionPane.ERROR_MESSAGE);
        }
    }
}
