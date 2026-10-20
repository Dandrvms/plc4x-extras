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
package org.apache.plc4x.malbec.s88.core;

/** Where a step goes when the box it is put after is a branch point. */
public enum InsertMode {

    /** The step goes in front of the branch point, so every branch still leaves after it. */
    BEFORE_FORK,

    /** The step goes into one branch, chosen by the transition named with the mode. */
    IN_BRANCH,

    /** The step goes after the branches come back together. */
    AFTER_JOIN
}
