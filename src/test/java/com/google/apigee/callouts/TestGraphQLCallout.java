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

import com.apigee.flow.execution.ExecutionResult;
import com.google.apigee.callouts.graphql.GraphQLProcessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import graphql.ParseAndValidateResult;
import graphql.language.AstPrinter;
import graphql.parser.Parser;
import graphql.validation.ValidationError;
import org.javatuples.Triplet;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGraphQLCallout {

    @Test
    public void testSimpleMutation() throws IOException {
        String inputQueryPath = "/simple_mutation.graphql";
        ParseAndValidateResult result = callParseAndValidate(inputQueryPath, "/schema.graphql");
        Assert.assertFalse(result.isFailure());

        String expectedQuery = AstPrinter.printAst(Parser.parse(readResourceFile(inputQueryPath)));
        String outputQuery = AstPrinter.printAst(result.getDocument());

        Assert.assertEquals("output mutation must match", expectedQuery, outputQuery);

    }

    @Test
    public void testVisibilityDirective() throws IOException {
        String inputQueryPath = "/simple_query.graphql";
        ParseAndValidateResult result = callParseAndValidate(inputQueryPath, "/schema.graphql");
        Assert.assertFalse(result.isFailure());


        String expectedQuery = readResourceFile(inputQueryPath).replaceAll("snow_condition", "");
        expectedQuery = AstPrinter.printAst(Parser.parse(expectedQuery));
        String outputQuery = AstPrinter.printAst(result.getDocument());
        Assert.assertEquals("output query must match", expectedQuery, outputQuery);
    }

    @Test
    public void testPatternDirective() throws IOException {
        ParseAndValidateResult result = callParseAndValidate("/bad_mutation.graphql", "/schema.graphql");
        Assert.assertTrue(result.isFailure());

        List<ValidationError> errors = result.getValidationErrors();
        Assert.assertTrue(errors.size() == 1);

        String expected = "field \"name\" with value \"lowercase\" does not match pattern ^[A-Z].*$";
        Assert.assertEquals("error string must match", expected, errors.get(0).getMessage());
    }

    @Test
    public void testQueryFromJSON() throws IOException {
        String queryResourcePath = "/simple_query.json";

        Triplet<ExecutionResult, Map<String, Object>, String> result = callValidateGraphQLMessage(queryResourcePath, "/schema.graphql");

        Assert.assertEquals(ExecutionResult.SUCCESS, result.getValue0());
        Assert.assertFalse((Boolean) result.getValue1().get("graphql.failed"));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Map jsonMap = gson.fromJson((String) result.getValue2(), Map.class);
        String outputQuery = gson.toJson(jsonMap);
        String expectedQuery = readResourceFile(queryResourcePath);
        Assert.assertEquals("output query must match", expectedQuery, outputQuery);
    }

    @Test
    public void testIntrospectionFromJSON() throws IOException {
        String inputQueryJSONPath = "/instrospection_query.json";
        Triplet<ExecutionResult, Map<String, Object>, String> result = callValidateGraphQLMessage(inputQueryJSONPath, "/schema.graphql");
        Assert.assertEquals(ExecutionResult.SUCCESS, result.getValue0());
        Assert.assertFalse((Boolean) result.getValue1().get("graphql.failed"));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map jsonMap = gson.fromJson((String) result.getValue2(), Map.class);

        String expectedQueryJSON = readResourceFile(inputQueryJSONPath);
        String outputQueryJSON = gson.toJson(jsonMap);

        Assert.assertEquals("output query JSON must match", expectedQueryJSON, outputQueryJSON);
    }

    public Triplet<ExecutionResult, Map<String, Object>, String> callValidateGraphQLMessage(String inputPath, String schemaPath) throws IOException {
        GraphQLCallout callout = new GraphQLCallout(new HashMap());

        GraphQLProcessor gql = new GraphQLProcessor();
        String schemaText = gql.loadResourceAsString(schemaPath);
        String jsonText = gql.loadResourceAsString(inputPath);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        return callout.validateGraphQLMessage(gql, jsonText, schemaText);
    }

    private ParseAndValidateResult callParseAndValidate(String inputPath, String schemaPath) throws IOException {
        GraphQLCallout callout = new GraphQLCallout(new HashMap());

        GraphQLProcessor gql = new GraphQLProcessor();
        String query = gql.loadResourceAsString(inputPath);
        String schema = gql.loadResourceAsString(schemaPath);

        DirectivesValidator validator = new DirectivesValidator();
        validator.addValidator(new VisibilityValidator());
        validator.addValidator(new PatternValidator());

        return gql.validateInputWithSchema(query, new HashMap<>(), schema, validator);
    }

    private String readResourceFile(String resourcePath) throws IOException {
        URL url = GraphQLCallout.class.getResource(resourcePath);
        String data = new String(Files.readAllBytes(Paths.get(url.getPath())));
        return data;
    }
}
