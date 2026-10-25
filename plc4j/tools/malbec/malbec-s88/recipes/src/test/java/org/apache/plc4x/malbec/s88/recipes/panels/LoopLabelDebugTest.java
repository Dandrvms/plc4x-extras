package org.apache.plc4x.malbec.s88.recipes.panels;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.ChartEditOps;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.data.impl.BatchMLRecipeRepositoryImpl;
import org.junit.jupiter.api.Test;

class LoopLabelDebugTest {

    @Test
    void printProcessLoopBars() throws IOException {
        S88MasterRecipe recipe = load(Path.of(
                "C:/Users/ceos/MalbecProjects/Batch Project2/PRUEBAS/Process.xml"));
        S88RecipeElement process = recipe.findElement("PROCESS").orElseThrow();
        ChartEditOps.loopStep(recipe, process, "ADICION_MANUAL_TANQUE", "T_ADICION_LOOP");
        recipe.getProcedureLogic();
        process.getProcedureLogic().findTransition("T_ADICION_LOOP").orElseThrow()
                .setCondition("ADICION_MANUAL_TANQUE#STATE_ADICION_MANUAL_TANQUE#=#COMPLETE");

        Map<String, RecipeChartScene.NodeSpec> what = RecipeChartScene.nodesOf(recipe, process);
        Map<String, RecipeShapes.Shape> shapes = new LinkedHashMap<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            what.forEach((id, spec) -> {
                shapes.put(id, spec.shape());
                widths.put(id, RecipeShapes.textWidth(g, spec.label()));
                System.out.println("NODE " + id + " label='" + spec.label() + "' width=" + widths.get(id));
            });
        } finally {
            g.dispose();
        }

        SfcLayoutEngine.Placement at = new SfcLayoutEngine(
                EditProcedureLogicUseCase.existingChart(recipe, process), shapes, widths).place();
        at.bars().forEach((id, rect) -> System.out.println("BAR " + id + " " + rect));
        at.steps().forEach((id, rect) -> System.out.println("STEP " + id + " " + rect));
    }

    private static S88MasterRecipe load(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new BatchMLRecipeRepositoryImpl(new S88Storage() {
            @Override
            public InputStream openInput() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public java.io.OutputStream openOutput() {
                throw new UnsupportedOperationException();
            }
        }).loadRecipe();
    }
}
