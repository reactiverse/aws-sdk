#!/usr/bin/env bash
PROJECT_VERSION=$(./gradlew properties -q | grep "version:" | awk '{print $2}')
if [[ "$PROJECT_VERSION" =~ .*SNAPSHOT ]] && [[ "${TRAVIS_BRANCH}" =~ ^master$|^[0-9]+\.[0-9]+$ ]] && [[ "${TRAVIS_PULL_REQUEST}" = "false" ]];
then
  ./gradlew assemble publish --no-daemon -PossrhUsername=${SONATYPE_NEXUS_USERNAME} -PossrhPassword=${SONATYPE_NEXUS_PASSWORD}
fi
