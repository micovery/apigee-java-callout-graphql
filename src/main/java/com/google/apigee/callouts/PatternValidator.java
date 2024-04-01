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
import com.google.apigee.callouts.graphql.GraphQLUtil;
import com.google.apigee.callouts.graphql.GraphQLValidationException;
import graphql.ExecutionInput;
import graphql.language.ObjectField;
import graphql.schema.GraphQLDirective;

public class PatternValidator implements GraphQLDirectiveValidator {

    @Override
    public ObjectField validateInputField(ObjectField field, GraphQLDirective directive, ExecutionInput input) throws GraphQLValidationException {
        String regexp = GraphQLUtil.getStringArgument(directive, "regexp");
        String value = GraphQLUtil.getStringValue(field);

        if (value == null) {
            throw new GraphQLValidationException("field " + field.getName() + " is required");
        }

        if (!value.matches(regexp)) {
            throw new GraphQLValidationException("field \"" + field.getName() + "\" with value \"" + value + "\" does not match pattern " + regexp);
        }

        return field;
    }


    public String getName() {
        return "pattern";
    }
}
