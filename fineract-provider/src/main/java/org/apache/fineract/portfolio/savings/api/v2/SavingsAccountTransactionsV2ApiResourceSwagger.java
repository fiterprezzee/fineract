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
package org.apache.fineract.portfolio.savings.api.v2;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Swagger documentation for V2 Savings Account Transactions API.
 */
public final class SavingsAccountTransactionsV2ApiResourceSwagger {

    private SavingsAccountTransactionsV2ApiResourceSwagger() {}

    @Schema(description = "V2 Release Amount Request")
    public static final class ReleaseAmountV2Request {

        private String locale;
        private String dateFormat;
        private String transactionDate;
        private String transactionAmount;
        private Long paymentTypeId;
        private String note;

        private ReleaseAmountV2Request() {}

        @Schema(example = "en")
        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        @Schema(example = "dd MMMM yyyy")
        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        @Schema(example = "15 May 2026")
        public String getTransactionDate() {
            return transactionDate;
        }

        public void setTransactionDate(String transactionDate) {
            this.transactionDate = transactionDate;
        }

        @Schema(description = "Optional settlement amount. Defaults to the hold amount when omitted.", example = "105.00")
        public String getTransactionAmount() {
            return transactionAmount;
        }

        public void setTransactionAmount(String transactionAmount) {
            this.transactionAmount = transactionAmount;
        }

        @Schema(example = "1")
        public Long getPaymentTypeId() {
            return paymentTypeId;
        }

        public void setPaymentTypeId(Long paymentTypeId) {
            this.paymentTypeId = paymentTypeId;
        }

        @Schema(example = "Release and withdraw funds")
        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    @Schema(description = "V2 Release Amount Response")
    public static final class ReleaseAmountV2Response {

        private Long officeId;
        private Long clientId;
        private Long savingsId;
        private Long resourceId;
        private ReleaseAmountV2Changes changes;

        private ReleaseAmountV2Response() {}

        @Schema(example = "1")
        public Long getOfficeId() {
            return officeId;
        }

        public void setOfficeId(Long officeId) {
            this.officeId = officeId;
        }

        @Schema(example = "1")
        public Long getClientId() {
            return clientId;
        }

        public void setClientId(Long clientId) {
            this.clientId = clientId;
        }

        @Schema(example = "1")
        public Long getSavingsId() {
            return savingsId;
        }

        public void setSavingsId(Long savingsId) {
            this.savingsId = savingsId;
        }

        @Schema(example = "100")
        public Long getResourceId() {
            return resourceId;
        }

        public void setResourceId(Long resourceId) {
            this.resourceId = resourceId;
        }

        public ReleaseAmountV2Changes getChanges() {
            return changes;
        }

        public void setChanges(ReleaseAmountV2Changes changes) {
            this.changes = changes;
        }
    }

    @Schema(description = "V2 Release Amount Changes")
    public static final class ReleaseAmountV2Changes {

        private Long holdTransactionId;
        private Long releaseTransactionId;
        private Long withdrawalTransactionId;
        private String holdAmount;
        private String settlementAmount;

        private ReleaseAmountV2Changes() {}

        @Schema(example = "50")
        public Long getHoldTransactionId() {
            return holdTransactionId;
        }

        public void setHoldTransactionId(Long holdTransactionId) {
            this.holdTransactionId = holdTransactionId;
        }

        @Schema(example = "100")
        public Long getReleaseTransactionId() {
            return releaseTransactionId;
        }

        public void setReleaseTransactionId(Long releaseTransactionId) {
            this.releaseTransactionId = releaseTransactionId;
        }

        @Schema(example = "101")
        public Long getWithdrawalTransactionId() {
            return withdrawalTransactionId;
        }

        public void setWithdrawalTransactionId(Long withdrawalTransactionId) {
            this.withdrawalTransactionId = withdrawalTransactionId;
        }

        @Schema(example = "100.00")
        public String getHoldAmount() {
            return holdAmount;
        }

        public void setHoldAmount(String holdAmount) {
            this.holdAmount = holdAmount;
        }

        @Schema(example = "105.00")
        public String getSettlementAmount() {
            return settlementAmount;
        }

        public void setSettlementAmount(String settlementAmount) {
            this.settlementAmount = settlementAmount;
        }
    }
}
