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
package io.uhndata.cards.forms.internal;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * An {@link Editor} that creates any missing answers and answer sections, following the questionnaire as a template.
 *
 * @version $Id$
 */
public class CreateMissingAnswersEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateMissingAnswersEditor.class);

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    /** The current user session. */
    private final Session currentSession;

    /**
     * A session that has access to all the questionnaire questions and can access restricted questions. This session
     * should not be used for accessing any user data.
     */
    private Session serviceSession;

    private final QuestionnaireUtils questionnaireUtils;

    private final FormUtils formUtils;

    private boolean isFormNode;

    private boolean shouldRunOnLeave;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param currentSession the current user session
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     */
    public CreateMissingAnswersEditor(final NodeBuilder nodeBuilder, final Session currentSession,
        final ResourceResolverFactory rrf, final QuestionnaireUtils questionnaireUtils, final FormUtils formUtils)
    {
        LOGGER.error("Constructor for {}", nodeBuilder);
        this.currentNodeBuilder = nodeBuilder;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.shouldRunOnLeave = false;
        this.currentSession = currentSession;
        this.rrf = rrf;
        this.isFormNode = this.formUtils.isForm(nodeBuilder);
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        LOGGER.error("CHILD ADDED {} {}", name, after);
        if (this.isFormNode) {
            // No need to descend further down, we already know that this is a form that has changes
            return null;
        } else {
            return new CreateMissingAnswersEditor(this.currentNodeBuilder.getChildNode(name), this.currentSession,
                this.rrf, this.questionnaireUtils, this.formUtils);
        }
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return childNodeAdded(name, after);
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        LOGGER.error("LEAVE \n\n\n {} \n\n\n {} \n\n\n", this.currentNodeBuilder.getNodeState(), after);
    }

    @Override
    public void enter(final NodeState before, final NodeState after)
    {
        LOGGER.error("ENTER \n\n\n {} \n\n\n {} \n\n\n{} \n\n\n", this.currentNodeBuilder.getNodeState(), before,
            after);
        if (!this.isFormNode) {
            return;
        }

        try (ResourceResolver serviceResolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "createMissingAnswers"))) {
            if (serviceResolver != null) {
                this.serviceSession = serviceResolver.adaptTo(Session.class);
                handleLeave(after);
            }
        } catch (LoginException e) {
            // Should not happen
        } finally {
            this.serviceSession = null;
        }
    }

    private void handleLeave(final NodeState form)
    {
        // Get a list of all unanswered reference questions
        final Node questionnaireNode = getQuestionnaire();
        if (questionnaireNode == null) {
            return;
        }

        FormGenerator generator = new FormGenerator(this.questionnaireUtils, this.formUtils,
            this.currentSession.getUserID());
        generator.createMissingNodes(questionnaireNode, this.currentNodeBuilder);
    }

    private Node getQuestionnaire()
    {
        final String questionnaireId = this.currentNodeBuilder.getProperty("questionnaire").getValue(Type.REFERENCE);
        try {
            return this.serviceSession.getNodeByIdentifier(questionnaireId);
        } catch (RepositoryException e) {
            return null;
        }
    }
}
