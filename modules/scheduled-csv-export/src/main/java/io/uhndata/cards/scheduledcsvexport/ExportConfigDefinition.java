/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.scheduledcsvexport;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Survey csv export",
    description = "Configuration for the Surveys csv exporter")
public @interface ExportConfigDefinition
{
    /** Default value of how often the task should be invoked. */
    int FREQUENCY_IN_DAYS = 1;

    /** Cron-readable export schedule. */
    String NIGHTLY_EXPORT_SCHEDULE = "0 0 0 * * ? *";

    /** Default value of the local path where exported survey CSV files should be saved to. */
    String SAVE_PATH = ".";

    @AttributeDefinition(name = "Name", description = "Configuration name")
    String name();

    @AttributeDefinition(name = "Frequency in days", description = "Time interval (days) between task invocations")
    int frequency_in_days() default FREQUENCY_IN_DAYS;

    @AttributeDefinition(name = "Export schedule", description = "Cron-readable export schedule")
    String export_schedule() default NIGHTLY_EXPORT_SCHEDULE;

    @AttributeDefinition(name = "Questionnaires to be exported",
        description = "List of questionnaires specified to be exported periodically, as full paths")
    String[] questionnaires_to_be_exported();

    @AttributeDefinition(name = "Save path",
        description = "The local disk path to the directory where exported survey CSV files are to be saved")
    String save_path() default SAVE_PATH;

    @AttributeDefinition(
        name = "Enable labels processor",
        description = "This property indicates whether the answer labels will be exported instead of the values",
        type = AttributeType.BOOLEAN)
    boolean enable_label();
}
