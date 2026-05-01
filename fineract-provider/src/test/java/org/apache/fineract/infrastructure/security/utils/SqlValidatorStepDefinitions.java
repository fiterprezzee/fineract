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
package org.apache.fineract.infrastructure.security.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cucumber.java8.En;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.config.FineractProperties.FineractSqlValidationPatternProperties;
import org.apache.fineract.infrastructure.core.config.FineractProperties.FineractSqlValidationPatternReferenceProperties;
import org.apache.fineract.infrastructure.core.config.FineractProperties.FineractSqlValidationProfileProperties;
import org.apache.fineract.infrastructure.core.config.FineractProperties.FineractSqlValidationProperties;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlValidatorStepDefinitions implements En {

    private static final Logger log = LoggerFactory.getLogger(SqlValidatorStepDefinitions.class);

    private SqlValidator sqlValidator;

    private Executable executable;
    private String statement;
    private Integer fuzzy = 0;

    public SqlValidatorStepDefinitions() {
        sqlValidator = createSqlValidator();

        Given("/^A partial SQL statement (.*) with whitespaces fuzzy degree (\\d*)$/", (String statement, Integer fuzzy) -> {
            this.statement = statement;
            if (fuzzy != null) {
                this.fuzzy = fuzzy;
            }
        });

        When("Validating the partial statement", () -> {
            if (fuzzy != null && fuzzy > 0) {
                String whitespaces = RandomStringUtils.random(fuzzy, '\n', '\r', '\t', ' ');
                statement = statement.replaceAll(" ", whitespaces);
            }

            executable = () -> sqlValidator.validate(statement);
        });

        Then("/^The validator had exception message (.*)$/", (String expectedMessage) -> {
            if (StringUtils.isBlank(expectedMessage)) {
                Assertions.assertDoesNotThrow(executable);
            } else {
                var exception = Assertions.assertThrows(SqlValidationException.class, executable);
                assertEquals(expectedMessage, exception.getMessage());
            }
        });
    }

    private static SqlValidator createSqlValidator() {
        FineractProperties properties = new FineractProperties();
        FineractSqlValidationProperties sqlValidation = new FineractSqlValidationProperties();

        List<FineractSqlValidationPatternProperties> patterns = new ArrayList<>();
        patterns.add(
                createPattern("inject-blind", "(?i).*[\"'`]?\\s*[and|or]+\\s*[\"'`]?([\\d\\w])+[\"'`]?\\s*=\\s*[\"'`]?(\\1)[\"'`]?\\s*.*"));
        patterns.add(createPattern("detect-entry-point", "(?i)^[\"'`]?[\\)\\s]+"));
        patterns.add(createPattern("inject-timing",
                "(?i).*[\"'`]?\\s*[and|\\+|&|\\|]+.*\\s*[sleep|pg_sleep|benchmark]+\\s*(\\(\\s*\\d+\\s*[,]?\\s*.*\\s*\\))+.*"));
        patterns.add(createPattern("detect-backend", "(?i).*\\[\\s*\"(\\w+\\(.*\\))=(\\1)\"\\s*,\\s*\"\\w+\"\\s*\\].*"));
        patterns.add(createPattern("detect-column",
                "(?i).*[\"'`]?\\s*(order\\s*by|group\\s*by|union\\s*select)+\\s+([\\d+|null]?\\s*,*\\s*)+\\s*.*"));
        patterns.add(createPattern("detect-out-of-bands", "(?i).*(select)+\\s+(load_file)+.*"));
        patterns.add(createPattern("inject-stacked-query",
                "(?i).*[;]+\\s*(create|drop|alter|truncate|comment|select|insert|update|delete|merge|upsert|call|exec)+.*(from|into|set|table|column|database)*.*"));
        patterns.add(createPattern("inject-comment", "(?i).*\\s+(--|/\\*|#|\\(\\{)++.*"));
        sqlValidation.setPatterns(patterns);

        List<FineractSqlValidationProfileProperties> profiles = new ArrayList<>();
        profiles.add(createProfile("main", "Main Query Validation Profile", List.of("inject-blind", "detect-entry-point", "inject-timing",
                "detect-backend", "detect-column", "detect-out-of-bands", "inject-stacked-query", "inject-comment")));
        profiles.add(createProfile("adhoc", "Adhoc Query Validation Profile", List.of("inject-blind", "detect-entry-point", "inject-timing",
                "detect-backend", "detect-column", "detect-out-of-bands", "inject-stacked-query", "inject-comment")));
        profiles.add(createProfile("dynamic", "Dynamic Query Validation Profile", List.of("inject-blind", "detect-entry-point",
                "inject-timing", "detect-backend", "detect-column", "detect-out-of-bands", "inject-stacked-query", "inject-comment")));
        sqlValidation.setProfiles(profiles);

        properties.setSqlValidation(sqlValidation);

        DefaultSqlValidator validator = new DefaultSqlValidator(properties);
        validator.init();
        return validator;
    }

    private static FineractSqlValidationPatternProperties createPattern(String name, String pattern) {
        FineractSqlValidationPatternProperties p = new FineractSqlValidationPatternProperties();
        p.setName(name);
        p.setPattern(pattern);
        return p;
    }

    private static FineractSqlValidationProfileProperties createProfile(String name, String description, List<String> patternNames) {
        FineractSqlValidationProfileProperties profile = new FineractSqlValidationProfileProperties();
        profile.setName(name);
        profile.setDescription(description);
        profile.setEnabled(true);
        List<FineractSqlValidationPatternReferenceProperties> refs = new ArrayList<>();
        for (int i = 0; i < patternNames.size(); i++) {
            FineractSqlValidationPatternReferenceProperties ref = new FineractSqlValidationPatternReferenceProperties();
            ref.setName(patternNames.get(i));
            ref.setOrder(i);
            refs.add(ref);
        }
        profile.setPatternRefs(refs);
        return profile;
    }
}
