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

import graphql.language.ObjectField;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.scalar.*;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLScalarType;
import graphql.schema.InputValueWithState;

public class GraphQLUtil {

    public static GraphQLScalarType newScalar(String name, String type) {

        switch (type) {
            case "Int":
                return GraphQLScalarType.newScalar().name(name).coercing(new GraphqlIntCoercing()).build();
            case "Float":
                return GraphQLScalarType.newScalar().name(name).coercing(new GraphqlFloatCoercing()).build();
            case "Boolean":
                return GraphQLScalarType.newScalar().name(name).coercing(new GraphqlBooleanCoercing()).build();
            case "ID":
                return GraphQLScalarType.newScalar().name(name).coercing(new GraphqlIDCoercing()).build();
            case "String":
            default:
                return GraphQLScalarType.newScalar().name(name).coercing(new GraphqlStringCoercing()).build();
        }
    }

    public static String getStringValue(ObjectField field) {
        if (field == null) {
            return null;
        }

        Value value = field.getValue();
        if (value == null || !(value instanceof StringValue)) {
            return null;
        }


        StringValue strValue = (StringValue) value;

        return strValue.getValue();
    }

    public static String getStringArgument(GraphQLDirective directive, String argumentName) {
        if (directive == null) {
            return "";
        }

        GraphQLArgument arg = directive.getArgument(argumentName);
        if (arg == null) {
            return "";
        }

        InputValueWithState argumentValue;

        if (arg.hasSetValue()) {
            argumentValue = arg.getArgumentValue();
        } else if (arg.hasSetDefaultValue()) {
            argumentValue = arg.getArgumentDefaultValue();
        } else {
            return "";
        }

        if (argumentValue == null) {
            return "";
        }

        Object value = argumentValue.getValue();
        if (value == null || !(value instanceof StringValue)) {
            return "";
        }

        String strValue = ((StringValue) value).getValue();
        if (strValue == null) {
            return  "";
        }

        return strValue;
    }
}
