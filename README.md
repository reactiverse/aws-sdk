## Use AWS SDK 2 with Vert.x

[![Build Status](https://travis-ci.org/aesteve/vertx-aws-sdk.svg?branch=master)](https://travis-ci.org/aesteve/vertx-aws-sdk)


This project provides a `VertxNioAsyncHttpClient` so that you can use with AWS SDK v2 (async) in a Vert.x context.

Look at the tests for more info, but here's an example on how to use DynamoClient (local installation) with Vert.x:

```java
DynamoDbAsyncClient dynamo = DynamoDbAsyncClient.builder()
        .httpClient(new VertxNioAsyncHttpClient(vertx))
        .region(Region.EU_WEST_1)
        .credentialsProvider(credentialsProvider)
        .endpointOverride(new URI("http://localhost:8000"))
        .build();
```
