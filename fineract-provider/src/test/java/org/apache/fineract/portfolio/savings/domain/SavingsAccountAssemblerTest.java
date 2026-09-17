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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class SavingsAccountAssemblerTest {

    private SavingsAccountAssembler assembler;

    @Mock
    private SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    @Mock
    private SavingsAccountTransactionDataSummaryWrapper savingsAccountTransactionDataSummaryWrapper;
    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private GroupRepositoryWrapper groupRepository;
    @Mock
    private StaffRepositoryWrapper staffRepository;
    @Mock
    private SavingsProductRepository savingProductRepository;
    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepository;
    @Mock
    private SavingsAccountChargeAssembler savingsAccountChargeAssembler;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private AccountTransfersReadPlatformService accountTransfersReadPlatformService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private ExternalIdFactory externalIdFactory;
    @Mock
    private SavingsAccount account;

    @BeforeEach
    public void setUp() {
        assembler = new SavingsAccountAssembler(savingsAccountTransactionSummaryWrapper, savingsAccountTransactionDataSummaryWrapper,
                clientRepository, groupRepository, staffRepository, savingProductRepository, savingsAccountRepository,
                savingsAccountChargeAssembler, fromApiJsonHelper, accountTransfersReadPlatformService, jdbcTemplate,
                configurationDomainService, externalIdFactory);
    }

    @Test
    public void assembleForOperationShouldNotLoadLazyCollectionsWhenBackdatedTransactionsAreDisabled() {
        final Long savingsId = 99L;
        when(savingsAccountRepository.findOneWithNotFoundDetectionWithoutLazyCollections(savingsId)).thenReturn(account);

        final SavingsAccount result = assembler.assembleForOperation(savingsId, false);

        assertSame(account, result);
        verify(savingsAccountRepository).findOneWithNotFoundDetectionWithoutLazyCollections(savingsId);
        verify(savingsAccountRepository, never()).findOneWithNotFoundDetection(savingsId);
        verify(account).setHelpers(eq(savingsAccountTransactionSummaryWrapper), any(SavingsHelper.class));
    }
}
