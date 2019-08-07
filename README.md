# Use AWS SDK v2 with Vert.x

[![Build Status](https://travis-ci.org/reactiverse/aws-sdk.svg?branch=master)](https://travis-ci.org/reactiverse/aws-sdk)

This project provides a `VertxNioAsyncHttpClient` and a `VertxExecutor` so that you can use AWS SDK v2 in a Vert.x context.

## Version compatibility matrix

| Project | Vert.x | AWS sdk |
| ------- | ------ | ------- |
|  0.0.1  | 3.8.0  | 2.7.8   |

## Documentation

See [this page](docs/README.md)

## Motivations

### AWS SDK v1 => blocking IOs

As you know, Vert.x uses non-blocking IO. This means, among other stuff, that you should never ever block the event-loop.
AWS SDK v1 implementation relies on blocking IOs. This means you cannot use it together with Vert.x in a straightforward
way. You would end up blocking the event-loop, hence killing your application's scalability. The only option would be
to wrap your synchronous calls to AWS SDK v1 within `executeBlocking` or use a worker thread.

Even though some methods of the AWS SDK are indicated as "async" (`DynamoAsyncClient` for instance), it internally uses
a thread pool whose size is configurable. Those threads can be a bottleneck in your application

You cannot really use AWS SDK v1 together with Vert.x in a non-blocking scalable way.

### Embrace AWS SDK v2

Since 2018, AWS has published the version 2 of its SDK, embracing non-blocking IO model.

Now you can use V2 together with Vert.x using this project.

* using Vert.x's HTTP client
* `CompletableFuture<?>`'s are executed in the same Vert.x context that the one that made the request

## Contributing

Tests placed under the `io.vertx.ext.awssdk.integration` package are using `localstack`: a huge set of
utilities (docker images) emulating AWS Services (DynamoDB, Kinesis, S3, etc.).

In order to do so, they require a local docker daemon running on the machine.

They will download docker images from the docker hub, run the appropriate service as a docker container, then test
the code against this local docker container.

They'll only be executed if the system property `tests.integration` is set to `localstack`. They'll be ignored otherwise.
