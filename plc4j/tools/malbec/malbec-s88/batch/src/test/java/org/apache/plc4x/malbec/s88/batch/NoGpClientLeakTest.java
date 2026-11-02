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
package org.apache.plc4x.malbec.s88.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoGpClientLeakTest {

    private static final Pattern GPCLIENT_IMPORT = Pattern.compile(
        "^\\s*import\\s+(?:static\\s+)?org\\.epics\\b.*$",
        Pattern.MULTILINE);

    @Test
    void onlyTheGpClientPackageImportsEpics() throws IOException {
        Path sources = Path.of(System.getProperty("basedir", "."), "src", "main", "java");
        assertTrue(Files.isDirectory(sources), sources + " does not exist");

        List<Path> files;
        try (Stream<Path> walk = Files.walk(sources)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).toList();
        }

        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            String relative = sources.relativize(file).toString().replace('\\', '/');
            if (relative.contains("link/gpclient/")) {
                continue;
            }
            Matcher matcher = GPCLIENT_IMPORT.matcher(Files.readString(file));
            while (matcher.find()) {
                offenders.add(relative + ": " + matcher.group());
            }
        }

        assertTrue(offenders.isEmpty(), () -> "GPClient imports outside link.gpclient:\n" + String.join("\n", offenders));
    }
}
