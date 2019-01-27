#!/usr/bin/env bash

if [[ ! -v OSS_RH_USERNAME ]] || [[ ! -v OSS_RH_PASSWORD ]];
then
  echo "No deployment keys are defined, stopping here."
  exit 0
fi

PROJECT_VERSION=$(./gradlew properties -q | grep "version:" | awk '{print $2}')

if [[ "$PROJECT_VERSION" =~ .*SNAPSHOT ]] && [[ "${TRAVIS_BRANCH}" =~ ^master$|^[0-9]+\.[0-9]+$ ]] && [[ "${TRAVIS_PULL_REQUEST}" = "false" ]];
then
  echo "Deploying..."
  ./gradlew assemble publish --no-daemon -PossrhUsername=${OSS_RH_USERNAME} -PossrhPassword=${OSS_RH_PASSWORD}
else
  echo "Not deploying."
fi
