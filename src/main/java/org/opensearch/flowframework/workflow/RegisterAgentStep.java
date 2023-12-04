/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.workflow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.flowframework.exception.FlowFrameworkException;
import org.opensearch.flowframework.util.ParseUtils;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgent.MLAgentBuilder;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.opensearch.flowframework.common.CommonValue.AGENT_ID;
import static org.opensearch.flowframework.common.CommonValue.APP_TYPE_FIELD;
import static org.opensearch.flowframework.common.CommonValue.CREATED_TIME;
import static org.opensearch.flowframework.common.CommonValue.DESCRIPTION_FIELD;
import static org.opensearch.flowframework.common.CommonValue.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.flowframework.common.CommonValue.MEMORY_FIELD;
import static org.opensearch.flowframework.common.CommonValue.MODEL_ID;
import static org.opensearch.flowframework.common.CommonValue.NAME_FIELD;
import static org.opensearch.flowframework.common.CommonValue.PARAMETERS_FIELD;
import static org.opensearch.flowframework.common.CommonValue.TOOLS_FIELD;
import static org.opensearch.flowframework.common.CommonValue.TYPE;
import static org.opensearch.flowframework.util.ParseUtils.getStringToStringMap;

/**
 * Step to register an agent
 */
public class RegisterAgentStep implements WorkflowStep {

    private static final Logger logger = LogManager.getLogger(RegisterAgentStep.class);

    private MachineLearningNodeClient mlClient;

    static final String NAME = "register_agent";

    private static final String LLM_MODEL_ID = "llm.model_id";
    private static final String LLM_PARAMETERS = "llm.parameters";

    private List<MLToolSpec> mlToolSpecList;

    /**
     * Instantiate this class
     * @param mlClient client to instantiate MLClient
     */
    public RegisterAgentStep(MachineLearningNodeClient mlClient) {
        this.mlClient = mlClient;
        this.mlToolSpecList = new ArrayList<>();
    }

    @Override
    public CompletableFuture<WorkflowData> execute(
        String currentNodeId,
        WorkflowData currentNodeInputs,
        Map<String, WorkflowData> outputs,
        Map<String, String> previousNodeInputs
    ) throws IOException {

        CompletableFuture<WorkflowData> registerAgentModelFuture = new CompletableFuture<>();

        ActionListener<MLRegisterAgentResponse> actionListener = new ActionListener<>() {
            @Override
            public void onResponse(MLRegisterAgentResponse mlRegisterAgentResponse) {
                logger.info("Agent registration successful for the agent {}", mlRegisterAgentResponse.getAgentId());
                registerAgentModelFuture.complete(
                    new WorkflowData(
                        Map.ofEntries(Map.entry(AGENT_ID, mlRegisterAgentResponse.getAgentId())),
                        currentNodeInputs.getWorkflowId(),
                        currentNodeInputs.getNodeId()
                    )
                );
            }

            @Override
            public void onFailure(Exception e) {
                logger.error("Failed to register the agent");
                registerAgentModelFuture.completeExceptionally(new FlowFrameworkException(e.getMessage(), ExceptionsHelper.status(e)));
            }
        };

        Set<String> requiredKeys = Set.of(NAME_FIELD, TYPE);
        Set<String> optionalKeys = Set.of(
            DESCRIPTION_FIELD,
            LLM_MODEL_ID,
            LLM_PARAMETERS,
            TOOLS_FIELD,
            PARAMETERS_FIELD,
            MEMORY_FIELD,
            CREATED_TIME,
            LAST_UPDATED_TIME_FIELD,
            APP_TYPE_FIELD
        );

        try {
            Map<String, Object> inputs = ParseUtils.getInputsFromPreviousSteps(
                requiredKeys,
                optionalKeys,
                currentNodeInputs,
                outputs,
                previousNodeInputs
            );

            String type = (String) inputs.get(TYPE);
            String name = (String) inputs.get(NAME_FIELD);
            String description = (String) inputs.get(DESCRIPTION_FIELD);
            String llmModelId = (String) inputs.get(LLM_MODEL_ID);
            Map<String, String> llmParameters = getStringToStringMap(inputs.get(PARAMETERS_FIELD), LLM_PARAMETERS);
            List<MLToolSpec> tools = getTools(previousNodeInputs, outputs);
            Map<String, String> parameters = getStringToStringMap(inputs.get(PARAMETERS_FIELD), PARAMETERS_FIELD);
            MLMemorySpec memory = getMLMemorySpec(inputs.get(MEMORY_FIELD));
            Instant createdTime = Instant.ofEpochMilli((Long) inputs.get(CREATED_TIME));
            Instant lastUpdateTime = Instant.ofEpochMilli((Long) inputs.get(LAST_UPDATED_TIME_FIELD));
            String appType = (String) inputs.get(APP_TYPE_FIELD);

            // Case when modelId is present in previous node inputs
            if (llmModelId == null) {
                llmModelId = getLlmModelId(previousNodeInputs, outputs);
            }

            // Case when modelId is not present at all
            if (llmModelId == null) {
                registerAgentModelFuture.completeExceptionally(
                    new FlowFrameworkException("llm model id is not provided", RestStatus.BAD_REQUEST)
                );
                return registerAgentModelFuture;
            }

            LLMSpec llmSpec = getLLMSpec(llmModelId, llmParameters);

            MLAgentBuilder builder = MLAgent.builder().name(name);

            if (description != null) {
                builder.description(description);
            }

            builder.type(type)
                .llm(llmSpec)
                .tools(tools)
                .parameters(parameters)
                .memory(memory)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .appType(appType);

            MLAgent mlAgent = builder.build();

            mlClient.registerAgent(mlAgent, actionListener);

        } catch (FlowFrameworkException e) {
            registerAgentModelFuture.completeExceptionally(e);
        }
        return registerAgentModelFuture;
    }

    @Override
    public String getName() {
        return NAME;
    }

    private List<MLToolSpec> getTools(Map<String, String> previousNodeInputs, Map<String, WorkflowData> outputs) {
        List<MLToolSpec> mlToolSpecList = new ArrayList<>();
        List<String> previousNodes = previousNodeInputs.entrySet()
            .stream()
            .filter(e -> TOOLS_FIELD.equals(e.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (previousNodes != null) {
            previousNodes.forEach((previousNode) -> {
                WorkflowData previousNodeOutput = outputs.get(previousNode);
                if (previousNodeOutput != null && previousNodeOutput.getContent().containsKey(TOOLS_FIELD)) {
                    MLToolSpec mlToolSpec = (MLToolSpec) previousNodeOutput.getContent().get(TOOLS_FIELD);
                    logger.info("Tool added {}", mlToolSpec.getType());
                    mlToolSpecList.add(mlToolSpec);
                }
            });
        }
        return mlToolSpecList;
    }

    private String getLlmModelId(Map<String, String> previousNodeInputs, Map<String, WorkflowData> outputs) {
        // Case when modelId is already pass in the template
        String llmModelId = null;

        // Case when modelId is passed through previousSteps
        Optional<String> previousNode = previousNodeInputs.entrySet()
            .stream()
            .filter(e -> MODEL_ID.equals(e.getValue()))
            .map(Map.Entry::getKey)
            .findFirst();

        if (previousNode.isPresent()) {
            WorkflowData previousNodeOutput = outputs.get(previousNode.get());
            if (previousNodeOutput != null && previousNodeOutput.getContent().containsKey(MODEL_ID)) {
                llmModelId = previousNodeOutput.getContent().get(MODEL_ID).toString();
            }
        }
        return llmModelId;
    }

    private LLMSpec getLLMSpec(String llmModelId, Map<String, String> llmParameters) {
        if (llmModelId == null) {
            throw new FlowFrameworkException("model id for llm is null", RestStatus.BAD_REQUEST);
        }
        LLMSpec.LLMSpecBuilder builder = LLMSpec.builder();
        builder.modelId(llmModelId);
        if (llmParameters != null) {
            builder.parameters(llmParameters);
        }

        LLMSpec llmSpec = builder.build();
        return llmSpec;
    }

    private MLMemorySpec getMLMemorySpec(Object mlMemory) {

        Map<?, ?> map = (Map<?, ?>) mlMemory;
        String type = null;
        String sessionId = null;
        Integer windowSize = null;
        type = (String) map.get(MLMemorySpec.MEMORY_TYPE_FIELD);
        if (type == null) {
            throw new IllegalArgumentException("agent name is null");
        }
        sessionId = (String) map.get(MLMemorySpec.SESSION_ID_FIELD);
        windowSize = (Integer) map.get(MLMemorySpec.WINDOW_SIZE_FIELD);

        @SuppressWarnings("unchecked")
        MLMemorySpec.MLMemorySpecBuilder builder = MLMemorySpec.builder();

        builder.type(type);
        if (sessionId != null) {
            builder.sessionId(sessionId);
        }
        if (windowSize != null) {
            builder.windowSize(windowSize);
        }

        MLMemorySpec mlMemorySpec = builder.build();
        return mlMemorySpec;

    }

}
