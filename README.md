## Apigee Custom GraphQL Java Callout policy

This is an Apigee custom Java callout policy that allows you to parse and perform custom validation
on GraphQL queries and mutations against a GraphQL schema.

The built-in GraphQL policy in Apigee X has limited functionality. This policy is meant to be a replacement
for cases where you want to write your own validation rules based on schema directives.


## How it works

First, the policy takes a GraphQL query / mutation, and validates it against the given schema. Then, it
traverses the GraphQL query / mutation recursively visiting each input / output field.

Each time a field is visited, you have a chance to enforce custom validation logic. For example, you could
annotate each schema field with directives that specify the validation logic.

If the policy fails to validate a certain field in the input query / mutation, it outputs the flow variables:

```properties
graphq.failed=true
graphq.error.0.message="Error message from validation exception"
```

## Sample validation

As an example, I've provided a [**DirectivesValidator**](/src/main/java/com/google/apigee/callouts/DirectivesValidator.java)  
class along with a couple of individual validators for two directives (i.e. [@pattern](/src/main/java/com/google/apigee/callouts/PatternValidator.java), 
and [@visibility](/src/main/java/com/google/apigee/callouts/Visibility.java)).

### @pattern directive validator

The [PatternValidator](/src/main/java/com/google/apigee/callouts/PatternValidator.java) class requires that you define the following directive in your schema

```graphql
directive @pattern(
    regexp: String!
) on  INPUT_FIELD_DEFINITION
```

The idea is that you would annotate query / mutation input fields in your schema with the `@pattern` directive
in order guarantee that the given field matches the specified regular expression.

When a field fails to match against the regular expression, the validator throws an exception
which causes the policy to output a flow variable `graphql.failed=true`.

### @visibility directive validator

The  [VisibilityValidator](/src/main/java/com/google/apigee/callouts/PatternValidator.java) class requires that you define the following directive in your schema with the
`@visibility`

```graphql
directive @visibility(
    extent: String!
) on FIELD_DEFINITION
```


The idea is that you would annotate query / mutation output fields in your schema with the `@visibility` directive
in order to specify whether the field should be kept or removed from the query before passing it to the GraphQL backend.



## How to configure it

This policy takes two properties as inputs 

```xml
    <Properties>
        <Property name="message-ref">request</Property>
        <Property name="schema-base64-ref">propertyset.graphql.schema</Property>
    </Properties>
```

The **message-ref** property should point to the flow variable that contains the GraphQL query / mutation (in JSON format).
e.g.
```json
{
  "query": "...",
  "mutation": "..."
}
```

The **schema-base64-ref** property should point to a flow variable containing the GraphQL schema, (encoded as base64).


In the example above, the schema has been stored inside a property set. This is pretty convenient, as you can
store the schemas directly inside the API proxy as a resource, or even outside the API proxy as an external resource.

I have included a script [build-schema-file.sh](/build-schema-file.sh) that helps with generating
a properties file containing your `schema` base64 encoded. You can run this script against
your schema, and it will output a `graphql.properties` file that you can import as a resource
into your API Proxy.

```shell
./build-schema-file.sh /path/to/your/schema.graphql
```


## Sample Proxy Endpoint

Below is a sample API Proxy endpoint showing you to use the policy

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ProxyEndpoint name="default">
    <Description/>
    <FaultRules/>
    <PreFlow name="PreFlow">
        <Request>
            <Step>
                <Condition>request.verb = "POST" AND request.header.content-type = "application/json" </Condition>
                <Name>JC-GraphQL</Name>
            </Step>
            <Step>
                <Condition>graphql.failed = true</Condition>
                <Name>RF-GraphQLError</Name>
            </Step>
        </Request>
        <Response/>
    </PreFlow>
    <PostFlow name="PostFlow">
        <Request/>
        <Response/>
    </PostFlow>
    <Flows/>
    <HTTPProxyConnection>
        <BasePath>/resorts/graphql</BasePath>
        <Properties/>
        <VirtualHost>default</VirtualHost>
    </HTTPProxyConnection>
    <RouteRule name="default">
        <TargetEndpoint>default</TargetEndpoint>
    </RouteRule>
</ProxyEndpoint>
```

### JC-GraphQL Java Callout policy

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<JavaCallout continueOnError="false" enabled="true" name="JC-GraphQL">
    <DisplayName>JC-GraphQL</DisplayName>
    <Properties>
        <Property name="message-ref">request</Property>
        <Property name="schema-base64-ref">propertyset.graphql.schema</Property>
    </Properties>
    <ClassName>com.google.apigee.callouts.GraphQLCallout</ClassName>
    <ResourceURL>java://apigee-java-callout-graphql.jar</ResourceURL>
</JavaCallout>
```

### RF-GraphQLError Raise Fault policy
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<RaiseFault continueOnError="false" enabled="true" name="RF-GraphQLError">
    <DisplayName>RF-GraphQLError</DisplayName>
    <Properties/>
    <FaultResponse>
        <Set>
            <Headers/>
            <Payload contentType="application/json">
{
  "error": "{escapeJSON(graphql.error.0.message)}"
}
            </Payload>
            <StatusCode>400</StatusCode>
            <ReasonPhrase>BadRequest</ReasonPhrase>
        </Set>
    </FaultResponse>
    <IgnoreUnresolvedVariables>true</IgnoreUnresolvedVariables>
</RaiseFault>
```

## Latency 

The most time-consuming step is the parsing the GraphQL schema itself. It can take a few hundred milliseconds.
However, the policy caches the parsed schema as an AST in-memory. This makes it so that subsequent requests can
do the validation process much faster.



### Support
This is not an officially supported Google product