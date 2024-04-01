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

import com.google.apigee.callouts.graphql.GraphQLDirectiveValidator;
import com.google.apigee.callouts.graphql.GraphQLValidationException;
import com.google.apigee.callouts.graphql.GraphQLVisitor;
import graphql.ExecutionInput;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.ObjectField;
import graphql.schema.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DirectivesValidator implements GraphQLVisitor {
    Map<String, GraphQLDirectiveValidator> validators = new HashMap<>();

    public void addValidator(GraphQLDirectiveValidator validator) {
        this.validators.put(validator.getName(), validator);
    }

    @Override
    public Field visitOutputField(Field field, GraphQLFieldDefinition definition, ExecutionInput input) throws GraphQLValidationException {
        List<GraphQLDirective> directives = definition.getDirectives();
        for (GraphQLDirective directive : directives) {
            String directiveName = directive.getName();
            if (validators.containsKey(directiveName)) {
                field = validators.get(directiveName).validateOutputField(field, directive, input);
                if (field == null) {
                    break;
                }
            }
        }
        return field;
    }

    @Override
    public Argument visitArgument(Argument argument, GraphQLArgument definition, ExecutionInput input) {
        List<GraphQLDirective> directives = definition.getDirectives();
        for (GraphQLDirective directive : directives) {
            String directiveName = directive.getName();
            if (validators.containsKey(directiveName)) {
                argument = validators.get(directiveName).validateArgument(argument, directive, input);
                if (argument == null) {
                    break;
                }
            }
        }
        return argument;
    }

    @Override
    public ObjectField visitInputField(ObjectField field, GraphQLInputObjectField definition, ExecutionInput input) {
        List<GraphQLDirective> directives = definition.getDirectives();
        for (GraphQLDirective directive : directives) {
            String directiveName = directive.getName();
            if (validators.containsKey(directiveName)) {
                field = validators.get(directiveName).validateInputField(field, directive, input);
                if (field == null) {
                    break;
                }
            }
        }
        return field;
    }
}
