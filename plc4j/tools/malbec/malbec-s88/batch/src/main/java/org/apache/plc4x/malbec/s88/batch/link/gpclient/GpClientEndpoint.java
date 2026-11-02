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
package org.apache.plc4x.malbec.s88.batch.link.gpclient;

import org.epics.gpclient.GPClientInstance;

/** One GPClient instance and the links built on it. */
public final class GpClientEndpoint implements AutoCloseable {

    private final GpClientLinkConfig config;
    private final GPClientInstance client;

    public GpClientEndpoint(GpClientLinkConfig config) {
        this.config = config;
        this.client = config.newClient();
    }

    public GpClientProcessLink newLink() {
        return config.newLink(client);
    }

    @Override
    public void close() {
        client.close();
    }
}
