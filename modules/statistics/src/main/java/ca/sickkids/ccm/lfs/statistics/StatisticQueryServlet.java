/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.sickkids.ccm.lfs.statistics;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.servlet.Servlet;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servlet for querying Statistics that returns a JSON object containing values for the x and y axes
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/Statistic", "lfs/StatisticsHomepage" },
    selectors = { "query" },
    methods = { "POST" })

public class StatisticQueryServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticQueryServlet.class);

    private ThreadLocal<Boolean> splitExists = new ThreadLocal<Boolean>()
    {
        @Override
        protected Boolean initialValue()
        {
            return Boolean.FALSE;
        }
    };

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        // set input variables
        JsonParser parser = Json.createParser(request.getInputStream());
        String statisticName = null;
        String xVariable = null;
        String yVariable = null;
        String splitVariable = null;
        while (parser.hasNext()) {
            Event event = parser.next();
            if (event == JsonParser.Event.KEY_NAME ) {
                String key = parser.getString();
                event = parser.next();
                if (key.equals("name")) {
                    statisticName = parser.getString();
                }
                if (key.equals("x-label")) {
                    xVariable = parser.getString();
                }
                if (key.equals("y-label")) {
                    yVariable = parser.getString();
                }
                if (key.equals("splitVar")) {
                    splitVariable = parser.getString();
                }
            }
        }

        try {
            // Check if split variable exists
            if (splitVariable != null) {
                this.splitExists.set(Boolean.TRUE);
            }
            // Steps to returning the calculated statistic:
            // Grab the question that has data for the given x-axis (xVar)
            Node question = request.getResourceResolver().adaptTo(Session.class).getNode(xVariable);

            Iterator<Resource> answers = null;
            Map<Resource, String> data = new HashMap<>();
            Map<String, Map<Resource, String>> dataById = null;

            // Grab all answers that have this question filled out, and the split var (if it exists)
            if (this.splitExists.get()) {
                Node split = request.getResourceResolver().adaptTo(Session.class).getNode(splitVariable);
                answers = getAnswersToQuestion(question, split, request.getResourceResolver());
                data = getAnswersWithType(data, "x", question, request.getResourceResolver());
                data = getAnswersWithType(data, "split", split, request.getResourceResolver());
            }
            else {
                answers = getAnswersToQuestion(question, request.getResourceResolver());
                data = getAnswersWithType(data, "x", question, request.getResourceResolver());
            }

            // Filter those answers based on whether or not their form's subject is of the correct SubjectType (yVar)
            Node correctSubjectType = request.getResourceResolver().adaptTo(Session.class)
                .getNode(yVariable);
            answers = filterAnswersToSubjectType(answers, correctSubjectType);

            dataById = filterAnswersWithType(data, correctSubjectType);

            String xLabel = question.getProperty("text").getString();
            String yLabel = correctSubjectType.getProperty("label").getString();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            String date = simpleDateFormat.format(new Date());

            // Add inputs and time generated to the output JSON
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("timeGenerated", date);
            builder.add("name", statisticName);
            builder.add("x-label", xLabel);
            builder.add("y-label", yLabel);
            if (this.splitExists.get()) {
                Node split = request.getResourceResolver().adaptTo(Session.class).getNode(splitVariable);
                String splitLabel = split.getProperty("text").getString();
                builder.add("split-label", splitLabel);
                // addData(answers, builder, split);
                addDataSplit(dataById, builder);
            }
            else {
                addData(answers, builder);
            }

            // Write the output
            final Writer out = response.getWriter();
            out.write(builder.build().toString());

        } catch (RepositoryException e) {
            LOGGER.error("Failed to obtain statistic: {}", e.getMessage(), e);
        }
    }

    private Map<Resource, String> getAnswersWithType(Map<Resource, String> data, String type, Node question, 
        ResourceResolver resolver) throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [lfs:Answer] as n where n.'question'='"
                + question.getIdentifier() + "'");
        Iterator<Resource> answers = resolver.findResources(query.toString(), Query.JCR_SQL2);

        while (answers.hasNext()) {
            Resource answer = answers.next();
            data.put(answer, type);
        }

        return data;
    }

    private Map<String, Map<Resource, String>> filterAnswersWithType(Map<Resource,String> data, Node subjectType)
        throws RepositoryException
    {
        Iterator<Map.Entry<Resource, String>> entries = data.entrySet().iterator();

        Map<String, Map<Resource, String>> newData = new HashMap<>();

        String correctType = subjectType.getIdentifier();

        // filter out answers without correct subject type
        while (entries.hasNext()) {
            Map.Entry<Resource,String> answer = entries.next();
            Map<Resource, String> newInnerData = new HashMap<>();
            // get parent node
            Node answerParent = getParentNode(answer.getKey().adaptTo(Node.class));
            // get parent subject
            Node answerSubject = answerParent.getProperty("subject").getNode().getProperty("type").getNode();

            if (answerSubject.getIdentifier().equals(correctType)) {
                // if it is the correct type, add to new map
                String uuid = answerParent.getProperty("jcr:uuid").getString();

                //this should create a nested hashmap <formID, <node, string>, <node, string>>
                newInnerData.put(answer.getKey(), answer.getValue());
                newData.put(uuid, newInnerData);
            }
        }

        return newData;
    }

    // WITH split
    private Map<String, Map<String, Integer>> aggregateSplitCounts(Map<Resource, Resource> eachForm)
        throws RepositoryException
    {
        Map<String, Map<String, Integer>> counts = new HashMap<>();

        Map<String, Integer> innerCount = new HashMap<>();

        Iterator<Map.Entry<Resource, Resource>> forms = eachForm.entrySet().iterator();

        // filter out answers without correct subject type
        while (forms.hasNext()) {
            Map.Entry<Resource,Resource> form = forms.next();
            Node xAnswer = form.getKey().adaptTo(Node.class);
            Node splitAnswer = form.getValue().adaptTo(Node.class);

            try {
                String xValue = xAnswer.getProperty("value").getString();
                String splitValue = splitAnswer.getProperty("value").getString();

                // if x value and split value already exist
                if (counts.containsKey(xValue) && counts.get(xValue).containsKey(splitValue)) {
                    innerCount.put(splitValue, counts.get(xValue).get(splitValue) + 1);
                    counts.put(xValue, innerCount);
                }
                // if x value already exists, but not split value
                // if (counts.containsKey(xValue) && !counts.get(xValue).containsKey(splitValue)) {
                //     innerCount.put(splitValue, 1);
                //     counts.put(xValue, innerCount);
                // }
                // else, create both and set to 1 count
                else {
                    innerCount.put(splitValue, 1);
                    counts.put(xValue, innerCount);
                }
            } catch (PathNotFoundException e) {
                LOGGER.error("Value does not exist for question: {}", e.getMessage(), e);
                continue;
            }
        }

        return counts;
    }

     private void addDataSplit(Map<String, Map<Resource, String>> data, JsonObjectBuilder builder) throws RepositoryException
    {
        Map<Resource, Resource> eachForm = new HashMap<>();
        
        // within each form id
        Iterator<Map.Entry<String, Map<Resource, String>>> entries = data.entrySet().iterator();

        while (entries.hasNext()) {
            Map.Entry<String, Map<Resource, String>> answer = entries.next();
            // iterate through inner map
            Iterator<Map.Entry<Resource, String>> child = (answer.getValue()).entrySet().iterator();
            Resource xVar = null;
            Resource splitVar = null;
            while (child.hasNext()) {
                Map.Entry<Resource,String> entry = child.next();
                if ("x".equals(entry.getKey())) {
                    xVar = entry.getKey();
                }
                if ("split".equals(entry.getKey())) {
                    splitVar = entry.getKey();
                }
                // create new map with a xVar and splitVar for each form. TODO: this can maybe be done elsewhere
                eachForm.put(xVar, splitVar);
            }
        }

        Map<String, Map<String, Integer>> counts = aggregateSplitCounts(eachForm);

        // TODO: Convert our HashMap into a JsonObject
        // JsonObjectBuilder innerBuilder = Json.createObjectBuilder();
        // JsonObjectBuilder outerBuilder = Json.createObjectBuilder();

        // Iterator<Map.Entry<String, Map<String, Integer>>> count = counts.entrySet().iterator();

        // while (count.hasNext()) {
        //     Map.Entry<String, Map<String, Integer>> answer = count.next();
        //     // iterate through inner map
        //     Iterator<Map.Entry<String, Integer>> child = (answer.getValue()).entrySet().iterator();
        //     while (child.hasNext()) {
        //         Map.Entry<Resource,Integer> entry = child.next();
        //         innerBuilder.add(entry.getKey(), entry.getValue());
        //     }

        //     outerBuilder.add(answer.getKey(), innerBuilder.build());
        // }

        // builder.add("data", outerBuilder.build());

        // JsonObjectBuilder dataBuilder = Json.createObjectBuilder();
        // Iterator<Map.Entry<String, Integer>> keysMap = counts.keySet().iterator();
        // while (keysMap.hasNext()) {
        //     Map.Entry<String, Integer> key = keysMap.next();
        //     Iterator<String> innerMap = key.keySet().iterator();
        //     while (innerMap.hasNext()) {
        //         String innerKey = innerMap.next();
        //         innerBuilder.add(innerKey, counts.get(innerKey));
        //     }
        //     outerBuilder.add(answer.getKey(), innerBuilder.build());
        // }
        // builder.add("data", outerBuilder.build());
        builder.add("data", "test");
    }

    

    /**
     * Get all answers that have a given question filled out, if split variable does NOT exist.
     *
     * @param question The question node that the answers is to
     * @param resolver A reference to a ResourceResolver
     * @return An iterator of Resources that are Nodes of answers to the given question
     */
    private Iterator<Resource> getAnswersToQuestion(Node question, ResourceResolver resolver) throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [lfs:Answer] as n where n.'question'='"
                + question.getIdentifier() + "'");
        return resolver.findResources(query.toString(), Query.JCR_SQL2);
    }

    /**
     * Get all answers that have a given question filled out, if split variable exists.
     *
     * @param question The question node that the answers is to
     * @param resolver A reference to a ResourceResolver
     * @return An iterator of Resources that are Nodes of answers to the given question
     */
    private Iterator<Resource> getAnswersToQuestion(Node question, Node split, ResourceResolver resolver) throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [lfs:Answer] as n where n.'question'='"
                + question.getIdentifier() + "' OR n.'question'='" 
                + split.getIdentifier() + "'");
        return resolver.findResources(query.toString(), Query.JCR_SQL2);
    }

    /**
     * Filter the given iterator of resources to only include resources whose parent is a Form, whose
     * Subject's type is equal to the given subjectType.
     *
     * @param answers The iterator of answers to filter
     * @param subjectType A subjectType to filter for
     * @return An iterator of Resources that are Nodes of answers for the given subjectType
     */
    private Iterator<Resource> filterAnswersToSubjectType(Iterator<Resource> answers, Node subjectType)
        throws RepositoryException
    {
        Deque<Resource> newAnswers = new LinkedList<>();
        String correctType = subjectType.getIdentifier();
        while (answers.hasNext()) {
            Resource answer = answers.next();
            final Node answerSubjectType = getParentSubject(answer.adaptTo(Node.class));
            if (answerSubjectType.getIdentifier().equals(correctType)) {
                newAnswers.push(answer);
            }
        }
        return newAnswers.iterator();
    }

    /**
     * Aggregate the given Iterator of answers to a map of counts for each unique value.
     *
     * @param answers An iterator of lfs:Answer objects
     * @return A map of values -> counts
     */
    private Map<String, Integer> aggregateCounts(Iterator<Resource> answers) throws RepositoryException
    {
        Map<String, Integer> counts = new HashMap<>();
        while (answers.hasNext()) {
            Node answer = answers.next().adaptTo(Node.class);
            
            try {
                String value = answer.getProperty("value").getString();
                // If this already exists in our counts dict, we add 1 to its value
                // Otherwise, set it to 1 count
                if (counts.containsKey(value)) {
                    counts.put(value, counts.get(value) + 1);
                } else {
                    counts.put(value, 1);
                }
            } catch (PathNotFoundException e) {
                LOGGER.error("Value does not exist for question: {}", e.getMessage(), e);
                continue;
            }
        }
        return counts;
    }

    // if split variable does NOT exist
    private void addData(Iterator<Resource> answers, JsonObjectBuilder builder) throws RepositoryException
    {
        // Aggregate our counts
        Map<String, Integer> counts = aggregateCounts(answers);

        // Convert our HashMap into a JsonObject
        JsonObjectBuilder dataBuilder = Json.createObjectBuilder();
        Iterator<String> keysMap = counts.keySet().iterator();
        while (keysMap.hasNext()) {
            String key = keysMap.next();
            dataBuilder.add(key, counts.get(key));
        }
        builder.add("data", dataBuilder.build());
    }

    // if split variable DOES exist
    private void addData(Iterator<Resource> answers, JsonObjectBuilder builder, Node split) throws RepositoryException
    {
        // Aggregate our counts
        Map<String, Integer> counts = aggregateCounts(answers);

        // TODO: also aggregate counts by split
            // Find the form that contains answer
            // Find the question that corresponds to split var --> grab value
            // Aggregate counts by that value WITHIN each x var

        // Convert our HashMap into a JsonObject
        JsonObjectBuilder dataBuilder = Json.createObjectBuilder();
        Iterator<String> keysMap = counts.keySet().iterator();
        while (keysMap.hasNext()) {
            String key = keysMap.next();
            dataBuilder.add(key, counts.get(key));
        }
        builder.add("data", dataBuilder.build());
    }

    /**
     * Get the parent subject type of a given lfs:Answer node.
     *
     * @param node A node corresponding to an lfs:Answer
     * @return A node corresponding to the parent subject type
     */
    public Node getParentSubject(Node node) throws RepositoryException
    {
        // Recursively go through our parents until we find a lfs:Form node
        // If we somehow reach the top level, return an error
        Node answerParent = node.getParent();
        while (!"lfs:Form".equals(answerParent.getPrimaryNodeType().getName()) && answerParent.getDepth() != 0) {
            answerParent = answerParent.getParent();
        }

        // If we never find a form by going upwards, this lfs:Answer is malformed
        if (answerParent.getDepth() == 0) {
            String error = String.format("Tried to obtain the parent lfs:Form for node {} but failed to find one",
                node.getPath());
            throw new RepositoryException(error);
        }

        Node answerSubject = answerParent.getProperty("subject").getNode();
        return answerSubject.getProperty("type").getNode();
    }

    public Node getParentNode(Node node) throws RepositoryException
    {
        // Recursively go through our parents until we find a lfs:Form node
        // If we somehow reach the top level, return an error
        Node answerParent = node.getParent();
        while (!"lfs:Form".equals(answerParent.getPrimaryNodeType().getName()) && answerParent.getDepth() != 0) {
            answerParent = answerParent.getParent();
        }

        // If we never find a form by going upwards, this lfs:Answer is malformed
        if (answerParent.getDepth() == 0) {
            String error = String.format("Tried to obtain the parent lfs:Form for node {} but failed to find one",
                node.getPath());
            throw new RepositoryException(error);
        }

        return answerParent;
    }
}
