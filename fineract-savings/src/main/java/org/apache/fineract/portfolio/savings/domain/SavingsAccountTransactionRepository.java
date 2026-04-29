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
package org.apache.fineract.portfolio.savings.domain;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsAccountTransactionRepository
        extends JpaRepository<SavingsAccountTransaction, Long>, JpaSpecificationExecutor<SavingsAccountTransaction> {

    @Query("select sat from SavingsAccountTransaction sat where sat.id = :transactionId and sat.savingsAccount.id = :savingsId")
    SavingsAccountTransaction findOneByIdAndSavingsAccountId(@Param("transactionId") Long transactionId,
            @Param("savingsId") Long savingsId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select st from SavingsAccountTransaction st where st.savingsAccount = :savingsAccount and st.dateOf >= :transactionDate order by st.dateOf,st.createdDate,st.id")
    List<SavingsAccountTransaction> findTransactionsAfterPivotDate(@Param("savingsAccount") SavingsAccount savingsAccount,
            @Param("transactionDate") LocalDate transactionDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select st from SavingsAccountTransaction st where st.savingsAccount = :savingsAccount and st.dateOf = :date and st.reversalTransaction <> 1 and st.reversed <> 1 order by st.id")
    List<SavingsAccountTransaction> findTransactionRunningBalanceBeforePivotDate(@Param("savingsAccount") SavingsAccount savingsAccount,
            @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SavingsAccountTransaction> findBySavingsAccount(@Param("savingsAccount") SavingsAccount savingsAccount);

    List<SavingsAccountTransaction> findByRefNo(@Param("refNo") String refNo);

    @Query("select sat from SavingsAccountTransaction sat where sat.savingsAccount.id = :savingsId and sat.dateOf <= :transactionDate and sat.reversed=false")
    List<SavingsAccountTransaction> findBySavingsAccountIdAndLessThanDateOfAndReversedIsFalse(@Param("savingsId") Long savingsId,
            @Param("transactionDate") LocalDate transactionDate, Pageable pageable);

    // Hold & Release Enhancement - New methods

    /**
     * Find transaction by ID with pessimistic lock for concurrent access protection
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select st from SavingsAccountTransaction st where st.id = :transactionId")
    Optional<SavingsAccountTransaction> findByIdWithLock(@Param("transactionId") Long transactionId);

    /**
     * Find transaction by hold transaction ID and idempotency key (scoped idempotency)
     */
    @Query("select t from SavingsAccountTransaction t where t.holdTransactionId = :holdId and t.idempotencyKey = :key")
    Optional<SavingsAccountTransaction> findByHoldTransactionIdAndIdempotencyKey(@Param("holdId") Long holdId, @Param("key") String key);

    /**
     * Atomic decrement of remaining hold amount with race condition protection
     */
    @Modifying
    @Query("UPDATE SavingsAccountTransaction t SET t.remainingHoldAmount = t.remainingHoldAmount - :amount "
            + "WHERE t.id = :holdId AND t.remainingHoldAmount >= :amount")
    int decrementRemainingHoldAmount(@Param("holdId") Long holdId, @Param("amount") BigDecimal amount);

    /**
     * Atomic increment of remaining hold amount for reversal (with cap protection)
     */
    @Modifying
    @Query("UPDATE SavingsAccountTransaction t SET t.remainingHoldAmount = t.remainingHoldAmount + :amount "
            + "WHERE t.id = :holdId AND t.remainingHoldAmount + :amount <= t.amount")
    int incrementRemainingHoldAmount(@Param("holdId") Long holdId, @Param("amount") BigDecimal amount);

    /**
     * Check if active (non-reversed) child transactions exist for a hold
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM SavingsAccountTransaction t "
            + "WHERE t.holdTransactionId = :holdId AND t.reversed = false AND t.id != :holdId")
    boolean existsActiveChildTransactions(@Param("holdId") Long holdId);

    /**
     * Find active withdrawal for a release transaction
     */
    @Query("SELECT t.id FROM SavingsAccountTransaction t "
            + "WHERE t.relatedTransactionId = :releaseId AND t.typeOf = 2 AND t.reversed = false")
    Optional<Long> findActiveWithdrawalForRelease(@Param("releaseId") Long releaseId);

    /**
     * Update GL status for a transaction
     */
    @Modifying
    @Query("UPDATE SavingsAccountTransaction t SET t.glStatus = :status WHERE t.id = :transactionId")
    int updateGlStatus(@Param("transactionId") Long transactionId, @Param("status") String status);
}
