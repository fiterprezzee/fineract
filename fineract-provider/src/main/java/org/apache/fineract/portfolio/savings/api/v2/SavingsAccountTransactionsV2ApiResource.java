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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionData;
import org.springframework.stereotype.Component;

/**
 * V2 Savings Account Transactions API Resource.
 *
 * This V2 endpoint provides a combined release + withdraw operation in a single API call.
 *
 * Key differences from V1: - V1 releaseAmount: Performs release only (no withdrawal, no journal entries) - V2
 * releaseAmount: Performs release + withdraw in one atomic transaction (journal entries created during withdrawal)
 *
 * Transaction linkage is maintained: holdId → releaseId → withdrawalId
 *
 * Clients can switch between V1 and V2 by changing the API version in the endpoint path only.
 */
@Path("/v2/savingsaccounts/{savingsId}/transactions")
@Component
@Tag(name = "Savings Account Transactions V2", description = "V2 Savings Account Transaction operations with combined release and withdraw")
@RequiredArgsConstructor
public class SavingsAccountTransactionsV2ApiResource {

    private final DefaultToApiJsonSerializer<SavingsAccountTransactionData> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private boolean is(final String commandParam, final String commandValue) {
        return StringUtils.isNotBlank(commandParam) && commandParam.trim().equalsIgnoreCase(commandValue);
    }

    /**
     * V2 Release Amount endpoint - performs release + withdraw in a single atomic transaction.
     *
     * This endpoint: 1. Releases the hold amount (no journal entry - same as V1) 2. Creates a withdrawal transaction
     * (journal entries created here) 3. Links all transaction IDs: holdId → releaseId → withdrawalId
     *
     * Journal entries are only created during the withdrawal phase, consistent with accounting requirements.
     *
     * @param savingsId
     *            The savings account ID
     * @param transactionId
     *            The hold transaction ID to release
     * @param commandParam
     *            The command (releaseAmount)
     * @param apiRequestBodyAsJson
     *            The request body
     * @return CommandProcessingResult with releaseTransactionId, withdrawalTransactionId, and holdTransactionId
     */
    @POST
    @Path("{transactionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "V2 Release Amount with Withdrawal", description = "Performs hold release and fund withdrawal in a single atomic transaction.\n\n"
            + "Transaction Flow:\n" + "1. Release hold (no journal entry)\n"
            + "2. Create withdrawal transaction (journal entries posted here)\n\n" + "Response includes all linked transaction IDs:\n"
            + "- holdTransactionId: Original hold transaction\n" + "- releaseTransactionId: The release transaction created\n"
            + "- withdrawalTransactionId: The withdrawal transaction created\n\n" + "Example Request:\n"
            + "POST /v2/savingsaccounts/{savingsId}/transactions/{transactionId}?command=releaseAmount\n\n"
            + "Accepted command = releaseAmount")
    @RequestBody(required = false, content = @Content(schema = @Schema(implementation = SavingsAccountTransactionsV2ApiResourceSwagger.ReleaseAmountV2Request.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SavingsAccountTransactionsV2ApiResourceSwagger.ReleaseAmountV2Response.class))) })
    public String releaseAmountWithWithdrawal(
            @PathParam("savingsId") @Parameter(description = "The savings account ID") final Long savingsId,
            @PathParam("transactionId") @Parameter(description = "The hold transaction ID to release") final Long transactionId,
            @QueryParam("command") @Parameter(description = "The command to execute (releaseAmount)") final String commandParam,
            final String apiRequestBodyAsJson) {

        String jsonApiRequest = apiRequestBodyAsJson;
        if (StringUtils.isBlank(jsonApiRequest)) {
            jsonApiRequest = "{}";
        }

        final CommandWrapperBuilder builder = new CommandWrapperBuilder().withJson(jsonApiRequest);

        CommandProcessingResult result = null;
        if (is(commandParam, SavingsApiConstants.COMMAND_RELEASE_AMOUNT)) {
            // V2 uses the releaseAmountWithWithdrawal command which performs both operations
            final CommandWrapper commandRequest = builder.releaseAmountWithWithdrawal(savingsId, transactionId).build();
            result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        }

        if (result == null) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { SavingsApiConstants.COMMAND_RELEASE_AMOUNT });
        }

        return this.toApiJsonSerializer.serialize(result);
    }
}
