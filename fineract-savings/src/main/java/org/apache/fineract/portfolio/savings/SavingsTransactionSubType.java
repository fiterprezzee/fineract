/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings;

/**
 * Enum representing the sub-type of a savings account transaction. Used to distinguish between normal transactions and
 * those related to hold/release operations.
 */
public enum SavingsTransactionSubType {

    NORMAL("NORMAL"), HOLD_RELEASE_WITHDRAWAL("HOLD_RELEASE_WITHDRAWAL"), HOLD_REVERSAL("HOLD_REVERSAL"), WITHDRAWAL_REVERSAL(
            "WITHDRAWAL_REVERSAL"), RELEASE_REVERSAL("RELEASE_REVERSAL");

    private final String value;

    SavingsTransactionSubType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static SavingsTransactionSubType fromString(final String value) {
        if (value == null) {
            return NORMAL;
        }
        for (SavingsTransactionSubType subType : values()) {
            if (subType.value.equalsIgnoreCase(value)) {
                return subType;
            }
        }
        return NORMAL;
    }

    public boolean isHoldReleaseWithdrawal() {
        return this == HOLD_RELEASE_WITHDRAWAL;
    }

    public boolean isHoldReversal() {
        return this == HOLD_REVERSAL;
    }

    public boolean isWithdrawalReversal() {
        return this == WITHDRAWAL_REVERSAL;
    }

    public boolean isReleaseReversal() {
        return this == RELEASE_REVERSAL;
    }
}
