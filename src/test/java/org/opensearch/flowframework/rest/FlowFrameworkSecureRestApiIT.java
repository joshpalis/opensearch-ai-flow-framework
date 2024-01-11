/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.rest;

import org.opensearch.client.ResponseException;
import org.opensearch.common.util.io.IOUtils;
import org.opensearch.flowframework.FlowFrameworkRestTestCase;
import org.opensearch.flowframework.TestHelpers;
import org.opensearch.flowframework.model.Template;
import org.junit.After;

import java.io.IOException;

public class FlowFrameworkSecureRestApiIT extends FlowFrameworkRestTestCase {

    @After
    public void tearDownSecureTests() throws IOException {
        IOUtils.close(fullAccessClient(), readAccessClient());
        deleteUser(FULL_ACCESS_USER);
        deleteUser(READ_ACCESS_USER);
    }

    public void testCreateWorkflowWithReadAccess() throws Exception {
        Template template = TestHelpers.createTemplateFromFile("register-deploylocalsparseencodingmodel.json");
        ResponseException exception = expectThrows(ResponseException.class, () -> createWorkflow(readAccessClient(), template));
        assertTrue(exception.getMessage().contains("no permissions for [cluster:admin/opensearch/flow_framework/workflow/create]"));
    }

    public void testProvisionWorkflowWithReadAccess() throws Exception {
        ResponseException exception = expectThrows(ResponseException.class, () -> provisionWorkflow(readAccessClient(), "test"));
        assertTrue(exception.getMessage().contains("no permissions for [cluster:admin/opensearch/flow_framework/workflow/provision]"));
    }

    public void testDeleteWorkflowWithReadAccess() throws Exception {
        ResponseException exception = expectThrows(ResponseException.class, () -> deleteWorkflow(readAccessClient(), "test"));
        assertTrue(exception.getMessage().contains("no permissions for [cluster:admin/opensearch/flow_framework/workflow/delete]"));
    }

    public void testDeprovisionWorkflowWithReadAcess() throws Exception {
        ResponseException exception = expectThrows(ResponseException.class, () -> deprovisionWorkflow(readAccessClient(), "test"));
        assertTrue(exception.getMessage().contains("no permissions for [cluster:admin/opensearch/flow_framework/workflow/deprovision]"));
    }

}