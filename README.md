# Use AWS SDK 2 with Vert.x

[![Build Status](https://travis-ci.org/reactiverse/aws-sdk.svg?branch=master)](https://travis-ci.org/reactiverse/aws-sdk.svg?branch=master)


This project provides a `VertxNioAsyncHttpClient` and a `VertxExecutor` so that you can use AWS SDK v2 (async) in a Vert.x context.

## Version compatibility matrix

| Project | Vert.x | AWS sdk |
| ------- | ------ | ------- |
|  0.0.1  | 3.6.2  | 2.2.0   |

## For the impatient 

Look at the tests for more info, but here's an example on how to use DynamoClient (local installation) with Vert.x.
Given `context` is a Vert.x `Context` object (either obtained by `vertx.getOrCreateContext()` or from a `AbstractVerticle.init` method), you can use `withVertx` utility method to create a client:

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

## Why using this project?

### AWS SDK v1 => blocking IOs

As you know, Vert.x uses non-blocking IO. This means, among other stuff, that you should never ever block the event-loop.
AWS SDK v1 implementation relies on blocking IOs. This means you cannot use it together with Vert.x in a straightforward way. You would end up blocking the event-loop, hence killing your application's scalability.
The only option would be to wrap your synchronous calls to AWS SDK v1 within `executeBlocking` or use a worker thread.

Even though some methods of the AWS SDK are indicated as "async" (`DynamoAsyncClient` for instance), it internally uses a thread pool whose size is configurable.
Those threads are not managed by Vert.x.

So basically, you cannot really use AWS SDK v1 together with Vert.x in a true async, non-blocking way.

### Embrace AWS SDK v2

In november 2018, AWS has published the version 2 of its SDK. Embracing non-blocking IO model.
This could mean we are able to use the V2 together with Vert.x without the risk of blocking the event-loop, right?

Well, yes, but there's another very important promise in Vert.x: "The callback is executed by the same thread (event-loop) that the one that registered it".
Using the (default, Netty-based) non-blocking version of the AWS SDK in a Vert.x context could end up breaking this promise. Let's elaborate:

Let's imagine you're making a request to AWS S3 from a Vert.x context using `S3AsyncClient`. 
By default, the http call will go through Netty's async http client.
This clients uses its own Netty event-loop, out of Vert.x's context. Your call will indeed be asynchronous, and use non-blocking IOs. 
But your callback will be executed from this (Netty) event-loop context, out of Vert.x one.
The promise would then be broken.

Using this project takes care of that.

You'll still be targeting AWS S3's REST API, but this time:
* using Vert.x's HTTP client 
* taking good care that the `CompletableFuture<?>`'s callback will be executed in the same Vert.x context that the one that made the request

## Contributing

Tests placed under the `io.vertx.ext.awssdk.integration` package are using `localstack`: a huge set of utilities (docker images) emulating AWS Services (DynamoDB, Kinesis, S3, etc.).
In order to do so, they require a local docker daemon running on the machine.
They will download docker images from the docker hub, run the appropriate service as a docker container, then test the code against this local docker container. 

They'll only be executed if the system property `tests.integration` is set to `localstack`. They'll be ignored otherwise.
