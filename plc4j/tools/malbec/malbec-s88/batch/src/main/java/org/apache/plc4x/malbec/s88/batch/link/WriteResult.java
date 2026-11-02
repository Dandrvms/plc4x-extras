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

/**
 * The outcome of a write to a process link.
 *
 * @param name    published name of the variable
 * @param success whether the write completed
 * @param error   the reason of a failed write, or {@code null}
 */
public record WriteResult(String name, boolean success, String error) {

    public static WriteResult ok(String name) {
        return new WriteResult(name, true, null);
    }

    public static WriteResult failed(String name, String error) {
        return new WriteResult(name, false, error);
    }
}
