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
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.cucumber.java8.En;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryEvent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fineract.commands.configuration.RetryConfigurationAssembler;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

public class CommandServiceStepDefinitions implements En {

    private static final Logger log = LoggerFactory.getLogger(CommandServiceStepDefinitions.class);

    private PortfolioCommandSourceWritePlatformService commandSourceWritePlatformService;

    private DummyCommand command;

    private RetryEvent retryEvent;

    private final AtomicInteger counter = new AtomicInteger();

    @SuppressWarnings("unchecked")
    public CommandServiceStepDefinitions() {
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();

        FineractProperties fineractProperties = new FineractProperties();
        FineractProperties.RetryProperties retryProps = new FineractProperties.RetryProperties();
        FineractProperties.RetryProperties.InstancesProperties instances = new FineractProperties.RetryProperties.InstancesProperties();
        FineractProperties.RetryProperties.InstancesProperties.ExecuteCommandProperties execCmd = new FineractProperties.RetryProperties.InstancesProperties.ExecuteCommandProperties();
        execCmd.setMaxAttempts(3);
        execCmd.setWaitDuration(Duration.ofSeconds(1));
        execCmd.setEnableExponentialBackoff(true);
        execCmd.setExponentialBackoffMultiplier(2.0);
        execCmd.setRetryExceptions(new Class[] { CannotAcquireLockException.class, ObjectOptimisticLockingFailureException.class });
        instances.setExecuteCommand(execCmd);
        retryProps.setInstances(instances);
        fineractProperties.setRetry(retryProps);

        FineractRequestContextHolder contextHolder = new FineractRequestContextHolder();
        RetryConfigurationAssembler retryConfigurationAssembler = new RetryConfigurationAssembler(retryRegistry, fineractProperties,
                contextHolder);

        ThrowingSupplier throwingSupplier = new ThrowingSupplier();

        Given("/^A command source write service$/", () -> {
            throwingSupplier.reset();

            Retry retry1 = retryConfigurationAssembler.getRetryConfigurationForExecuteCommand();
            assertNotNull(retry1);
            retry1.getEventPublisher().onRetry(event -> {
                log.warn("... retry event: {}", event);
                counter.incrementAndGet();
                CommandServiceStepDefinitions.this.retryEvent = event;
            });

            CommandProcessingService processAndLogCommandService = new RetryCommandProcessingService(retry1, throwingSupplier);

            this.commandSourceWritePlatformService = new DummyCommandSourceWriteService(processAndLogCommandService);
            this.command = new DummyCommand();
        });

        When("/^The user executes the command via a command write service with exceptions$/", () -> {
            try {
                this.commandSourceWritePlatformService.logCommandSource(command);
            } catch (Exception e) {
                log.warn("At the moment mocking data access is so incredibly hard... it's easier to just ignore this exception: {}",
                        e.getMessage());
            }
        });

        Then("/^The command processing service should fallback as expected$/", () -> {
            assertNotNull(retryEvent);
            assertEquals("executeCommand", retryEvent.getName());
            assertEquals(2, retryEvent.getNumberOfRetryAttempts());
        });

        Then("/^The command processing service execute function should be called 2 times$/", () -> {
            assertEquals(2, counter.get());
        });
    }

    private static class ThrowingSupplier {

        private final AtomicInteger callCount = new AtomicInteger(0);

        void reset() {
            callCount.set(0);
        }

        Object call() {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                throw new CannotAcquireLockException("BLOW IT UP!!!");
            } else if (call == 2) {
                throw new ObjectOptimisticLockingFailureException("Dummy", new RuntimeException("BLOW IT UP!!!"));
            } else {
                throw new RollbackTransactionNotApprovedException(1L, null);
            }
        }
    }

    private static class RetryCommandProcessingService implements CommandProcessingService {

        private final Retry retry;
        private final ThrowingSupplier supplier;

        RetryCommandProcessingService(Retry retry, ThrowingSupplier supplier) {
            this.retry = retry;
            this.supplier = supplier;
        }

        @Override
        public CommandProcessingResult executeCommand(CommandWrapper wrapper, JsonCommand command, boolean isApprovedByChecker) {
            return retry.executeSupplier(() -> {
                supplier.call();
                return CommandProcessingResult.empty();
            });
        }

        @Override
        public boolean validateRollbackCommand(CommandWrapper commandWrapper, org.apache.fineract.useradministration.domain.AppUser user) {
            return false;
        }
    }

    public static class DummyCommand extends CommandWrapper {

        public DummyCommand() {
            super(null, null, null, null, null, null, null, null, null, null, "{}", null, null, null, null, null, null,
                    UUID.randomUUID().toString(), null, null);
        }

        @Override
        public String actionName() {
            return "dummy";
        }
    }

    public static class DummyCommandSourceWriteService implements PortfolioCommandSourceWritePlatformService {

        private final CommandProcessingService processAndLogCommandService;

        public DummyCommandSourceWriteService(CommandProcessingService processAndLogCommandService) {
            this.processAndLogCommandService = processAndLogCommandService;
        }

        @Override
        public CommandProcessingResult logCommandSource(CommandWrapper wrapper) {
            final String json = wrapper.getJson();
            JsonCommand command = JsonCommand.from(json, null, null, wrapper.getEntityName(), wrapper.getEntityId(),
                    wrapper.getSubentityId(), wrapper.getGroupId(), wrapper.getClientId(), wrapper.getLoanId(), wrapper.getSavingsId(),
                    wrapper.getTransactionId(), wrapper.getHref(), wrapper.getProductId(), wrapper.getCreditBureauId(),
                    wrapper.getOrganisationCreditBureauId(), wrapper.getJobName(), wrapper.getLoanExternalId());

            return this.processAndLogCommandService.executeCommand(wrapper, command, true);
        }

        @Override
        public CommandProcessingResult approveEntry(Long id) {
            return null;
        }

        @Override
        public Long rejectEntry(Long id) {
            return null;
        }

        @Override
        public Long deleteEntry(Long makerCheckerId) {
            return null;
        }
    }
}
