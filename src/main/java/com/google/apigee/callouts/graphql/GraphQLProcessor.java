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

import com.google.apigee.callouts.GraphQLCallout;
import com.google.apigee.callouts.util.Logger;
import graphql.*;
import graphql.language.*;
import graphql.parser.InvalidSyntaxException;
import graphql.schema.*;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.validation.ValidationError;
import org.javatuples.Pair;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

public class GraphQLProcessor {

    public static final String SCHEMA_INTROSPECTION = "__schema";
    public Logger logger;

    public GraphQLProcessor(Logger logger) {
        this.logger = logger;
    }

    public GraphQLProcessor() {
        this.logger = new Logger();
    }

    private static Map<Integer, GraphQLSchema> parsedSchemas = new HashMap<>();

    public ParseAndValidateResult parseAndValidate(String query, Map<String, Object> variables, GraphQLSchema schemaDoc, GraphQLVisitor visitor) {

        ExecutionInput queryInput = new ExecutionInput.Builder()
                .variables(variables)
                .query(query)
                .build();

        ParseAndValidateResult stage1 = ParseAndValidate.parseAndValidate(schemaDoc, queryInput);

        if (stage1.isFailure()) {
            InvalidSyntaxException syntaxException = stage1.getSyntaxException();
            List<ValidationError> errors =  new ArrayList<>();
            if (syntaxException != null) {
                errors.add(ValidationError.newValidationError().description(syntaxException.getMessage()).build());
            }
            errors.addAll(stage1.getValidationErrors());
            return ParseAndValidateResult.newResult().validationErrors(errors).build();
        }

        Document queryDoc = stage1.getDocument();

        try {
            Document newQueryDoc = queryDoc.transform((builder -> {
                List<Definition> definitions = queryDoc.getDefinitions();
                List<Definition> newDefinitions = new ArrayList<>();
                for (Definition definition : definitions) {
                    if (definition instanceof OperationDefinition) {
                        newDefinitions.add(processOperation((OperationDefinition) definition, schemaDoc, visitor, queryInput));
                    } else {
                        //Fragments ...
                        newDefinitions.add(definition);
                    }
                }
                builder.definitions(newDefinitions);
                builder.build();
            }));

            return ParseAndValidateResult.newResult().document(newQueryDoc).build();
        } catch(GraphQLValidationException ex) {
            List<ValidationError> errors = new ArrayList<>();
            errors.add(ValidationError.newValidationError().description(ex.getMessage()).build());
            return ParseAndValidateResult.newResult().validationErrors(errors).build();
        } catch (GraphQLException ex) {
            List<ValidationError> errors = new ArrayList<>();
            errors.add(ValidationError.newValidationError().description(ex.getMessage()).build());
            return ParseAndValidateResult.newResult().validationErrors(errors).build();
        }
    }

    private OperationDefinition processOperation(OperationDefinition operationDefinition, GraphQLSchema schemaDoc, GraphQLVisitor visitor, ExecutionInput input) {
        String operationName = operationDefinition.getOperation().name();
        if (operationName.equals("QUERY") || operationName.equals("MUTATION")) {
            operationName = operationName.substring(0, 1).toUpperCase() + operationName.substring(1).toLowerCase();
        }
        GraphQLObjectType objectType = schemaDoc.getObjectType(operationName);

        if (objectType == null) {
            throw new GraphQLException("Could not find type for operation " + operationName);
        }

        return operationDefinition.transform(builder -> {
            SelectionSet newSelectionSet = processSelectionSet(operationDefinition.getSelectionSet(), objectType, visitor, input);
            builder.selectionSet(newSelectionSet);
            builder.build();
        });
    }

    private Field processListField(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLList outputType, GraphQLVisitor visitor, ExecutionInput input) {
        GraphQLType wrappedType = outputType.getWrappedType();
        if (wrappedType instanceof GraphQLObjectType) {
            GraphQLObjectType objectType = (GraphQLObjectType) wrappedType;
            return processObjectField(field, fieldDefinition, objectType, visitor, input);
        }

        return field;
    }

    private SelectionSet processSelectionSet(SelectionSet selectionSet, GraphQLObjectType parentObjectType, GraphQLVisitor visitor, ExecutionInput input) {
        return selectionSet.transform(builder -> {
            List<Selection> selections = selectionSet.getSelections();
            List<Selection> newSelections = new ArrayList<>();
            for (Selection selection : selections) {
                Selection newSelection = processSelection(selection, parentObjectType, visitor, input);
                if (newSelection == null) {
                    continue;
                }
                newSelections.add(newSelection);
            }

            builder.selections(newSelections);
            builder.build();
        });
    }

    private Field processObjectField(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLObjectType objectType, GraphQLVisitor visitor, ExecutionInput input) {
        return field.transform(builder -> {
            List<Argument> newArguments = processArguments(field, fieldDefinition, visitor, input);
            SelectionSet newSelectionSet = processSelectionSet(field.getSelectionSet(), objectType, visitor, input);
            builder.arguments(newArguments);
            builder.selectionSet(newSelectionSet);
            builder.build();
        });
    }

    private List<Argument> processArguments(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLVisitor visitor, ExecutionInput input) {
        List<Argument> arguments = field.getArguments();
        List<Argument> newArguments = new ArrayList<>();
        for (Argument argument : arguments) {
            Argument newArgument = processArgument(argument, fieldDefinition, visitor, input);
            if (newArgument == null) {
                continue;
            }
            newArguments.add(newArgument);
        }

        return newArguments;
    }

    private Argument processArgument(Argument argument, GraphQLFieldDefinition parentFieldDefinition, GraphQLVisitor visitor, ExecutionInput input) {

        GraphQLArgument argumentDefinition = parentFieldDefinition.getArgument(argument.getName());
        if (argumentDefinition == null) {
            throw new GraphQLException("could not find argument type for " + argument.getName());
        }

        if (visitor != null) {
            argument = visitor.visitArgument(argument, argumentDefinition, input);
            if (argument == null) {
                return null;
            }
        }

        GraphQLInputType type = argumentDefinition.getType();
        if (type instanceof  GraphQLInputObjectType) {
            return processArgumentObject(argument, argumentDefinition, visitor, input);
        } else if(type instanceof  GraphQLList) {
            return processArgumentList(argument, argumentDefinition, visitor, input);
        }else {
            //Lists, Scalar, NonNull, Enum
            return argument;
        }
    }


    private Value processArgumentValue(Value value, GraphQLType valueType, InputValueDefinition definition, GraphQLVisitor visitor, ExecutionInput input) {
        if (value instanceof VariableReference) {
            //TODO: handle variable references
            return value;
        }

        if (valueType instanceof GraphQLInputObjectType) {
            if (!(value instanceof ObjectValue)) {
                throw new GraphQLValidationException("expected " +ObjectValue.class.getSimpleName()+ ", but found " + value.getClass().getSimpleName() + " for input field " + definition.getName());
            }
            return processArgumentObjectValue((ObjectValue) value, (GraphQLInputObjectType) valueType, definition,  visitor, input);
        } else if (valueType instanceof  GraphQLList) {
            if (!(value instanceof ArrayValue)) {
                throw new GraphQLValidationException("expected " + ArrayValue.class.getSimpleName() + ", but found " + value.getClass().getSimpleName() + " for input field " + definition.getName());
            }
            return processArgumentArrayValue((ArrayValue) value, (GraphQLList) valueType, definition,  visitor, input);
        } else {
            //Scalar, Enum
            return value;
        }
    }

    private Argument processArgumentList(Argument argument, GraphQLArgument argumentDefinition, GraphQLVisitor visitor, ExecutionInput input) {
        GraphQLInputType argType = argumentDefinition.getType();
        InputValueDefinition definition = argumentDefinition.getDefinition();

        return argument.transform(builder -> {
            Value value = argument.getValue();
            builder.value(processArgumentValue(value, argType, definition, visitor, input));
            builder.build();
        });
    }

    private Argument processArgumentObject(Argument argument, GraphQLArgument argumentDefinition, GraphQLVisitor visitor, ExecutionInput input) {
        GraphQLInputType argType = argumentDefinition.getType();
        InputValueDefinition definition = argumentDefinition.getDefinition();

        return argument.transform((builder) -> {
            Value value = argument.getValue();
            builder.value(processArgumentValue(value, argType, definition, visitor, input));

            builder.build();
        });
    }

    private ObjectValue processArgumentObjectValue(ObjectValue value, GraphQLInputObjectType argumentType,  InputValueDefinition definition, GraphQLVisitor visitor, ExecutionInput input) {
        return value.transform(builder -> {
            List<ObjectField> objectFields = value.getObjectFields();
            List<ObjectField> newObjectFields = new ArrayList<>();
            for (ObjectField field : objectFields) {
                ObjectField newField = processArgumentObjectField(field, argumentType, visitor, input);
                if (newField != null) {
                    newObjectFields.add(newField);
                }
            }

            builder.objectFields(newObjectFields);
            builder.build();
        });

    }

    private Value processArgumentArrayValue(ArrayValue value, GraphQLList valueType, InputValueDefinition definition, GraphQLVisitor visitor, ExecutionInput input) {

        return value.transform(builder -> {
            List<Value> values = value.getValues();
            List<Value> newValues = new ArrayList<>();
            for (Value curValue : values) {
                Value newCurValue = processArgumentValue(curValue, valueType.getWrappedType(), definition, visitor, input);
                if (newCurValue == null) {
                    continue;
                }
                newValues.add(newCurValue);
            }
            builder.values(newValues);
        });

    }


    private ObjectField processArgumentObjectField(ObjectField field, GraphQLInputObjectType parentType, GraphQLVisitor visitor, ExecutionInput input) {

        GraphQLInputObjectField fieldDefinition = parentType.getField(field.getName());
        if (fieldDefinition == null) {
            throw new GraphQLException("field " + field.getName() + " not found in type " + parentType.getName());
        }

        if (visitor != null) {
            field = visitor.visitInputField(field, fieldDefinition, input);
            if (field == null) {
                return null;
            }
        }

        ObjectField finalField = field;
        ;
        return field.transform(builder -> {
            Value newValue = processArgumentValue(finalField.getValue(),fieldDefinition.getType(), fieldDefinition.getDefinition(), visitor, input);
            if (newValue != null) {
                builder.value(newValue);
            }
            builder.build();
        });
    }

    private Selection processSelection(Selection selection, GraphQLObjectType parentObjectType, GraphQLVisitor visitor, ExecutionInput input) {
        if (selection instanceof Field) {
            Field field = (Field) selection;
            return processField(field, parentObjectType, visitor, input);
        }
        return selection;
    }

    private Field processField(Field field, GraphQLObjectType parentObjectType, GraphQLVisitor visitor, ExecutionInput input) {
        String fieldName = field.getName();
        if (fieldName.equals(SCHEMA_INTROSPECTION)) {
            //ignore introspection queries
            return field;
        }

        GraphQLFieldDefinition fieldDefinition = parentObjectType.getFieldDefinition(fieldName);
        if (fieldDefinition == null) {
            throw new GraphQLException("Could not find type for field " + fieldName);
        }

        if (visitor != null) {
            field = visitor.visitOutputField(field, fieldDefinition, input);
            if (field == null) {
                return null;
            }
        }


        GraphQLOutputType outputType = fieldDefinition.getType();
        if (outputType instanceof GraphQLList) {
            return processListField(field, fieldDefinition, (GraphQLList) outputType, visitor, input);
        } else if (outputType instanceof GraphQLObjectType) {
            return processObjectField(field, fieldDefinition, (GraphQLObjectType) outputType, visitor, input);
        } else if (outputType instanceof GraphQLScalarType) {
            return processScalarField(field, fieldDefinition, (GraphQLScalarType) outputType, visitor, input);
        } else if (outputType instanceof GraphQLUnionType) {
            return processUnionField(field, fieldDefinition, (GraphQLUnionType) outputType, visitor, input);
        } else {
            return processOtherField(field, fieldDefinition, outputType, visitor, input);
        }
    }


    private Field processOtherField(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLOutputType outputType, GraphQLVisitor visitor, ExecutionInput input) {
        return field;
    }

    private Field processUnionField(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLUnionType outputType, GraphQLVisitor visitor, ExecutionInput input) {
        return field;
    }

    private Field processScalarField(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLScalarType outputType, GraphQLVisitor visitor, ExecutionInput input) {
        return field;
    }



    public String loadResourceAsString(String resourcePath) throws IOException {
        URL url = GraphQLCallout.class.getResource(resourcePath);
        String data = new String(Files.readAllBytes(Paths.get(url.getPath())));
        return data;
    }

    public Pair<GraphQLSchema, List<ValidationError>> parseGraphQLSchema(String text) {
        Integer hash = text.hashCode();
        //if schema already parsed, re-use it
        if (parsedSchemas.containsKey(hash)) {
            logger.stdout.printf("%s\n", "re-using parsed schema");
            return new Pair<>(parsedSchemas.get(hash), null);
        }

        try {
            logger.stdout.printf("%s\n", "parsing schema");

            SchemaParser schemaParser = new SchemaParser();
            SchemaGenerator schemaGenerator = new SchemaGenerator();
            TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
            typeRegistry.merge(schemaParser.parse(text));

            GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, buildRuntimeWiring(typeRegistry));

            parsedSchemas.put(hash, graphQLSchema);
            return new Pair<>(graphQLSchema, null);
        }catch (SchemaProblem ex) {
            List<ValidationError> errors =  new ArrayList<>();
            for (GraphQLError error : ex.getErrors()) {
                errors.add(ValidationError.newValidationError()
                        .description(error.getMessage())
                        .build());
            }
            return new Pair<>(null, errors);
        }
    }

    public ParseAndValidateResult validateInputWithSchema(String inputText, Map<String, Object> variables, String schemaText, GraphQLVisitor visitor) {
        Pair<GraphQLSchema, List<ValidationError>> result = parseGraphQLSchema(schemaText);
        GraphQLSchema graphQLSchema = result.getValue0();
        List<ValidationError> errors = result.getValue1();
        if (errors != null) {
            return ParseAndValidateResult.newResult()
                    .validationErrors(errors)
                    .build();
        }

        return parseAndValidate(inputText, variables, graphQLSchema, visitor);
    }

    private RuntimeWiring buildRuntimeWiring(TypeDefinitionRegistry typeRegistry) {
        RuntimeWiring.Builder builder = newRuntimeWiring();
        //built-in scalars
        Set<String> nativeScalars = new HashSet<>() {{
            add("Int");
            add("Float");
            add("String");
            add("Boolean");
            add("ID");
        }};

        Map<String, ScalarTypeDefinition> schemaScalars = typeRegistry.scalars();

        for (String scalarName : schemaScalars.keySet()) {
            ScalarTypeDefinition scalar = schemaScalars.get(scalarName);
            if (nativeScalars.contains(scalarName)) {
                continue;
            }
            builder.scalar(GraphQLScalarType.newScalar().name(scalarName).coercing(new GraphQLAnyCoercing()).build());
        }

        return builder.build();
    }
}
