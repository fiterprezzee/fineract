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
package org.apache.fineract.infrastructure.core.filters;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.service.SynchronousCommandProcessingService;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdempotencyStoreHelper {

    private final CommandSourceRepository commandSourceRepository;
    private final FineractRequestContextHolder fineractRequestContextHolder;

    @Transactional
    public void updateResultStatusCode(Integer statusCode, Long commandId) {
        commandSourceRepository.updateResultStatusCodeById(commandId, statusCode);
    }

    public boolean isStoreIdempotencyKey(HttpServletRequest request) {
        return Optional
                .ofNullable(
                        fineractRequestContextHolder.getAttribute(SynchronousCommandProcessingService.IDEMPOTENCY_KEY_STORE_FLAG, request))
                .filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false);
    }

    public Optional<Long> getCommandId(HttpServletRequest request) {
        return Optional
                .ofNullable(fineractRequestContextHolder.getAttribute(SynchronousCommandProcessingService.COMMAND_SOURCE_ID, request))
                .filter(Long.class::isInstance).map(Long.class::cast);
    }
}
