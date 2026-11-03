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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableCatalog;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.VariableSample;

/** Samples the variables worth keeping in {@code values.csv}. */
public final class ValuesRecorder implements AutoCloseable {

    private static final String[] HEADERS = {
        "simTime", "wallTime", "variable", "value", "quality"
    };
    private static final double SAMPLE_SECONDS = 5.0;
    private static final double RELATIVE_CHANGE = 0.005;
    private static final int MAX_ROWS = 200_000;

    private final Writer writer;
    private final CSVPrinter printer;
    private final Set<String> watch;
    private final Map<String, LastSample> last = new LinkedHashMap<>();
    private final List<Sample> samples = new ArrayList<>();

    /**
     * @param watch variables to record, or the reports of the payload when {@code null}
     */
    public ValuesRecorder(RunFolder folder, BatchPayload payload, Set<String> watch)
            throws IOException {
        this.writer = Files.newBufferedWriter(folder.valuesFile(), StandardCharsets.UTF_8);
        this.printer = new CSVPrinter(writer, CSVFormat.DEFAULT);
        printer.printRecord((Object[]) HEADERS);
        printer.flush();
        this.watch = watch == null ? defaultWatch(payload)
                : Collections.unmodifiableSet(new LinkedHashSet<>(watch));
    }

    /** Records the samples of one snapshot that pass the sampling policy. */
    public synchronized void record(BatchSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            return;
        }
        for (String name : watch) {
            VariableSample sample = snapshot.variables().get(name);
            if (sample == null) {
                continue;
            }
            if (samples.size() >= MAX_ROWS) {
                return;
            }
            if (!shouldRecord(name, sample, snapshot.simTime())) {
                continue;
            }
            Sample row = new Sample(snapshot.simTime(), sample.sourceTime(), name,
                    text(sample.value()), sample.quality());
            printer.printRecord(row.simTime(), row.wallTime(), row.variable(), row.value(),
                    row.quality().name());
            printer.flush();
            samples.add(row);
        }
    }

    public synchronized List<Sample> samples() {
        return List.copyOf(samples);
    }

    @Override
    public synchronized void close() throws IOException {
        printer.close();
        writer.close();
    }

    private boolean shouldRecord(String name, VariableSample sample, double simTime) {
        LastSample previous = last.get(name);
        if (previous == null || simTime < previous.simTime
                || simTime - previous.simTime >= SAMPLE_SECONDS) {
            remember(name, sample, simTime);
            return true;
        }
        if (isNumeric(sample)) {
            Double current = number(sample.value());
            if (current == null) {
                return false;
            }
            if (previous.number == null) {
                remember(name, sample, simTime);
                return true;
            }
            double change = Math.abs(current - previous.number);
            double threshold = Math.abs(previous.number) * RELATIVE_CHANGE;
            if (change > threshold) {
                remember(name, sample, simTime);
                return true;
            }
            return false;
        }
        if (!Objects.equals(previous.text, text(sample.value()))) {
            remember(name, sample, simTime);
            return true;
        }
        return false;
    }

    private void remember(String name, VariableSample sample, double simTime) {
        last.put(name, new LastSample(simTime, number(sample.value()), text(sample.value())));
    }

    private static Set<String> defaultWatch(BatchPayload payload) {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, VariableCatalog.Definition> entry
                : VariableCatalog.reportsOf(payload).entrySet()) {
            DataType type = entry.getValue().type();
            if (entry.getKey().startsWith("STATE_") || type == DataType.INTEGER
                    || type == DataType.REAL) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static boolean isNumeric(VariableSample sample) {
        return sample.type() == DataType.INTEGER || sample.type() == DataType.REAL;
    }

    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private record LastSample(double simTime, Double number, String text) {
    }

    /** One row of {@code values.csv}. */
    public record Sample(double simTime, Instant wallTime, String variable, String value,
                         Quality quality) {
    }
}

