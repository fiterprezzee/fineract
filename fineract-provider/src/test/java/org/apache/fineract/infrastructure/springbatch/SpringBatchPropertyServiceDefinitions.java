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
package org.apache.fineract.infrastructure.springbatch;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java8.En;
import java.util.List;
import org.apache.fineract.infrastructure.core.config.FineractProperties;

public class SpringBatchPropertyServiceDefinitions implements En {

    private FineractProperties fineractProperties;
    private PropertyService propertyService;
    private int partitionSize;
    private int chunkSize;
    private int retryLimit;

    public SpringBatchPropertyServiceDefinitions() {
        Given("Property Service is initialized", () -> {
            fineractProperties = new FineractProperties();
            FineractProperties.FineractPartitionedJob partitionedJob = new FineractProperties.FineractPartitionedJob();
            FineractProperties.PartitionedJobProperty prop = new FineractProperties.PartitionedJobProperty();
            prop.setJobName("LOAN_COB");
            prop.setChunkSize(100);
            prop.setPartitionSize(100);
            prop.setRetryLimit(5);
            partitionedJob.setPartitionedJobProperties(List.of(prop));
            fineractProperties.setPartitionedJob(partitionedJob);
            propertyService = new PropertyServiceImpl(fineractProperties);
        });

        When("partition size is fetched for {string}", (String jobName) -> {
            partitionSize = propertyService.getPartitionSize(jobName);
        });

        When("chunk size is fetched for {string}", (String jobName) -> {
            chunkSize = propertyService.getChunkSize(jobName);
        });

        When("retry limit is fetched for {string}", (String jobName) -> {
            retryLimit = propertyService.getRetryLimit(jobName);
        });

        Then("partition size is {int}", (Integer value) -> {
            assertThat(partitionSize).isEqualTo(value);
        });

        Then("chunk size is {int}", (Integer value) -> {
            assertThat(chunkSize).isEqualTo(value);
        });

        Then("retry limit is {int}", (Integer value) -> {
            assertThat(retryLimit).isEqualTo(value);
        });
    }
}
