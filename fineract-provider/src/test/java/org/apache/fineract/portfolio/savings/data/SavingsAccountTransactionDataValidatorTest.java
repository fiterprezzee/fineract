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

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

public class SavingsAccountTransactionDataValidatorTest {

    @Test
    public void validateHoldShouldUseRepositoryForLastTransactionDateWhenBackdatedTransactionsAreDisabled() {
        final Long savingsId = 99L;
        final AtomicBoolean repositoryLookupUsed = new AtomicBoolean(false);
        final SavingsAccountTransactionDataValidator validator = new SavingsAccountTransactionDataValidator(new FromJsonHelper(),
                configurationDomainService(), savingsAccountTransactionRepository(savingsId, repositoryLookupUsed));
        TestSavingsAccount account = new TestSavingsAccount();
        account.setId(savingsId);

        validator.validateHoldAndAssembleForm(
                "{\"transactionDate\":\"29 May 2026\",\"transactionAmount\":\"10\",\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"lienAllowed\":false,\"reasonForBlock\":\"test\"}",
                account, null, false);

        Assertions.assertFalse(account.retrieveLastTransactionDateCalled);
        Assertions.assertTrue(repositoryLookupUsed.get());
    }

    private static ConfigurationDomainService configurationDomainService() {
        return (ConfigurationDomainService) Proxy.newProxyInstance(ConfigurationDomainService.class.getClassLoader(),
                new Class<?>[] { ConfigurationDomainService.class }, (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static SavingsAccountTransactionRepository savingsAccountTransactionRepository(final Long expectedSavingsId,
            final AtomicBoolean repositoryLookupUsed) {
        return (SavingsAccountTransactionRepository) Proxy.newProxyInstance(SavingsAccountTransactionRepository.class.getClassLoader(),
                new Class<?>[] { SavingsAccountTransactionRepository.class }, (proxy, method, args) -> {
                    if ("findLastTransactionDate".equals(method.getName())) {
                        Assertions.assertEquals(expectedSavingsId, args[0]);
                        Assertions.assertInstanceOf(Pageable.class, args[1]);
                        repositoryLookupUsed.set(true);
                        return List.of(LocalDate.of(2026, 5, 29));
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(final Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    private static final class TestSavingsAccount extends SavingsAccount {

        private boolean retrieveLastTransactionDateCalled;

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Boolean getEnforceMinRequiredBalance() {
            return false;
        }

        @Override
        public boolean isLienAllowed() {
            return false;
        }

        @Override
        public boolean isAllowOverdraft() {
            return false;
        }

        @Override
        public BigDecimal getWithdrawableBalance() {
            return BigDecimal.valueOf(1000);
        }

        @Override
        public LocalDate retrieveLastTransactionDate() {
            this.retrieveLastTransactionDateCalled = true;
            return LocalDate.of(2026, 5, 29);
        }
    }
}
