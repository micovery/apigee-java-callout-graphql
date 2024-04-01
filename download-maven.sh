#!/bin/bash

# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


set -e
pushd ./tools
export MAVEN_VERSION="3.9.5"

echo "*** Downloading maven (version: ${MAVEN_VERSION})"
curl -o maven.zip -sfL "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.zip"

echo "*** Extracting maven.zip ***"
unzip maven.zip
rm -f maven.zip

mv "./apache-maven-${MAVEN_VERSION}" ./apache-maven

chmod a+x ./apache-maven/bin/*






