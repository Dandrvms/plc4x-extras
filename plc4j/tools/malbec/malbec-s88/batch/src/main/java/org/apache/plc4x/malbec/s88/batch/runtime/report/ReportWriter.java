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
package org.apache.plc4x.malbec.s88.batch.runtime.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.EventType;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.core.RecipeFlowAnalyzer;
import org.apache.plc4x.malbec.s88.core.RecipeFlowFormatter;

/** Writes the self-contained HTML report and the planned route of one run. */
public final class ReportWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_INSTANT;
    private static final Set<EventType> INCIDENTS = Set.of(
            EventType.COMMAND_REJECTED,
            EventType.FAILURE_INJECTED,
            EventType.CONNECTION_LOST,
            EventType.LOOP_GUARD_TRIPPED);

    private ReportWriter() {
    }

    /** Writes {@code route.txt}. */
    public static void writeRoute(RunFolder folder, BatchPayload payload) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(payload, "payload");
        writeAtomically(folder.routeFile(), RecipeFlowFormatter.toText(route(payload)));
    }

    /** Writes {@code route.txt} and {@code report.html}. */
    public static void write(RunFolder folder, BatchRunSummary summary) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(summary, "summary");
        BatchPayload payload = Objects.requireNonNull(summary.payload(), "payload");
        RecipeFlowAnalyzer.Route route = route(payload);
        writeRoute(folder, payload);
        writeAtomically(folder.reportFile(), html(summary, route));
    }

    private static RecipeFlowAnalyzer.Route route(BatchPayload payload) {
        if (payload.control() == null) {
            return new RecipeFlowAnalyzer.Route(List.of(), List.of());
        }
        return RecipeFlowAnalyzer.analyze(payload.control());
    }

    private static String html(BatchRunSummary summary, RecipeFlowAnalyzer.Route route) {
        BatchPayload payload = summary.payload();
        StringBuilder html = new StringBuilder(16_384);
        html.append("<!DOCTYPE html>\n<html lang=\"es\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<title>Reporte de batch ").append(escape(payload.batchId()))
                .append("</title>\n<style>\n")
                .append("body{font-family:system-ui,sans-serif;margin:2rem;color:#1f2933;}")
                .append("h1,h2{margin-top:1.6rem;}table{border-collapse:collapse;width:100%;")
                .append("margin:.5rem 0 1rem;}th,td{border:1px solid #cbd2d9;padding:.35rem .5rem;")
                .append("text-align:left;vertical-align:top;}th{background:#eef2f6;}")
                .append("pre{background:#f5f7fa;padding:1rem;overflow:auto;}")
                .append(".muted{color:#616e7c;}code{font-family:ui-monospace,monospace;}")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>Reporte de batch</h1>\n");

        appendIdentification(html, payload);
        appendTimes(html, summary);
        appendParameters(html, payload);
        appendPhases(html, summary.events());
        appendRoute(html, route);

        html.append("<h2>Línea de tiempo</h2>\n");
        if (summary.events().isEmpty()) {
            html.append("<p class=\"muted\">Sin eventos.</p>\n");
        } else {
            html.append("<table><thead><tr><th>Secuencia</th><th>Tiempo simulado</th>")
                    .append("<th>Tipo</th><th>Origen</th><th>Elemento</th><th>Estado</th>")
                    .append("<th>Valor anterior</th><th>Valor nuevo</th><th>Detalle</th></tr>")
                    .append("</thead><tbody>\n");
            for (BatchEvent event : summary.events()) {
                html.append("<tr>");
                cell(html, event.seq());
                cell(html, number(event.simTime()));
                cell(html, event.type());
                cell(html, event.source());
                cell(html, event.elementId());
                cell(html, event.state());
                cell(html, event.oldValue());
                cell(html, event.newValue());
                cell(html, event.details());
                html.append("</tr>\n");
            }
            html.append("</tbody></table>\n");
        }

        appendValues(html, summary.values());
        appendIncidents(html, summary.events());
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static void appendIdentification(StringBuilder html, BatchPayload payload) {
        List<String> units = new ArrayList<>(new LinkedHashSet<>(payload.unitOfStep().values()));
        html.append("<h2>Identificación</h2>\n<table><tbody>\n");
        row(html, "Batch", payload.batchId());
        row(html, "Receta", payload.sourceRecipeId());
        row(html, "Versión", payload.recipeVersion());
        row(html, "Unidades", units.isEmpty() ? "—" : String.join(", ", units));
        row(html, "Modo", "SIMULATED");
        row(html, "Perfil", profile(payload.profile()));
        html.append("</tbody></table>\n");
    }

    private static void appendTimes(StringBuilder html, BatchRunSummary summary) {
        html.append("<h2>Tiempos</h2>\n<table><tbody>\n");
        row(html, "Estado final", summary.outcome());
        row(html, "Inicio", time(summary.startWallTime()));
        row(html, "Fin", time(summary.endWallTime()));
        double simulated = summary.endSimTime() - summary.startSimTime();
        row(html, "Duración simulada (s)", simulated < 0 ? "—" : number(simulated));
        Duration wall = summary.startWallTime() == null || summary.endWallTime() == null
                ? null : Duration.between(summary.startWallTime(), summary.endWallTime());
        row(html, "Duración de pared (s)", wall == null ? "—" : number(wall.toMillis() / 1000.0));
        html.append("</tbody></table>\n");
    }

    private static void appendParameters(StringBuilder html, BatchPayload payload) {
        html.append("<h2>Parámetros y setpoints</h2>\n");
        if (payload.setpoints().isEmpty()) {
            html.append("<p class=\"muted\">Sin parámetros.</p>\n");
            return;
        }
        html.append("<table><thead><tr><th>Paso</th><th>Módulo</th><th>Variable</th>")
                .append("<th>Tipo</th><th>Valor</th><th>Unidad</th></tr></thead><tbody>\n");
        for (Map.Entry<String, List<PreparedSetpoint>> entry : payload.setpoints().entrySet()) {
            for (PreparedSetpoint setpoint : entry.getValue()) {
                html.append("<tr>");
                cell(html, entry.getKey());
                cell(html, setpoint.moduleId());
                cell(html, setpoint.variable());
                cell(html, setpoint.dataType());
                cell(html, setpoint.value());
                cell(html, setpoint.unit());
                html.append("</tr>\n");
            }
        }
        html.append("</tbody></table>\n");
    }

    private static void appendPhases(StringBuilder html, List<BatchEvent> events) {
        List<Phase> phases = phases(events);
        html.append("<h2>Tiempo por fase</h2>\n");
        if (phases.isEmpty()) {
            html.append("<p class=\"muted\">Sin fases.</p>\n");
            return;
        }
        html.append("<table><thead><tr><th>Paso</th><th>Inicio simulado</th>")
                .append("<th>Fin simulado</th><th>Duración (s)</th></tr></thead><tbody>\n");
        for (Phase phase : phases) {
            Double duration = phase.end() == null ? null
                    : phase.end().simTime() - phase.start().simTime();
            html.append("<tr data-sim-start=\"").append(phase.start().simTime())
                    .append("\" data-sim-end=\"")
                    .append(phase.end() == null ? "" : phase.end().simTime())
                    .append("\" data-sim-duration=\"")
                    .append(duration == null ? "" : duration).append("\">");
            cell(html, phase.key());
            cell(html, number(phase.start().simTime()));
            cell(html, phase.end() == null ? "—" : number(phase.end().simTime()));
            cell(html, duration == null ? "—" : number(duration));
            html.append("</tr>\n");
        }
        html.append("</tbody></table>\n");
    }

    private static List<Phase> phases(List<BatchEvent> events) {
        List<BatchEvent> starts = new ArrayList<>();
        Map<String, Deque<BatchEvent>> completions = new LinkedHashMap<>();
        for (BatchEvent event : events) {
            if (event.type() == EventType.STEP_ACTIVATED) {
                starts.add(event);
            } else if (event.type() == EventType.STEP_COMPLETED) {
                completions.computeIfAbsent(event.elementId(), ignored -> new ArrayDeque<>())
                        .addLast(event);
            }
        }
        List<Phase> phases = new ArrayList<>();
        for (BatchEvent start : starts) {
            Deque<BatchEvent> queue = completions.get(start.elementId());
            phases.add(new Phase(start.elementId(), start,
                    queue == null || queue.isEmpty() ? null : queue.removeFirst()));
        }
        return phases;
    }

    private static void appendRoute(StringBuilder html, RecipeFlowAnalyzer.Route route) {
        html.append("<h2>Recorrido planificado</h2>\n<pre>")
                .append(escape(RecipeFlowFormatter.toText(route))).append("</pre>\n");
        if (!route.warnings().isEmpty()) {
            html.append("<p class=\"muted\">Avisos: ").append(escape(String.join("; ",
                    route.warnings()))).append("</p>\n");
        }
    }

    private static void appendValues(StringBuilder html, List<ValuesRecorder.Sample> values) {
        html.append("<h2>Resumen de valores</h2>\n");
        if (values.isEmpty()) {
            html.append("<p class=\"muted\">Sin muestras.</p>\n");
            return;
        }
        Map<String, List<ValuesRecorder.Sample>> byName = new TreeMap<>();
        for (ValuesRecorder.Sample sample : values) {
            byName.computeIfAbsent(sample.variable(), ignored -> new ArrayList<>()).add(sample);
        }
        html.append("<table><thead><tr><th>Variable</th><th>Mínimo</th><th>Máximo</th>")
                .append("<th>Final</th><th>Muestras</th></tr></thead><tbody>\n");
        for (Map.Entry<String, List<ValuesRecorder.Sample>> entry : byName.entrySet()) {
            List<ValuesRecorder.Sample> samples = entry.getValue();
            Double min = null;
            Double max = null;
            for (ValuesRecorder.Sample sample : samples) {
                Double value = numberOrNull(sample.value());
                if (value == null) {
                    continue;
                }
                min = min == null ? value : Math.min(min, value);
                max = max == null ? value : Math.max(max, value);
            }
            ValuesRecorder.Sample last = samples.get(samples.size() - 1);
            html.append("<tr>");
            cell(html, entry.getKey());
            cell(html, min == null ? "—" : number(min));
            cell(html, max == null ? "—" : number(max));
            cell(html, last.value());
            cell(html, samples.size());
            html.append("</tr>\n");
        }
        html.append("</tbody></table>\n");
    }

    private static void appendIncidents(StringBuilder html, List<BatchEvent> events) {
        List<BatchEvent> incidents = new ArrayList<>();
        for (BatchEvent event : events) {
            if (INCIDENTS.contains(event.type())
                    || event.type() == EventType.BATCH_ABORTED) {
                incidents.add(event);
            }
        }
        html.append("<h2>Incidencias</h2>\n");
        if (incidents.isEmpty()) {
            html.append("<p class=\"muted\">Sin incidencias.</p>\n");
            return;
        }
        html.append("<table><thead><tr><th>Tiempo simulado</th><th>Tipo</th>")
                .append("<th>Origen</th><th>Elemento</th><th>Detalle</th></tr></thead><tbody>\n");
        for (BatchEvent event : incidents) {
            html.append("<tr>");
            cell(html, number(event.simTime()));
            cell(html, event.type());
            cell(html, event.source());
            cell(html, event.elementId());
            cell(html, event.details());
            html.append("</tr>\n");
        }
        html.append("</tbody></table>\n");
    }

    private static String profile(SimulationProfile profile) {
        if (profile == null) {
            profile = SimulationProfile.defaults();
        }
        return "fase por defecto " + number(profile.defaultPhaseSeconds()) + " s; "
                + "transitorios " + profile.transientTicks() + " ticks; "
                + "tick " + number(profile.tickSeconds()) + " s; "
                + profile.followRules().size() + " reglas de seguimiento";
    }

    private static void row(StringBuilder html, String name, String value) {
        html.append("<tr>");
        cell(html, name);
        cell(html, value);
        html.append("</tr>\n");
    }

    private static void cell(StringBuilder html, Object value) {
        html.append("<td>").append(escape(value == null ? null : value.toString())).append("</td>");
    }

    private static String time(Instant instant) {
        return instant == null ? "—" : TIME.format(instant);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Double numberOrNull(String value) {
        try {
            return value == null ? null : Double.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException noAtomicMove) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Phase(String key, BatchEvent start, BatchEvent end) {
    }
}
