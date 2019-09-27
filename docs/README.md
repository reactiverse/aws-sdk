# Reactive AWS SDK v2

This project provides a `VertxNioAsyncHttpClient` and a `VertxExecutor` so that you can use AWS SDK v2 (async, non-blocking)
in a Vert.x application.

## Javadoc

The [Javadoc](./javadoc/index.html).

## Install

Using maven:
```
<dependency>
    <groupId>io.reactiverse</groupId>
    <artifactId>aws-sdk</artifactId>
    <version>0.0.3</version>
</dependency>
```

Using Gradle:
```
implementation("io.reactiverse:aws-sdk:0.0.3")
```

## How-to

Given `context` is a Vert.x `Context` object (either obtained by `vertx.getOrCreateContext()` or from
a `AbstractVerticle.init` method), you can use `VertxSdkClient::withVertx` utility method to create a client:

```java
DynamoDbAsyncClient dynamo = VertxSdkClient.withVertx( // use the provided utility method
    DynamoDbAsyncClient.builder() // with the traditional AwsAsyncClientBuilder you're used to
        .region(Region.EU_WEST_1) // that you'll confiugure as usual
    , context) // and provide a Vert.x context (the one from within your Verticle for example)
    .build(); // then build it => you'll have a Vert.x compatible AwsAsyncClient
```

Under the hood, it's gonna do the following:

```java
return builder
        .httpClient(new VertxNioAsyncHttpClient(context)) // uses Vert.x's HttpClient to make call to AWS services
        .asyncConfiguration(conf -> // tells AWS to execute response callbacks in a Vert.x context
                conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(context))
        );
```

* `VertxNioAsyncHttpClient` takes care of making HTTP calls through Vert.x's http client
* `VertxExecutor` provides Vert.x `Context` to `CompletableFuture`s. (see: Vert.x contract regarding contexts and callbacks)

You can then use your `DynamoDbAsyncClient` as you're used to with AWS SDK.
You can have a look at [integration tests](https://github.com/reactiverse/aws-sdk/blob/master/src/test/java/io/reactiverse/awssdk/integration/) to get many examples.


