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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.CSVString;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

public class ExportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    private final int frequencyInDays;

    private final List<String> questionnairesToBeExported;

    private final String savePath;

    private final boolean enableLabels;

    ExportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        int frequencyInDays, String[] questionnairesToBeExported,
        String savePath, boolean enableLabels)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.frequencyInDays = frequencyInDays;
        this.questionnairesToBeExported = new ArrayList<>(Arrays.asList(questionnairesToBeExported));
        this.savePath = savePath;
        this.enableLabels = enableLabels;
    }

    @Override
    public void run()
    {
        final String labelsParameter = this.enableLabels ? ".labels" : "";
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        final String modifiedAfterDate = simpleDateFormat.format(getPastDate(this.frequencyInDays));
        final String modifiedBeforeDate = simpleDateFormat.format(new Date());
        final String timePeriod;
        if (this.frequencyInDays == 1) {
            timePeriod = simpleDateFormat.format(getPastDate(1));
        } else {
            final String endModificationDate = simpleDateFormat.format(getPastDate(1));
            timePeriod = modifiedAfterDate + "_" + endModificationDate;
        }
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;
            for (String questionnaire : this.questionnairesToBeExported) {
                final File csvFile = new File(this.savePath + "/ExportedForms_" + questionnaire.replace("/", "_") + "_"
                    + timePeriod + ".csv");
                try (FileWriter writer = new FileWriter(csvFile)) {
                    final String csvPath = String.format(
                        questionnaire + "%s.data.dataFilter:modifiedAfter=%s.dataFilter:"
                            + "modifiedBefore=%s.csv",
                        labelsParameter, modifiedAfterDate, modifiedBeforeDate);
                    final CSVString csv = resolver.resolve(csvPath).adaptTo(CSVString.class);
                    writer.write(csv.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }

    }

    private Date getPastDate(int numberOfDaysAgo)
    {
        Calendar date = new GregorianCalendar();
        date.add(Calendar.DAY_OF_MONTH, -numberOfDaysAgo);
        return date.getTime();
    }
}
