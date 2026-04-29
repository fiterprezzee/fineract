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
package org.apache.fineract.accounting.journalentry.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionEnumData;

@Getter
public class SavingsTransactionDTO {

    private final Long officeId;
    private final Long paymentTypeId;
    private final String transactionId;
    private final LocalDate transactionDate;
    private final SavingsAccountTransactionEnumData transactionType;

    private final BigDecimal amount;

    /*** Boolean values determines if the transaction is reversed ***/
    private final boolean reversed;

    /** Breakdowns of fees and penalties this Transaction pays **/
    private final List<ChargePaymentDTO> feePayments;
    private final List<ChargePaymentDTO> penaltyPayments;
    private final BigDecimal overdraftAmount;
    private final boolean isAccountTransfer;
    private final List<TaxPaymentDTO> taxPayments;

    // Hold & Release Enhancement fields
    private final boolean isFromHoldRelease;
    private final Long holdTransactionId;
    private final boolean isHoldGLPosted;
    private final Long holdFundsOnHoldAccountId;
    private final Long holdSavingsControlAccountId;

    /**
     * Original constructor for backward compatibility
     */
    public SavingsTransactionDTO(final Long officeId, final Long paymentTypeId, final String transactionId, final LocalDate transactionDate,
            final SavingsAccountTransactionEnumData transactionType, final BigDecimal amount, final boolean reversed,
            final List<ChargePaymentDTO> feePayments, final List<ChargePaymentDTO> penaltyPayments, final BigDecimal overdraftAmount,
            final boolean isAccountTransfer, final List<TaxPaymentDTO> taxPayments) {
        this(officeId, paymentTypeId, transactionId, transactionDate, transactionType, amount, reversed, feePayments, penaltyPayments,
                overdraftAmount, isAccountTransfer, taxPayments, false, null, false, null, null);
    }

    /**
     * Full constructor with Hold & Release Enhancement fields
     */
    public SavingsTransactionDTO(final Long officeId, final Long paymentTypeId, final String transactionId, final LocalDate transactionDate,
            final SavingsAccountTransactionEnumData transactionType, final BigDecimal amount, final boolean reversed,
            final List<ChargePaymentDTO> feePayments, final List<ChargePaymentDTO> penaltyPayments, final BigDecimal overdraftAmount,
            final boolean isAccountTransfer, final List<TaxPaymentDTO> taxPayments, final boolean isFromHoldRelease,
            final Long holdTransactionId, final boolean isHoldGLPosted, final Long holdFundsOnHoldAccountId,
            final Long holdSavingsControlAccountId) {
        this.officeId = officeId;
        this.paymentTypeId = paymentTypeId;
        this.transactionId = transactionId;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.amount = amount;
        this.reversed = reversed;
        this.feePayments = feePayments;
        this.penaltyPayments = penaltyPayments;
        this.overdraftAmount = overdraftAmount;
        this.isAccountTransfer = isAccountTransfer;
        this.taxPayments = taxPayments;
        this.isFromHoldRelease = isFromHoldRelease;
        this.holdTransactionId = holdTransactionId;
        this.isHoldGLPosted = isHoldGLPosted;
        this.holdFundsOnHoldAccountId = holdFundsOnHoldAccountId;
        this.holdSavingsControlAccountId = holdSavingsControlAccountId;
    }

    public boolean isOverdraftTransaction() {
        return this.overdraftAmount != null && this.overdraftAmount.doubleValue() > 0;
    }

    public boolean isFromHoldRelease() {
        return this.isFromHoldRelease;
    }

    public boolean isHoldGLPosted() {
        return this.isHoldGLPosted;
    }
}
