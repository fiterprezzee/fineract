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
package org.apache.fineract.infrastructure.core;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.java8.En;
import org.apache.fineract.infrastructure.core.config.FineractProperties;

public class InstanceTypeStepDefinitions implements En {

    static FineractProperties sharedFineractProperties;

    private FineractProperties fineractProperties;

    public InstanceTypeStepDefinitions() {
        fineractProperties = new FineractProperties();
        FineractProperties.FineractModeProperties mode = new FineractProperties.FineractModeProperties();
        fineractProperties.setMode(mode);
        setSharedFineractProperties(fineractProperties);

        Given("Set every Fineract instance type to false", () -> {
            fineractProperties.getMode().setWriteEnabled(false);
            fineractProperties.getMode().setReadEnabled(false);
            fineractProperties.getMode().setBatchWorkerEnabled(false);
            fineractProperties.getMode().setBatchManagerEnabled(false);
        });
        Given("Fineract instance is a write instance", () -> {
            fineractProperties.getMode().setWriteEnabled(true);
        });
        Given("Fineract instance is a read instance", () -> {
            fineractProperties.getMode().setReadEnabled(true);
        });
        Given("Fineract instance is a batch manager instance", () -> {
            fineractProperties.getMode().setBatchManagerEnabled(true);
        });
    }

    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "Test class requires static field sharing between test instances")
    @SuppressWarnings("StaticAssignmentInConstructor")
    private static void setSharedFineractProperties(FineractProperties properties) {
        sharedFineractProperties = properties;
    }
}
