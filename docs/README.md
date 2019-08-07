# Reactive AWS SDK v2

This project provides a `VertxNioAsyncHttpClient` and a `VertxExecutor` so that you can use AWS SDK v2 (async)
in a Vert.x application.

Given `context` is a Vert.x `Context` object (either obtained by `vertx.getOrCreateContext()` or from
a `AbstractVerticle.init` method), you can use `withVertx` utility method to create a client:

```java
final DynamoDbAsyncClient dynamo = VertxSdkClient.withVertx( // use the provided utility method
        DynamoDbAsyncClient.builder() // with the traditional AwsAsyncClientBuilder you're used to
            .region(Region.EU_WEST_1) // that you'll confiugure as usual
        , context) // and provide a Vert.x context (the one from within your Verticle for example)
        .build(); // then build it => you'll have a Vert.x compatible AwsAsyncClient
```

Under the hood, it's gonna attach the following:

```java
return builder
        .httpClient(new VertxNioAsyncHttpClient(context)) // uses Vert.x's HttpClient to make call to AWS services
        .asyncConfiguration(conf -> // tells AWS to execute response callbacks in a Vert.x context
                conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(context))
        );
```

