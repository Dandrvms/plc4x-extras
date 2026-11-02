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
package org.apache.plc4x.malbec.s88.batch.link;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.api.DataType;

/**
 * A link to process variables, independent of the protocol behind it.
 */
public interface ProcessLink extends AutoCloseable {

    /**
     * Opens the link. The call is idempotent and does not block.
     */
    void connect();

    LinkStatus status();

    void addStatusListener(Consumer<LinkStatus> listener);

    /**
     * Subscribes to a variable until the returned handle is closed.
     */
    AutoCloseable subscribe(String name, DataType type, Consumer<VariableUpdate> listener);

    /**
     * Reads a variable once and delivers the result asynchronously.
     */
    void read(String name, DataType type, Consumer<VariableUpdate> once);

    /**
     * Writes a variable and completes when the source reports success or failure.
     */
    CompletableFuture<WriteResult> write(String name, DataType type, Object value);

    @Override
    void close();
}
