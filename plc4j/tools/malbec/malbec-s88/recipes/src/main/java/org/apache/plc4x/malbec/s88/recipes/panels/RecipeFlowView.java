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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.RecipeFlowAnalyzer;
import org.apache.plc4x.malbec.s88.core.RecipeFlowFormatter;
import org.netbeans.core.api.multiview.MultiViewHandler;
import org.netbeans.core.api.multiview.MultiViewPerspective;
import org.netbeans.core.api.multiview.MultiViews;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

/**
 * The route through a recipe, as a list of the steps and branches it takes.
 */
@MultiViewElement.Registration(
        displayName = "Flow",
        iconBase = "org/apache/plc4x/malbec/s88/recipes/nodes/recipe.png",
        mimeType = "text/b2mml-recipe+xml",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        preferredID = "RecipeFlow",
        position = 200
)
public final class RecipeFlowView extends AbstractRecipeView {

    public static final String PREFERRED_ID = "RecipeFlow";

    private static final int MAX_ROWS = 5000;
    private static final int MAX_DEPTH = 12;

    private final RecipeFlowTableModel tableModel = new RecipeFlowTableModel();
    private final JTable table = new JTable(tableModel);
    private final JCheckBox expand = new JCheckBox("Expand subcharts", true);
    private final JCheckBox follow = new JCheckBox("Follow chart", true);
    private final JTextArea warnings = new JTextArea(3, 0);
    private final JScrollPane warningsScroll = new JScrollPane(warnings);
    private final JPanel warningsPanel = new JPanel(new BorderLayout());
    private final Consumer<String> onChartSelection = this::followSelection;

    private RecipeFlowAnalyzer.Route route =
            new RecipeFlowAnalyzer.Route(List.of(), List.of());

    public RecipeFlowView(Lookup context) {
        super(context);
        table.setFillsViewportHeight(true);
        table.setFont(table.getFont().deriveFont(Font.PLAIN));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openRowAt(event);
                }
            }
        });

        warnings.setEditable(false);
        warnings.setLineWrap(true);
        warnings.setWrapStyleWord(true);
        warnings.setFont(warnings.getFont().deriveFont(Font.PLAIN));
        warnings.setBackground(warningsPanel.getBackground());
        warningsScroll.setBorder(null);
        warningsPanel.add(new JLabel("Warnings"), BorderLayout.NORTH);
        warningsPanel.add(warningsScroll, BorderLayout.CENTER);
        warningsPanel.setVisible(false);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(warningsPanel, BorderLayout.SOUTH);
        addActions();
    }

    public RecipeFlowView(RecipeEditorModel model) {
        this(Lookups.singleton(model));
    }

    private void addActions() {
        expand.addActionListener(event -> regenerate());
        follow.addActionListener(event -> followSelection(model.chartSelection()));

        JButton copy = new JButton("Copy text");
        copy.addActionListener(event -> copyText());
        JButton exportText = new JButton("Export text");
        exportText.addActionListener(event -> export(false));
        JButton exportCsv = new JButton("Export CSV");
        exportCsv.addActionListener(event -> export(true));

        toolBar().addSeparator();
        toolBar().add(expand);
        toolBar().add(follow);
        toolBar().add(copy);
        toolBar().add(exportText);
        toolBar().add(exportCsv);
    }

    @Override
    public String viewName() {
        return "Flow";
    }

    @Override
    protected int redrawDelayMillis() {
        return 300;
    }

    @Override
    protected void redraw() {
        regenerate();
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        model.removeChartSelectionListener(onChartSelection);
        model.addChartSelectionListener(onChartSelection);
        followSelection(model.chartSelection());
    }

    @Override
    public void componentClosed() {
        model.removeChartSelectionListener(onChartSelection);
        super.componentClosed();
    }

    /** Reads the recipe again and shows the route it takes now. */
    private void regenerate() {
        route = RecipeFlowAnalyzer.analyze(model.getRecipe(),
                new RecipeFlowAnalyzer.Options(expand.isSelected(), MAX_ROWS, MAX_DEPTH));
        tableModel.read(route);
        showWarnings();
        followSelection(model.chartSelection());
    }

    private void showWarnings() {
        List<String> said = new ArrayList<>();
        if (model.warning() != null) {
            said.add(model.warning());
        }
        said.addAll(route.warnings());
        warnings.setText(String.join("\n", said));
        warningsPanel.setVisible(!said.isEmpty());
    }

    private void followSelection(String nodeId) {
        if (!follow.isSelected()) {
            return;
        }
        int row = tableModel.findRow(nodeId, model.currentChartPath());
        if (row < 0) {
            return;
        }
        int shown = table.convertRowIndexToView(row);
        table.setRowSelectionInterval(shown, shown);
        table.scrollRectToVisible(table.getCellRect(shown, 0, true));
    }

    private void openRowAt(MouseEvent event) {
        int shown = table.rowAtPoint(event.getPoint());
        if (shown < 0) {
            return;
        }
        RecipeFlowAnalyzer.Row row = tableModel.rowAt(table.convertRowIndexToModel(shown));
        if (row == null || row.id() == null) {
            return;
        }
        model.showChartNode(chartOwner(row), row.id());
        showChart();
    }

    private S88RecipeElement chartOwner(RecipeFlowAnalyzer.Row row) {
        String ownerId = chartOwnerId(row.chartPath());
        return ownerId == null ? null : model.getRecipe().findElement(ownerId).orElse(null);
    }

    /**
     * The id of the step whose chart a route path belongs to.
     *
     * @param chartPath the path the route gives a row
     * @return the step id, or {@code null} when the row is in the chart of the recipe
     */
    static String chartOwnerId(String chartPath) {
        if (chartPath == null) {
            return null;
        }
        int slash = chartPath.lastIndexOf('/');
        return slash < 0 ? null : chartPath.substring(slash + 1);
    }

    /** Brings the chart tab forward, after the model has said which node to pick. */
    private void showChart() {
        TopComponent window = window();
        if (window == null) {
            return;
        }
        MultiViewHandler handler = MultiViews.findMultiViewHandler(window);
        if (handler == null) {
            return;
        }
        for (MultiViewPerspective perspective : handler.getPerspectives()) {
            if (RecipeChartView.PREFERRED_ID.equals(perspective.preferredID())) {
                handler.requestVisible(perspective);
                handler.requestActive(perspective);
                return;
            }
        }
    }

    private void copyText() {
        StringSelection copied = new StringSelection(RecipeFlowFormatter.toText(route));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(copied, copied);
    }

    private void export(boolean csv) {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                csv ? "CSV" : "Text", csv ? "csv" : "txt");
        chooser.setFileFilter(filter);
        chooser.setSelectedFile(new java.io.File(
                (model.getRecipe().getId() == null ? "route" : model.getRecipe().getId())
                        + (csv ? ".csv" : ".txt")));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File file = chooser.getSelectedFile();
        try {
            Files.writeString(file.toPath(),
                    csv ? RecipeFlowFormatter.toCsv(route) : RecipeFlowFormatter.toText(route),
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            JOptionPane.showMessageDialog(this,
                    "Could not write '" + file.getName() + "': " + failure.getMessage(),
                    "Export route", JOptionPane.ERROR_MESSAGE);
        }
    }
}
