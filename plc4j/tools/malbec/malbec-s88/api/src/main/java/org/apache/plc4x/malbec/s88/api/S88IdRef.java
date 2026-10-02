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
package org.apache.plc4x.malbec.s88.api;

/**
 * One end of an {@link S88ProcedureLink}, given as a name plus what to read it as and where to
 * look for it.
 * <p>
 * The recipe format writes the two ends of a link with the same three fields.
 * {@link S88IdRefType} says whether the name belongs to a box or a bar, and
 * {@link S88IdScope} says whether to look for it inside this
 * procedure or somewhere outside it.
 */
public class S88IdRef {

    private String value;
    private S88IdRefType type = S88IdRefType.STEP;
    private S88IdScope scope = S88IdScope.INTERNAL;

    public S88IdRef() {
    }

    public S88IdRef(String value, S88IdRefType type) {
        this.value = value;
        setType(type);
    }

    public S88IdRef(String value, S88IdRefType type, S88IdScope scope) {
        this.value = value;
        setType(type);
        setScope(scope);
    }

    /** An end pointing at a step of this same procedure, the ordinary case. */
    public static S88IdRef step(String value) {
        return new S88IdRef(value, S88IdRefType.STEP, S88IdScope.INTERNAL);
    }

    /** An end pointing at a bar of this same procedure. */
    public static S88IdRef transition(String value) {
        return new S88IdRef(value, S88IdRefType.TRANSITION, S88IdScope.INTERNAL);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public S88IdRefType getType() {
        return type;
    }

    public void setType(S88IdRefType type) {
        this.type = type != null ? type : S88IdRefType.STEP;
    }

    public S88IdScope getScope() {
        return scope;
    }

    public void setScope(S88IdScope scope) {
        this.scope = scope != null ? scope : S88IdScope.INTERNAL;
    }

    /** True when the name is to be looked for inside the procedure that holds this reference. */
    public boolean isInternal() {
        return scope == S88IdScope.INTERNAL;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof S88IdRef that)) {
            return false;
        }
        return java.util.Objects.equals(value, that.value) && type == that.type && scope == that.scope;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(value, type, scope);
    }

    @Override
    public String toString() {
        return type + ":" + value + (isInternal() ? "" : " (" + scope + ")");
    }
}
