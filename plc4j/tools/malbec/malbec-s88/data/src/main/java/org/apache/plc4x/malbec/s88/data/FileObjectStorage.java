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
package org.apache.plc4x.malbec.s88.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.openide.filesystems.FileObject;

/**
 * Storage that reads and writes one file of a NetBeans project.
 * <p>
 * Shared rather than repeated. A plant file and a recipe file are both a single file inside the
 * project, and both repositories take an {@link S88Storage} rather than knowing about projects, so
 * this is the one place that has to know how a {@link FileObject} turns into streams.
 * <p>
 * It lives here, next to the repositories that take it, rather than in the model: the model is
 * plain Java with no NetBeans in it, and one of the two halves of this tool is the runtime that
 * will read these files without a project in sight at all.
 */
public record FileObjectStorage(FileObject fileObject) implements S88Storage {

    @Override
    public InputStream openInput() throws IOException {
        return fileObject.getInputStream();
    }

    @Override
    public OutputStream openOutput() throws IOException {
        return fileObject.getOutputStream();
    }
}
