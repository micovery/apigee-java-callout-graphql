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

package com.google.apigee.callouts.graphql;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.util.Locale;
import java.util.Map;

public class GraphQLAnyCoercing implements Coercing<Object, Object> {
    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
        return dataFetcherResult;
    }

    public Object serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        return this.serialize(dataFetcherResult);
    }

    public Object parseValue(Object input) throws CoercingParseValueException {
        return input;
    }

    public Object parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        return this.parseValue(input);
    }

    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
        return input;
    }

    public Object parseLiteral(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
        return this.parseLiteral(input);
    }

    public Object parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        return this.parseLiteral(input, variables.toMap());
    }

    public Value valueToLiteral(Object input) {
        return (Value) input;
    }

    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        return this.valueToLiteral(input);
    }

}
