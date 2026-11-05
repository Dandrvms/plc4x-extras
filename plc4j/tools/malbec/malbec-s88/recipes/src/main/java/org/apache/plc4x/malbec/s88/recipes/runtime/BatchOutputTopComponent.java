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
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** The live event log of the batch runtime. */
@NbBundle.Messages("CTL_BatchOutputTopComponent=Output")
@TopComponent.Description(
        preferredID = "BatchOutputTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "output", openAtStartup = false)
public final class BatchOutputTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;

    private static final Color INK = new Color(0x25, 0x2B, 0x2B);
    private static final Color ALARM = new Color(0xA8, 0x2A, 0x2A);

    private final BatchOutputModel model = new BatchOutputModel();
    private final DefaultTableModel rows = new DefaultTableModel(
            new Object[]{"Wall time", "Sim time", "Type", "Element", "State", "Detail"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(rows);
    private final JComboBox<BatchOutputModel.Filter> filter =
            new JComboBox<>(BatchOutputModel.Filter.values());
    private final JCheckBox autoScroll = new JCheckBox("Auto scroll", true);
    private final JButton openReport = new JButton("Open report");
    private final JButton openFolder = new JButton("Open folder");
    private final JLabel status = new JLabel("No batch selected");

    private BatchRuntimeTopComponent source;
    private Path report;
    private Path folder;
    private List<BatchEvent> shown = new ArrayList<>();

    public BatchOutputTopComponent() {
        initUi();
        setName("Output");
        setDisplayName("Output");
        setToolTipText("Events of the simulated batch");
    }

    private void initUi() {
        setLayout(new BorderLayout(6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        top.add(new JLabel("Filter:"));
        top.add(filter);
        top.add(autoScroll);
        top.add(openReport);
        top.add(openFolder);
        add(top, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(table, value,
                        selected, focused, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                BatchEvent event = modelRow < shown.size() ? shown.get(modelRow) : null;
                component.setForeground(!selected && BatchOutputModel.alarm(event) ? ALARM : INK);
                return component;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        bottom.add(status, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        filter.addActionListener(event -> refresh());
        openReport.addActionListener(event -> open(report));
        openFolder.addActionListener(event -> open(folder));
        updateButtons();
    }

    static void showFor(BatchRuntimeTopComponent runtime) {
        BatchOutputTopComponent output = find();
        if (output == null) {
            output = new BatchOutputTopComponent();
            if (WindowManager.getDefault().findMode("output") != null) {
                WindowManager.getDefault().findMode("output").dockInto(output);
            }
            output.open();
        }
        output.bind(runtime);
        output.requestActive();
    }

    static void publish(BatchRuntimeTopComponent runtime, List<BatchEvent> events) {
        BatchOutputTopComponent output = find();
        if (output != null) {
            output.accept(runtime, events);
        }
    }

    private static BatchOutputTopComponent find() {
        for (TopComponent opened : WindowManager.getDefault().getRegistry().getOpened()) {
            if (opened instanceof BatchOutputTopComponent output) {
                return output;
            }
        }
        return null;
    }

    void bind(BatchRuntimeTopComponent runtime) {
        source = runtime;
        model.clear();
        model.addAll(runtime == null ? List.of() : runtime.outputEvents());
        report = runtime == null ? null : runtime.reportFile();
        folder = runtime == null ? null : runtime.runFolder();
        status.setText(runtime == null ? "No batch selected" : "Batch: " + runtime.outputBatchId());
        filter.setSelectedItem(model.filter());
        refresh();
    }

    private void accept(BatchRuntimeTopComponent runtime, List<BatchEvent> events) {
        if (runtime != source) {
            bind(runtime);
            return;
        }
        model.addAll(events);
        report = runtime.reportFile();
        folder = runtime.runFolder();
        refresh();
    }

    private void refresh() {
        model.filter((BatchOutputModel.Filter) filter.getSelectedItem());
        shown = model.visible();
        rows.setRowCount(0);
        for (BatchEvent event : shown) {
            rows.addRow(new Object[]{wallTime(event.wallTime()), number(event.simTime()),
                    event.type().name(), text(event.elementId()), text(event.state()),
                    text(event.details())});
        }
        if (autoScroll.isSelected() && rows.getRowCount() > 0) {
            int last = rows.getRowCount() - 1;
            table.scrollRectToVisible(table.getCellRect(last, 0, true));
        }
        updateButtons();
    }

    private void updateButtons() {
        openReport.setEnabled(report != null && Files.isRegularFile(report));
        openFolder.setEnabled(folder != null && Files.isDirectory(folder));
    }

    private static String wallTime(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String text(String value) {
        return value == null ? "" : value;
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
