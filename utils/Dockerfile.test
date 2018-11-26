# Dockerfile for running tests in a container (like Travis does)
# from the root of the project: `mv utils/Dockerfile.test Dockerfile` then `docker build .` then `docker run <id>`
FROM openjdk:9-jdk

WORKDIR /tmp

ADD src ./src
ADD gradle ./gradle
ADD build.gradle .
ADD settings.gradle .
ADD gradlew .

ENTRYPOINT ./gradlew test
