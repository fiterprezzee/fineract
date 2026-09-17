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
package org.apache.fineract.portfolio.savings.data;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class SavingsAccountTransactionDataValidatorTest {

    private SavingsAccountTransactionDataValidator validator;

    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    @Mock
    private SavingsAccount account;

    @BeforeEach
    public void setUp() {
        validator = new SavingsAccountTransactionDataValidator(new FromJsonHelper(), configurationDomainService,
                savingsAccountTransactionRepository);
    }

    @Test
    public void validateHoldShouldUseRepositoryForLastTransactionDateWhenBackdatedTransactionsAreDisabled() {
        final Long savingsId = 99L;
        when(account.getId()).thenReturn(savingsId);
        when(account.isActive()).thenReturn(true);
        when(account.getEnforceMinRequiredBalance()).thenReturn(false);
        when(account.isLienAllowed()).thenReturn(false);
        when(account.isAllowOverdraft()).thenReturn(false);
        when(account.getWithdrawableBalance()).thenReturn(BigDecimal.valueOf(1000));
        when(savingsAccountTransactionRepository.findLastTransactionDate(eq(savingsId), any(Pageable.class)))
                .thenReturn(List.of(LocalDate.of(2026, 5, 29)));

        validator.validateHoldAndAssembleForm(
                "{\"transactionDate\":\"29 May 2026\",\"transactionAmount\":\"10\",\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"lienAllowed\":false,\"reasonForBlock\":\"test\"}",
                account, null, false);

        verify(savingsAccountTransactionRepository).findLastTransactionDate(eq(savingsId), any(Pageable.class));
        verify(account, never()).retrieveLastTransactionDate();
    }
}
