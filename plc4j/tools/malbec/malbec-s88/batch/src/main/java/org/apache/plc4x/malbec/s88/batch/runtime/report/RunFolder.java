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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The files of one batch run.
 *
 * @param outputRoot directory that holds every batch
 * @param run        run number, starting at one
 */
public record RunFolder(Path outputRoot, String batchId, int run) {

    public RunFolder {
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(batchId, "batchId");
        if (batchId.isBlank()) {
            throw new IllegalArgumentException("A run folder needs a batch id.");
        }
        if (run <= 0) {
            throw new IllegalArgumentException("A run number starts at one.");
        }
    }

    /** Creates the run directory. */
    public static RunFolder create(Path outputRoot, String batchId, int run) throws IOException {
        RunFolder folder = new RunFolder(outputRoot, batchId, run);
        Files.createDirectories(folder.directory());
        return folder;
    }

    public Path directory() {
        return outputRoot.resolve(batchId).resolve("run-" + run);
    }

    public Path eventsFile() {
        return directory().resolve("events.csv");
    }

    public Path valuesFile() {
        return directory().resolve("values.csv");
    }

    public Path routeFile() {
        return directory().resolve("route.txt");
    }

    public Path reportFile() {
        return directory().resolve("report.html");
    }

    public Path engineeringReportFile() {
        return directory().resolve("report-engineering.html");
    }

    public Path manifestFile() {
        return directory().resolve("manifest.txt");
    }
}
