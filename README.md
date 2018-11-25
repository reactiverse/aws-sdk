## Use AWS SDK 2 with Vert.x

[![Build Status](https://travis-ci.org/aesteve/vertx-aws-sdk.svg?branch=master)](https://travis-ci.org/aesteve/vertx-aws-sdk)


This project provides a `VertxNioAsyncHttpClient` so that you can use AWS SDK v2 (async) in a Vert.x context.

### For the impatient 

Look at the tests for more info, but here's an example on how to use DynamoClient (local installation) with Vert.x:

```java
        DynamoDbAsyncClient dynamo = DynamoDbAsyncClient.builder()
                .httpClient(new VertxNioAsyncHttpClient(vertx))
                .asyncConfiguration(conf ->
                        conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(vertx.getOrCreateContext()))
                )
                .region(Region.EU_WEST_1)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(new URI("http://localhost:8000"))
                .build();

```

The 2 first configuration lines are important.
You have to use `VertxNioAsyncHttpClient` to execute SDK http requests and set the `FUTURE_COMPLETION_EXECUTOR` to the Vert.x one.


### Why using `VertxNioAsyncHttpClient`

As you know, Vert.x uses non-blocking IO. This means, among other stuff, that you should never ever block the event-loop.
AWS SDK v1 implementation relies on blocking IOs. This means you cannot use it together with Vert.x in a straightforward way. You would end up blocking the event-loop, hence killing your application's scalability.
The only option would be to wrap your synchronous calls to AWS SDK v1 within `executeBlocking` or use a worker thread.

Even though some methods of the AWS SDK are indicated as "async" (`DynamoAsyncClient` for instance), it internally uses a thread pool whose size is configurable.
Those threads are not managed by Vert.x.

So basically, you cannot really use AWS SDK v1 together with Vert.x in a true async, non-blocking way.

In november 2018, AWS has published the version 2 of its SDK. Embracing non-blocking IO model.
This could mean we are able to use the V2 together with Vert.x without the risk of blocking the event-loop, right ?

Well, yes, but there's another very important promise in Vert.x: "The callback is executed by the same thread (event-loop) that the one that registered it".
Using the (default, Netty-based) non-blocking version of the SDK in a Vert.x context could end up breaking this promise.
Let's imagine you're making a request to AWS S3 from a Vert.x context. By default, the http call would then go through Netty's async http client.
This clients uses its own Netty event-loop, out of Vert.x's context. Your callback would then be executing from this event-loop context, out of Vert.x one.
The promise would then be broken.

Using the `VertxNioAsyncHttpClient` takes care of that.
You'll still be targeting AWS S3's REST API, but this time, through Vert.x HTTP client which will take good care of dealing with the event-loops you configured in your Vert.x project, respecting the promise.

