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

import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.accounting.JournalEntryHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsStatusChecker;
import org.apache.fineract.integrationtests.common.savings.SavingsTestLifecycleExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Hold & Release Enhancement feature.
 *
 * Tests the following scenarios: - V1 Release: Release only (no withdrawal, no journal entries) - V2 Release: Combined
 * release + withdraw in single transaction (creates journal entries) - Transaction linkage (holdTransactionId,
 * relatedTransactionId) - Balance changes verification - Multiple holds on same account - Over-release prevention
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith({ SavingsTestLifecycleExtension.class })
public class HoldReleaseEnhancementIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(HoldReleaseEnhancementIntegrationTest.class);

    public static final String MINIMUM_OPENING_BALANCE = "1000.0";
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private SavingsAccountHelper savingsAccountHelper;
    private SavingsProductHelper savingsProductHelper;
    private AccountHelper accountHelper;
    private JournalEntryHelper journalEntryHelper;

    // GL Accounts for cash-based accounting
    private Account savingsReferenceAccount;
    private Account savingsControlAccount;
    private Account fundsOnHoldAccount;
    private Account interestOnSavingsAccount;
    private Account incomeFromFeeAccount;
    private Account transfersInSuspenseAccount;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(SC_OK).build();
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);
        this.savingsProductHelper = new SavingsProductHelper();
        this.accountHelper = new AccountHelper(this.requestSpec, this.responseSpec);
        this.journalEntryHelper = new JournalEntryHelper(this.requestSpec, this.responseSpec);

        // Create GL accounts for cash-based accounting
        createGLAccounts();
    }

    private void createGLAccounts() {
        this.savingsReferenceAccount = this.accountHelper.createAssetAccount("Savings Reference");
        this.savingsControlAccount = this.accountHelper.createLiabilityAccount("Savings Control");
        this.fundsOnHoldAccount = this.accountHelper.createLiabilityAccount("Funds on Hold");
        this.interestOnSavingsAccount = this.accountHelper.createExpenseAccount("Interest on Savings");
        this.incomeFromFeeAccount = this.accountHelper.createIncomeAccount("Income from Fee");
        this.transfersInSuspenseAccount = this.accountHelper.createLiabilityAccount("Transfers in Suspense");
    }

    /**
     * Test V1 Release: Release only - no withdrawal created, no journal entries. After V1 release, funds return to
     * available balance but account balance unchanged.
     */
    @Test
    public void testV1ReleaseOnly() {
        // Create client and savings account
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        assertNotNull(clientID);

        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        assertNotNull(savingsProductId);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);
        assertNotNull(savingsId);

        HashMap savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        // Verify initial balance
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01, "Initial balance should be 1000");

        // Create HOLD for 200
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId, "Hold transaction should be created");

        // Verify after hold: Account=1000, Available=800
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01, "Account balance should remain 1000 after hold");
        assertEquals(800f, (Float) summary.get("availableBalance"), 0.01, "Available balance should be 800 after hold");

        // V1 Release - release only, no withdrawal
        Integer releaseTransactionId = this.savingsAccountHelper.releaseAmount(savingsId, holdTransactionId);
        assertNotNull(releaseTransactionId, "Release transaction ID should be returned");

        // After V1 release: Account=1000, Available=1000 (funds returned to available)
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        Float finalAccountBalance = (Float) summary.get("accountBalance");
        Float finalAvailableBalance = (Float) summary.get("availableBalance");

        // V1 release only releases hold - no withdrawal, so account balance unchanged
        assertEquals(1000f, finalAccountBalance, 0.01, "V1 Release: Account balance should remain 1000 (no withdrawal)");
        assertEquals(1000f, finalAvailableBalance, 0.01, "V1 Release: Available balance should return to 1000");

        LOG.info("Test V1 Release Only PASSED");
    }

    /**
     * Test V2 Release: Combined release + withdraw in single transaction. After V2 release, account balance is reduced
     * by the held amount.
     */
    @Test
    public void testV2ReleaseWithWithdrawal() {
        // Create client and savings account
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        assertNotNull(clientID);

        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        assertNotNull(savingsProductId);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);
        assertNotNull(savingsId);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Verify initial balance
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01, "Initial balance should be 1000");

        // Create HOLD for 200
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId, "Hold transaction should be created");

        // Verify after hold: Account=1000, Available=800
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01);
        assertEquals(800f, (Float) summary.get("availableBalance"), 0.01);

        // V2 Release - combined release + withdraw
        HashMap v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransactionId);
        assertNotNull(v2Response, "V2 Release response should not be null");

        Integer withdrawalTransactionId = (Integer) v2Response.get("resourceId");
        assertNotNull(withdrawalTransactionId, "Withdrawal transaction ID should be returned");

        // Verify transaction IDs are linked in changes
        HashMap changes = (HashMap) v2Response.get("changes");
        if (changes != null) {
            Integer linkedHoldId = (Integer) changes.get("holdTransactionId");
            Integer linkedReleaseId = (Integer) changes.get("releaseTransactionId");
            LOG.info("V2 Response - Hold ID: {}, Release ID: {}, Withdrawal ID: {}", linkedHoldId, linkedReleaseId,
                    withdrawalTransactionId);
        }

        // After V2 release: Account=800, Available=800 (funds withdrawn)
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        Float finalAccountBalance = (Float) summary.get("accountBalance");
        Float finalAvailableBalance = (Float) summary.get("availableBalance");

        assertEquals(800f, finalAccountBalance, 0.01, "V2 Release: Account balance should be 800 after release+withdraw");
        assertEquals(800f, finalAvailableBalance, 0.01, "V2 Release: Available balance should be 800");

        LOG.info("Test V2 Release With Withdrawal PASSED");
    }

    @Test
    public void testV2ReleaseRejectsStandardHoldAmountAboveHold() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId);

        ResponseSpecification errorResponseSpec = new ResponseSpecBuilder().expectStatusCode(403).build();
        SavingsAccountHelper errorHelper = new SavingsAccountHelper(this.requestSpec, errorResponseSpec);

        ArrayList<HashMap> error = (ArrayList<HashMap>) errorHelper.releaseAmountV2WithError(savingsId, holdTransactionId, "220");
        assertNotNull(error, "Standard hold should reject settlement amount above hold amount");
        assertEquals("error.msg.savingsaccount.release.amount.must.equal.hold.amount",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));
    }

    @Test
    public void testV2ReleaseRejectsStandardHoldAmountBelowHold() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId);

        ResponseSpecification errorResponseSpec = new ResponseSpecBuilder().expectStatusCode(403).build();
        SavingsAccountHelper errorHelper = new SavingsAccountHelper(this.requestSpec, errorResponseSpec);

        ArrayList<HashMap> error = (ArrayList<HashMap>) errorHelper.releaseAmountV2WithError(savingsId, holdTransactionId, "180");
        assertNotNull(error, "Standard hold should reject settlement amount below hold amount");
        assertEquals("error.msg.savingsaccount.release.amount.must.equal.hold.amount",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));
    }

    @Test
    public void testV2ReleaseAllowsPreAuthHoldAmountBelowHold() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false, true,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId);

        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01);
        assertEquals(800f, (Float) summary.get("availableBalance"), 0.01);

        HashMap v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransactionId, "180", true);
        assertNotNull(v2Response, "V2 preAuth release response should not be null");

        HashMap changes = (HashMap) v2Response.get("changes");
        assertNotNull(changes, "V2 preAuth release should return linked transaction changes");
        assertEquals(holdTransactionId, changes.get("holdTransactionId"));
        assertNotNull(changes.get("releaseTransactionId"), "Release transaction ID should be returned");
        assertNotNull(changes.get("withdrawalTransactionId"), "Withdrawal transaction ID should be returned");
        assertEquals(true, changes.get("preAuth"));
        assertEquals(0, new BigDecimal(changes.get("holdAmount").toString()).compareTo(new BigDecimal("200")));
        assertEquals(0, new BigDecimal(changes.get("settlementAmount").toString()).compareTo(new BigDecimal("180")));

        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(820f, (Float) summary.get("accountBalance"), 0.01, "PreAuth V2 release should withdraw only the settlement amount");
        assertEquals(820f, (Float) summary.get("availableBalance"), 0.01,
                "PreAuth V2 release should clear the hold and leave available equal to account balance");
    }

    /**
     * Test that V1 and V2 releases can coexist - clients can switch between versions.
     */
    @Test
    public void testV1AndV2Coexistence() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Create two holds
        Integer holdTransaction1Id = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Integer holdTransaction2Id = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "300", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);

        // Verify: Account=1000, Available=500 (500 held)
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01);
        assertEquals(500f, (Float) summary.get("availableBalance"), 0.01);

        // Use V1 for first hold (release only)
        Integer releaseTransaction1Id = this.savingsAccountHelper.releaseAmount(savingsId, holdTransaction1Id);
        assertNotNull(releaseTransaction1Id);

        // After V1 release: Account=1000, Available=700 (only 300 still held)
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01, "After V1 release, account balance unchanged");
        assertEquals(700f, (Float) summary.get("availableBalance"), 0.01, "After V1 release, available increased by 200");

        // Use V2 for second hold (release + withdraw)
        HashMap v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransaction2Id);
        assertNotNull(v2Response);

        // After V2 release: Account=700, Available=700
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(700f, (Float) summary.get("accountBalance"), 0.01, "After V2 release, account reduced by 300");
        assertEquals(700f, (Float) summary.get("availableBalance"), 0.01, "After V2 release, available equals account");

        LOG.info("Test V1 and V2 Coexistence PASSED");
    }

    /**
     * Test over-release rejection - cannot release an already released hold.
     */
    @Test
    public void testOverReleaseRejection() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Create hold
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "500", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId);

        // Release once (V1)
        Integer releaseTransactionId = this.savingsAccountHelper.releaseAmount(savingsId, holdTransactionId);
        assertNotNull(releaseTransactionId);

        // Try to release again - should fail
        ResponseSpecification errorResponseSpec = new ResponseSpecBuilder().expectStatusCode(403).build();
        SavingsAccountHelper errorHelper = new SavingsAccountHelper(this.requestSpec, errorResponseSpec);

        ArrayList<HashMap> error = (ArrayList<HashMap>) errorHelper.releaseAmountWithError(savingsId, holdTransactionId);
        assertNotNull(error, "Should return error for already released hold");

        LOG.info("Test Over-Release Rejection PASSED");
    }

    /**
     * Test multiple holds on same account - releasing one doesn't affect others.
     */
    @Test
    public void testMultipleHoldsSameAccount() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Create Hold #1 for 200
        Integer holdTransaction1Id = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);

        // Create Hold #2 for 300
        Integer holdTransaction2Id = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "300", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);

        // Verify: Account=1000, Available=500
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(1000f, (Float) summary.get("accountBalance"), 0.01);
        assertEquals(500f, (Float) summary.get("availableBalance"), 0.01);

        // V2 Release Hold #1 only (200)
        HashMap v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransaction1Id);
        assertNotNull(v2Response);

        // Verify: Account=800, Hold=300, Available=500
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(800f, (Float) summary.get("accountBalance"), 0.01, "Account balance reduced by 200");
        assertEquals(500f, (Float) summary.get("availableBalance"), 0.01, "300 still on hold from Hold #2");

        // V2 Release Hold #2 (300)
        v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransaction2Id);
        assertNotNull(v2Response);

        // Verify: Account=500, Hold=0, Available=500
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(500f, (Float) summary.get("accountBalance"), 0.01);
        assertEquals(500f, (Float) summary.get("availableBalance"), 0.01);

        LOG.info("Test Multiple Holds Same Account PASSED");
    }

    /**
     * Test that HOLD transaction reduces available balance but not account balance.
     */
    @Test
    public void testHoldReducesAvailableBalanceOnly() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Initial state
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        Float initialAccountBalance = (Float) summary.get("accountBalance");
        Float initialAvailableBalance = (Float) summary.get("availableBalance");

        assertEquals(initialAccountBalance, initialAvailableBalance, "Initially, account and available balance should be equal");

        // Create hold
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "300", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId);

        // After hold
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        Float accountBalanceAfterHold = (Float) summary.get("accountBalance");
        Float availableBalanceAfterHold = (Float) summary.get("availableBalance");

        // Account balance unchanged, available reduced
        assertEquals(initialAccountBalance, accountBalanceAfterHold, 0.01, "Account balance should not change after hold");
        assertEquals(initialAvailableBalance - 300f, availableBalanceAfterHold, 0.01, "Available balance should be reduced by hold amount");

        LOG.info("Test Hold Reduces Available Balance Only PASSED");
    }

    /**
     * Test that withdrawal is blocked when insufficient available balance due to hold.
     */
    @Test
    public void testWithdrawalBlockedByHold() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Hold 800 of 1000 (leaving only 200 available)
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "800", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        assertNotNull(holdTransactionId);

        // Try to withdraw 300 (more than available 200) - should fail
        ResponseSpecification errorResponseSpec = new ResponseSpecBuilder().expectStatusCode(403).build();
        SavingsAccountHelper errorHelper = new SavingsAccountHelper(this.requestSpec, errorResponseSpec);

        ArrayList<HashMap> error = (ArrayList<HashMap>) errorHelper.withdrawalFromSavingsAccount(savingsId, "300",
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_ERROR);

        assertNotNull(error, "Withdrawal should fail when amount exceeds available balance");
        assertEquals("error.msg.savingsaccount.transaction.insufficient.account.balance",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        LOG.info("Test Withdrawal Blocked By Hold PASSED");
    }

    /**
     * Test V2 release creates both release and withdrawal transactions.
     */
    @Test
    public void testV2ReleaseCreatesWithdrawalTransaction() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer savingsProductId = createSavingsProductWithCashBasedAccounting();
        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(clientID, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsAccountHelper.approveSavings(savingsId);
        this.savingsAccountHelper.activateSavings(savingsId);

        // Count transactions before
        List<HashMap> transactionsBefore = (List<HashMap>) this.savingsAccountHelper.getSavingsDetails(savingsId, "transactions");
        int countBefore = transactionsBefore != null ? transactionsBefore.size() : 0;

        // Create hold
        Integer holdTransactionId = (Integer) this.savingsAccountHelper.holdAmountInSavingsAccount(savingsId, "200", false,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);

        // V2 Release (creates release + withdrawal)
        HashMap v2Response = this.savingsAccountHelper.releaseAmountV2WithFullResponse(savingsId, holdTransactionId);
        assertNotNull(v2Response);
        Integer withdrawalTransactionId = (Integer) v2Response.get("resourceId");
        assertNotNull(withdrawalTransactionId);

        // Count transactions after
        List<HashMap> transactionsAfter = (List<HashMap>) this.savingsAccountHelper.getSavingsDetails(savingsId, "transactions");
        int countAfter = transactionsAfter != null ? transactionsAfter.size() : 0;

        // Should have 3 new transactions: HOLD, RELEASE, WITHDRAWAL
        assertTrue(countAfter >= countBefore + 3,
                "Should have at least 3 new transactions (HOLD, RELEASE, WITHDRAWAL). Before: " + countBefore + ", After: " + countAfter);

        // Find withdrawal transaction (type = 2)
        boolean foundWithdrawal = false;
        for (HashMap txn : transactionsAfter) {
            HashMap transactionType = (HashMap) txn.get("transactionType");
            if (transactionType != null) {
                Integer typeId = (Integer) transactionType.get("id");
                if (typeId != null && typeId == 2) { // WITHDRAWAL
                    foundWithdrawal = true;
                    BigDecimal amount = new BigDecimal(txn.get("amount").toString());
                    assertEquals(200, amount.intValue(), "Withdrawal amount should match release amount");
                    break;
                }
            }
        }

        assertTrue(foundWithdrawal, "Should find a WITHDRAWAL transaction after V2 release");

        LOG.info("Test V2 Release Creates Withdrawal Transaction PASSED");
    }

    /**
     * Helper method to create a savings product with cash-based accounting.
     */
    private Integer createSavingsProductWithCashBasedAccounting() {
        Account[] accountList = new Account[] { this.savingsReferenceAccount, this.savingsControlAccount, this.interestOnSavingsAccount,
                this.incomeFromFeeAccount, this.transfersInSuspenseAccount };

        final String savingsProductJSON = this.savingsProductHelper.withInterestCompoundingPeriodTypeAsDaily()
                .withInterestPostingPeriodTypeAsMonthly().withInterestCalculationPeriodTypeAsDailyBalance()
                .withMinimumOpenningBalance(MINIMUM_OPENING_BALANCE)
                .withFundsOnHoldAccountId(this.fundsOnHoldAccount.getAccountID().toString()).withAccountingRuleAsCashBased(accountList)
                .build();

        return SavingsProductHelper.createSavingsProduct(savingsProductJSON, this.requestSpec, this.responseSpec);
    }
}
