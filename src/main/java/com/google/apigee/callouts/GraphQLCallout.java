// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.apigee.callouts;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.google.apigee.callouts.graphql.GraphQLProcessor;
import com.google.apigee.callouts.util.Debug;
import com.google.apigee.callouts.util.Logger;
import com.google.apigee.callouts.util.VarResolver;
import com.google.gson.Gson;
import graphql.ParseAndValidateResult;
import graphql.language.*;
import graphql.validation.ValidationError;
import org.javatuples.Triplet;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GraphQLCallout implements Execution {
    public static final String CALLOUT_VAR_PREFIX = "graphql";
    public static final String PROP_SCHEMA_BASE64_REF = "schema-base64-ref";
    public static final String PROP_MESSAGE_REF = "message-ref";
    public static final String MUTATION_FIELD = "mutation";
    public static final String QUERY_FIELD = "query";
    public static final String VARIABLES_FIELD = "variables";

    private static Gson gson = new Gson();


    private enum OperationType {
        Unknown,
        Query,
        Mutation,
    }


    private final Map properties;

    public GraphQLCallout(Map properties) throws UnsupportedEncodingException {
        this.properties = properties;
    }

    private void saveOutputs(MessageContext msgCtx, Logger logger) {
        msgCtx.setVariable(CALLOUT_VAR_PREFIX + ".info.stdout", new String(logger.stdoutOS.toByteArray(), StandardCharsets.UTF_8));
        msgCtx.setVariable(CALLOUT_VAR_PREFIX + ".info.stderr", new String(logger.stderrOS.toByteArray(), StandardCharsets.UTF_8));
    }

    public ExecutionResult execute(MessageContext messageContext, ExecutionContext executionContext) {
        Logger logger = new Logger();

        try {
            VarResolver vars = new VarResolver(messageContext, properties);
            Debug dbg = new Debug(messageContext, CALLOUT_VAR_PREFIX);

            String schemaTextBase64 = vars.getVar(vars.getProp(PROP_SCHEMA_BASE64_REF));
            String schemaText = new String(Base64.getDecoder().decode(schemaTextBase64));
            String messageVariable = vars.getProp(PROP_MESSAGE_REF);
            Message msg = (Message) messageContext.getVariable(messageVariable);

            GraphQLProcessor gql = new GraphQLProcessor(logger);

            Triplet<ExecutionResult, Map<String, Object>, String> result = validateGraphQLMessage(gql, msg.getContent(), schemaText);

            setFlowVars(messageContext, result.getValue1());
            setContent(msg, result.getValue2());

            return result.getValue0();
        } catch (Error | Exception e) {
            e.printStackTrace(logger.stderr);
            return ExecutionResult.ABORT;
        } finally {
            saveOutputs(messageContext, logger);
        }
    }

    private void setContent(Message msg, String content) {
        if (content != null) {
            msg.setContent(content);
        }
    }
    private void setFlowVars(MessageContext messageContext, Map<String, Object> flowVars) {
        for (Map.Entry<String, Object> flowVar : flowVars.entrySet()) {
            messageContext.setVariable(flowVar.getKey(), flowVar.getValue());
        }
    }

    public Triplet<ExecutionResult, Map<String, Object>, String> validateGraphQLMessage(GraphQLProcessor gql, String content, String schemaText) {


        Map<String, Object> outFlowVars = new HashMap<>();

        if (content == null) {
            outFlowVars.put(CALLOUT_VAR_PREFIX + ".failed", true);
            outFlowVars.put(CALLOUT_VAR_PREFIX + ".error.0.message",  "message body missing");
            return new Triplet<>(ExecutionResult.SUCCESS, outFlowVars, null);
        }

        Map<String, Object> json = gson.fromJson(content, Map.class);

        Object mutationObj = json.get(MUTATION_FIELD);
        Object queryObj = json.get(QUERY_FIELD);
        Object variables = json.get(VARIABLES_FIELD);

        OperationType operationType = OperationType.Unknown;
        String inputText = "";
        if (queryObj != null && queryObj instanceof String) {
            operationType = OperationType.Query;
            inputText = (String) queryObj;
        } else if (mutationObj != null && mutationObj instanceof String) {
            operationType = OperationType.Mutation;
            inputText = (String) mutationObj;
        }

        if (variables == null || !(variables instanceof Map)) {
            variables = new HashMap<String, Object>();
        }

        if (operationType.equals(OperationType.Unknown)) {
            outFlowVars.put(CALLOUT_VAR_PREFIX + ".failed", true);
            outFlowVars.put(CALLOUT_VAR_PREFIX + ".error.0.message", "unknown operation type (expected query or mutation");
            return new Triplet<>(ExecutionResult.SUCCESS, outFlowVars, null);
        }

        DirectivesValidator validator = new DirectivesValidator();
        validator.addValidator(new VisibilityValidator());
        validator.addValidator(new PatternValidator());

        ParseAndValidateResult result = gql.validateInputWithSchema(inputText, (Map<String, Object>) variables, schemaText, validator);

        if (result.isFailure()) {
            List<ValidationError> validationErrors = result.getValidationErrors();
            outFlowVars.put(CALLOUT_VAR_PREFIX + ".failed", true);

            for (int i = 0; i < validationErrors.size(); i++) {
                outFlowVars.put(CALLOUT_VAR_PREFIX + ".error." + i + ".message", validationErrors.get(i).getMessage());
            }
            return new Triplet<>(ExecutionResult.SUCCESS, outFlowVars, null);
        }


        String outputText = AstPrinter.printAst(result.getDocument());
        if (operationType.equals(OperationType.Mutation)) {
            json.put(MUTATION_FIELD, outputText);
        } else if (operationType.equals(OperationType.Query)) {
            json.put(QUERY_FIELD, outputText);
        }

        outFlowVars.put(CALLOUT_VAR_PREFIX + ".failed", false);
        return new Triplet<>(ExecutionResult.SUCCESS, outFlowVars, gson.toJson(json));
    }


}
