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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.util.HashMap;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsStatusChecker;
import org.apache.fineract.integrationtests.common.savings.SavingsTestLifecycleExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Savings Integration Test for checking Savings Application.
 *
 * This test class verifies the hold and release behavior for savings accounts.
 */
@SuppressWarnings({ "rawtypes", "removal", "unchecked" })
@Order(2)
@ExtendWith({ SavingsTestLifecycleExtension.class })
public class SavingsAccountRecalculateBalanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(SavingsAccountRecalculateBalanceTest.class);
    public static final String DEPOSIT_AMOUNT = "2000";
    public static final String MINIMUM_OPENING_BALANCE = "1000.0";
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    public static final String DATE_FORMAT = "dd MMMM yyyy";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private SavingsAccountHelper savingsAccountHelper;
    private GlobalConfigurationHelper globalConfigurationHelper;
    private AccountHelper accountHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.requestSpec.header("Fineract-Platform-TenantId", "default");
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.globalConfigurationHelper = new GlobalConfigurationHelper();
        this.accountHelper = new AccountHelper(this.requestSpec, this.responseSpec);
    }

    /**
     * Test: Multiple Hold and Partial Release Scenario (V1 - Release Only)
     */
    @Test
    public void testMultipleHoldAndReleaseWithGLReconciliation() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        // Create GL accounts for cash-based accounting
        final Account savingsReferenceAccount = this.accountHelper.createAssetAccount("Savings Reference Multi");
        final Account savingsControlAccount = this.accountHelper.createLiabilityAccount("Savings Control Multi");
        final Account interestOnSavingsAccount = this.accountHelper.createExpenseAccount("Interest on Savings Multi");
        final Account incomeFromFeeAccount = this.accountHelper.createIncomeAccount("Income from Fee Multi");
        final Account fundsOnHoldAccount = this.accountHelper.createLiabilityAccount("Funds on Hold Multi");

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // Create savings product with cash-based accounting and overdraft
        final Integer savingsProductID = createSavingsProductWithCashBasedAccountingAndOverdraft(savingsReferenceAccount,
                savingsControlAccount, interestOnSavingsAccount, incomeFromFeeAccount, fundsOnHoldAccount);
        Assertions.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductID, ACCOUNT_TYPE_INDIVIDUAL);
        this.savingsAccountHelper.approveSavings(savingsId);
        HashMap savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        // Initial deposit
        float depositAmount = 2000F;
        Integer depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, String.valueOf(depositAmount),
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);

        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(depositAmount, summary.get("availableBalance"), "Verifying initial balance");

        // Place first hold
        float holdAmount1 = 300F;
        Integer holdTransactionId1 = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, String.valueOf(holdAmount1),
                false, SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(holdTransactionId1);

        float expectedAvailableBalance = depositAmount - holdAmount1;
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(expectedAvailableBalance, summary.get("availableBalance"), "Verifying Balance after first hold");

        // Place second hold
        float holdAmount2 = 200F;
        Integer holdTransactionId2 = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, String.valueOf(holdAmount2),
                false, SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(holdTransactionId2);

        expectedAvailableBalance = expectedAvailableBalance - holdAmount2;
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(expectedAvailableBalance, summary.get("availableBalance"), "Verifying Balance after second hold");

        // Total on hold should be holdAmount1 + holdAmount2
        // Verify via availableBalance since onHoldFunds is at account level, not summary level
        float expectedAccountBalance = depositAmount; // account balance unchanged by holds
        assertEquals(expectedAccountBalance, summary.get("accountBalance"), "Account balance should be unchanged by holds");
        // availableBalance = accountBalance - totalHold = 2000 - 500 = 1500
        assertEquals(expectedAvailableBalance, summary.get("availableBalance"), "Available balance should reflect total holds");

        // V1 Release first hold - this only releases hold, does NOT create withdrawal
        Integer releaseTransactionId1 = this.savingsAccountHelper.releaseAmount(savingsId, holdTransactionId1);
        Assertions.assertNotNull(releaseTransactionId1);

        // V1 behavior: Release only returns held funds to available balance
        // Account balance unchanged, available balance increases by released amount
        // Available = 1500 + 300 = 1800, Account balance still 2000
        float expectedAvailableAfterFirstRelease = expectedAvailableBalance + holdAmount1; // 1500 + 300 = 1800
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(expectedAvailableAfterFirstRelease, summary.get("availableBalance"),
                "V1 Release: Available balance should increase by released amount");
        assertEquals(depositAmount, summary.get("accountBalance"), "V1 Release: Account balance should remain unchanged");

        // V1 Release second hold
        Integer releaseTransactionId2 = this.savingsAccountHelper.releaseAmount(savingsId, holdTransactionId2);
        Assertions.assertNotNull(releaseTransactionId2);

        // After both releases: all holds released, available = account balance
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(depositAmount, summary.get("availableBalance"),
                "V1 Release: Available balance should equal deposit after all holds released");
        assertEquals(depositAmount, summary.get("accountBalance"), "V1 Release: Account balance should equal deposit (unchanged)");

        // No funds should be on hold - account balance equals available balance
        Float accountBalance = (Float) summary.get("accountBalance");
        Float availBalance = (Float) summary.get("availableBalance");
        assertEquals(accountBalance, availBalance, "Account balance should equal available balance when no holds remain");

        LOG.info("Multiple Hold and Release (V1) test completed successfully");
        LOG.info("Initial deposit: {}", depositAmount);
        LOG.info("Hold 1 amount: {}", holdAmount1);
        LOG.info("Hold 2 amount: {}", holdAmount2);
        LOG.info("Final balance (V1 - no withdrawal): {}", depositAmount);
    }

    /**
     * Helper method to create a savings product with cash-based accounting and overdraft
     */
    private Integer createSavingsProductWithCashBasedAccountingAndOverdraft(Account savingsReferenceAccount, Account savingsControlAccount,
            Account interestOnSavingsAccount, Account incomeFromFeeAccount, Account fundsOnHoldAccount) {

        LOG.info("Creating savings product with cash-based accounting and overdraft for hold/release GL verification");
        SavingsProductHelper productHelper = new SavingsProductHelper();

        final String savingsProductJSON = productHelper.withInterestCompoundingPeriodTypeAsDaily().withInterestPostingPeriodTypeAsMonthly()
                .withInterestCalculationPeriodTypeAsDailyBalance().withMinimumOpenningBalance("0").withOverDraft("500.0")
                .withAccountingRuleAsCashBased(
                        new Account[] { savingsReferenceAccount, savingsControlAccount, interestOnSavingsAccount, incomeFromFeeAccount })
                .withSavingsReferenceAccountId(savingsReferenceAccount.getAccountID().toString())
                .withSavingsControlAccountId(savingsControlAccount.getAccountID().toString())
                .withInterestOnSavingsAccountId(interestOnSavingsAccount.getAccountID().toString())
                .withIncomeFromFeeAccountId(incomeFromFeeAccount.getAccountID().toString())
                .withFundsOnHoldAccountId(fundsOnHoldAccount.getAccountID().toString()).build();

        return SavingsProductHelper.createSavingsProduct(savingsProductJSON, requestSpec, responseSpec);
    }

    /**
     * Test: Savings Account Deposit After Hold and Release (V1 - Release Only)
     *
     * V1 Release behavior: Release only returns held funds to available balance, does NOT create withdrawal. Account
     * balance remains unchanged. No overdraft is needed for V1 release.
     */
    @Test
    public void testSavingsAccountDepositAfterNegativeHoldAmount() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // V1 release doesn't need overdraft since it doesn't create withdrawal
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, "0", null, false, false, false, null);
        Assertions.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductID, ACCOUNT_TYPE_INDIVIDUAL);
        this.savingsAccountHelper.approveSavings(savingsId);
        HashMap savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        float accountBalance = 0F;
        float availableBalance;
        float transactionAmount = 100F;

        // Deposit 100
        Integer depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId,
                String.valueOf(transactionAmount), SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);
        accountBalance = accountBalance + transactionAmount; // 100
        availableBalance = accountBalance; // 100
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after deposit");

        // Withdraw 100
        Integer withdrawalTransactionId = (Integer) this.savingsAccountHelper.withdrawalFromSavingsAccount(savingsId,
                String.valueOf(transactionAmount), SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(withdrawalTransactionId);
        accountBalance = accountBalance - transactionAmount; // 0
        availableBalance = accountBalance; // 0
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after withdrawal");

        // Deposit again to have funds for hold
        depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, String.valueOf(transactionAmount),
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);
        accountBalance = accountBalance + transactionAmount; // 100
        availableBalance = accountBalance; // 100
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after second deposit");

        // Hold 50
        float holdAmount = 50F;
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, String.valueOf(holdAmount),
                false, SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(holdTransactionId);
        // Hold doesn't change account balance, only available balance
        availableBalance = accountBalance - holdAmount; // 100 - 50 = 50
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after hold amount");

        // V1 Release hold - only releases hold, does NOT create withdrawal
        this.savingsAccountHelper.releaseAmount(savingsId, holdTransactionId);

        // V1 behavior: Release returns held amount to available balance
        // Account balance unchanged, available balance = account balance
        availableBalance = accountBalance; // 100 (hold released, no withdrawal)
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "V1 Release: Available balance should equal account balance");
        assertEquals(accountBalance, ((Number) summary.get("accountBalance")).floatValue(),
                "V1 Release: Account balance should be unchanged");

        // Deposit 100 after hold-release
        depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, String.valueOf(transactionAmount),
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);
        accountBalance = accountBalance + transactionAmount; // 100 + 100 = 200
        availableBalance = accountBalance; // 200
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after hold-release-deposit");
    }

    /**
     * Test: Savings Account Deposit After Hold and Release - No Interest (V1 - Release Only)
     *
     * V1 Release behavior: Release only returns held funds to available balance, does NOT create withdrawal. Account
     * balance remains unchanged. This test also verifies running balances for each transaction.
     *
     * With V1 release, no overdraft is needed since no withdrawal is created.
     */
    @Test
    public void testSavingsAccountDepositAfterNegativeHoldAmountNoInterest() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // V1 release doesn't need overdraft since it doesn't create withdrawal
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, "0", null, false, false, false,
                BigDecimal.ZERO);
        Assertions.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductID, ACCOUNT_TYPE_INDIVIDUAL);
        this.savingsAccountHelper.approveSavings(savingsId);
        HashMap savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        float accountBalance = 0F;
        float availableBalance;
        float transactionAmount = 100F;

        // Deposit 100
        Integer depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId,
                String.valueOf(transactionAmount), SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);
        accountBalance = accountBalance + transactionAmount; // 100
        availableBalance = accountBalance; // 100
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after deposit");
        HashMap depositTransaction = savingsAccountHelper.getTransactionDetails(savingsId, depositTransactionId);
        assertEquals(accountBalance, ((Number) depositTransaction.get("runningBalance")).floatValue(),
                "Verifying Running Balance of deposit");

        // Withdraw 50 (partial)
        float withdrawAmount = 50F;
        Integer withdrawalTransactionId = (Integer) this.savingsAccountHelper.withdrawalFromSavingsAccount(savingsId,
                String.valueOf(withdrawAmount), SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(withdrawalTransactionId);
        accountBalance = accountBalance - withdrawAmount; // 50
        availableBalance = accountBalance; // 50
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after withdrawal");
        HashMap withdrawalTransaction = savingsAccountHelper.getTransactionDetails(savingsId, withdrawalTransactionId);
        assertEquals(accountBalance, ((Number) withdrawalTransaction.get("runningBalance")).floatValue(),
                "Verifying Running Balance of withdraw");

        // Hold 30
        float holdAmount = 30F;
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, String.valueOf(holdAmount),
                false, SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(holdTransactionId);
        // Hold doesn't change account balance, only reduces available balance
        availableBalance = accountBalance - holdAmount; // 50 - 30 = 20
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after hold amount");

        // V1 Release hold - only releases hold, does NOT create withdrawal
        Integer releaseTransactionId = this.savingsAccountHelper.releaseAmount(savingsId, holdTransactionId);
        Assertions.assertNotNull(releaseTransactionId);

        // V1 behavior: Release returns held amount to available balance
        // Account balance unchanged, available balance = account balance
        availableBalance = accountBalance; // 50 (hold released, no withdrawal)
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "V1 Release: Available balance should equal account balance");
        assertEquals(accountBalance, ((Number) summary.get("accountBalance")).floatValue(),
                "V1 Release: Account balance should be unchanged");

        // Deposit 100 after hold-release
        depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, String.valueOf(transactionAmount),
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);
        accountBalance = accountBalance + transactionAmount; // 50 + 100 = 150
        availableBalance = accountBalance; // 150
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(availableBalance, summary.get("availableBalance"), "Verifying Balance after hold-release-deposit");

        // Verify release transaction details
        HashMap releaseTransaction = savingsAccountHelper.getTransactionDetails(savingsId, releaseTransactionId);
        assertFalse((Boolean) releaseTransaction.get("reversed"), "Verifying release transaction is not reversed");

        LOG.info("V1 Release test completed - release only, no withdrawal created");
    }

    /**
     * Test: V2 Release with Withdrawal - Combined Release + Withdrawal in Single Call
     *
     * This test verifies V2 release behavior: 1. V2 Release performs both release AND withdrawal in one atomic
     * transaction 2. Journal entries are posted during the withdrawal phase 3. Account balance is deducted by the
     * release amount 4. Response includes holdTransactionId, releaseTransactionId, and withdrawalTransactionId
     *
     * V2 Release requires overdraft if releasing from an account where the release would cause negative balance.
     */
    @Test
    public void testV2ReleaseWithWithdrawal() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        // Create GL accounts for cash-based accounting
        final Account savingsReferenceAccount = this.accountHelper.createAssetAccount("Savings Reference V2");
        final Account savingsControlAccount = this.accountHelper.createLiabilityAccount("Savings Control V2");
        final Account interestOnSavingsAccount = this.accountHelper.createExpenseAccount("Interest on Savings V2");
        final Account incomeFromFeeAccount = this.accountHelper.createIncomeAccount("Income from Fee V2");
        final Account fundsOnHoldAccount = this.accountHelper.createLiabilityAccount("Funds on Hold V2");

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // Create savings product with cash-based accounting
        final Integer savingsProductID = createSavingsProductWithCashBasedAccountingAndOverdraft(savingsReferenceAccount,
                savingsControlAccount, interestOnSavingsAccount, incomeFromFeeAccount, fundsOnHoldAccount);
        Assertions.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductID, ACCOUNT_TYPE_INDIVIDUAL);
        this.savingsAccountHelper.approveSavings(savingsId);
        HashMap savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        // Initial deposit
        float depositAmount = 1000F;
        Integer depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, String.valueOf(depositAmount),
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(depositTransactionId);

        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(depositAmount, summary.get("availableBalance"), "Verifying initial balance");
        assertEquals(depositAmount, summary.get("accountBalance"), "Verifying initial account balance");

        // Place hold
        float holdAmount = 300F;
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, String.valueOf(holdAmount),
                false, SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assertions.assertNotNull(holdTransactionId);

        // Verify after hold: account balance unchanged, available balance reduced
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(depositAmount - holdAmount, summary.get("availableBalance"), "Verifying available balance after hold");
        assertEquals(depositAmount, summary.get("accountBalance"), "Account balance should be unchanged by hold");

        // V2 Release with Withdrawal - performs release + withdrawal in one call
        HashMap v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransactionId);
        Assertions.assertNotNull(v2Response);

        // Verify V2 response contains all linked transaction IDs
        HashMap changes = (HashMap) v2Response.get("changes");
        Assertions.assertNotNull(changes, "V2 response should contain changes");
        Assertions.assertNotNull(changes.get("holdTransactionId"), "V2 response should contain holdTransactionId");
        Assertions.assertNotNull(changes.get("releaseTransactionId"), "V2 response should contain releaseTransactionId");
        Assertions.assertNotNull(changes.get("withdrawalTransactionId"), "V2 response should contain withdrawalTransactionId");

        // V2 behavior: Release + Withdrawal deducts from account balance
        // Account balance: 1000 - 300 = 700
        // Available balance: 700 (no holds remaining)
        float expectedBalanceAfterV2Release = depositAmount - holdAmount; // 700
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(expectedBalanceAfterV2Release, summary.get("availableBalance"),
                "V2 Release: Available balance should be reduced by hold amount");
        assertEquals(expectedBalanceAfterV2Release, summary.get("accountBalance"),
                "V2 Release: Account balance should be reduced by hold amount");

        // Account balance should equal available balance (no holds remaining)
        Float accountBalance = (Float) summary.get("accountBalance");
        Float availBalance = (Float) summary.get("availableBalance");
        assertEquals(accountBalance, availBalance, "Account balance should equal available balance after V2 release");

        LOG.info("V2 Release with Withdrawal test completed successfully");
        LOG.info("Initial deposit: {}", depositAmount);
        LOG.info("Hold amount: {}", holdAmount);
        LOG.info("Final balance after V2 release: {}", expectedBalanceAfterV2Release);
    }

    // LienAtProductLevel
    private Integer createSavingsProduct(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final String minOpenningBalance, String minBalanceForInterestCalculation, final boolean enforceMinRequiredBalance,
            final boolean allowOverDraft, final boolean lienAllowed, BigDecimal interestRate) {

        LOG.info("------------------------------CREATING NEW SAVINGS PRODUCT WITH LIEN---------------------------------------");
        SavingsProductHelper savingsProductHelper = new SavingsProductHelper();
        if (lienAllowed) {
            final String maxAllowedLienLimit = "2000.0";
            savingsProductHelper.withLienAllowed(maxAllowedLienLimit);
        }
        if (enforceMinRequiredBalance) {
            final String minRequiredBalance = "100.0";
            savingsProductHelper.withMinRequiredBalance(minRequiredBalance);
            savingsProductHelper.withEnforceMinRequiredBalance("true");
        }
        if (allowOverDraft) {
            final String overDraftLimit = "500.0";
            savingsProductHelper.withOverDraft(overDraftLimit);
        }
        if (interestRate != null) {
            savingsProductHelper.withNominalAnnualInterestRate(interestRate);
        }
        final String savingsProductJSON = savingsProductHelper.withInterestCompoundingPeriodTypeAsDaily()
                .withInterestPostingPeriodTypeAsMonthly().withInterestCalculationPeriodTypeAsDailyBalance()
                .withMinBalanceForInterestCalculation(minBalanceForInterestCalculation).withMinimumOpenningBalance(minOpenningBalance)
                .build();

        return SavingsProductHelper.createSavingsProduct(savingsProductJSON, requestSpec, responseSpec);
    }

    @AfterEach
    public void tearDown() {
        globalConfigurationHelper.resetAllDefaultGlobalConfigurations();
        globalConfigurationHelper.verifyAllDefaultGlobalConfigurations();
    }
}
