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
public final class SavingsAccountTransactionsApiResourceV2Swagger {

    private SavingsAccountTransactionsApiResourceV2Swagger() {}

    @Schema(description = "V2 Release Amount Request")
    public static final class ReleaseAmountV2Request {

        private ReleaseAmountV2Request() {}

        @Schema(example = "en")
        public String locale;
        @Schema(example = "dd MMMM yyyy")
        public String dateFormat;
        @Schema(example = "15 May 2026")
        public String transactionDate;
        @Schema(example = "Release and withdraw funds")
        public String note;
    }

    @Schema(description = "V2 Release Amount Response")
    public static final class ReleaseAmountV2Response {

        private ReleaseAmountV2Response() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "1")
        public Long savingsId;
        @Schema(example = "100")
        public Long resourceId;
        public ReleaseAmountV2Changes changes;
    }

    @Schema(description = "V2 Release Amount Changes")
    public static final class ReleaseAmountV2Changes {

        private ReleaseAmountV2Changes() {}

        @Schema(example = "50")
        public Long holdTransactionId;
        @Schema(example = "100")
        public Long releaseTransactionId;
        @Schema(example = "101")
        public Long withdrawalTransactionId;
    }
}
