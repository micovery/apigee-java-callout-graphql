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

import graphql.ExecutionInput;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.ObjectField;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLType;

public interface GraphQLVisitor {
    Field visitOutputField(Field field, GraphQLFieldDefinition definition, ExecutionInput input);
    ObjectField visitInputField(ObjectField field, GraphQLInputObjectField definition, ExecutionInput input);

    Argument visitArgument(Argument argument, GraphQLArgument definition, ExecutionInput input);

}
