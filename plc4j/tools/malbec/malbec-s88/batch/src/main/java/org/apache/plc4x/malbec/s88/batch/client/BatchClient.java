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
package org.apache.plc4x.malbec.s88.batch.client;

import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.model.BatchCommand;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;
import org.apache.plc4x.malbec.s88.batch.model.CommandResult;

/**
 * A batch seen through a link. The runtime and the user interface use only this interface.
 */
public interface BatchClient extends AutoCloseable {

    /** Makes a prepared payload the batch this client observes. */
    void load(BatchPayload payload);

    /** Opens the link and starts receiving snapshots. */
    void connect();

    /** Sends a command and waits for the programmable controller to acknowledge it. */
    CommandResult sendCommand(BatchCommand command);

    void addListener(BatchUpdateListener listener);

    void removeListener(BatchUpdateListener listener);

    /** The last snapshot the client built, {@code null} before the first one. */
    BatchSnapshot lastSnapshot();

    /** Closes the link and stops receiving updates. */
    void disconnect();

    @Override
    default void close() {
        disconnect();
    }
}
