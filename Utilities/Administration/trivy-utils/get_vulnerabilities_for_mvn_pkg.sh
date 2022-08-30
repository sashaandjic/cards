#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

export MAVEN_ARTIFACT_GROUP_ID=$1
export MAVEN_ARTIFACT_ARTIFACT_ID=$2
export MAVEN_ARTIFACT_VERSION=$3

envsubst > pom.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.mycompany.app</groupId>
  <artifactId>my-app</artifactId>
  <version>1.0-SNAPSHOT</version>

  <dependencies>
    <dependency>
      <groupId>$MAVEN_ARTIFACT_GROUP_ID</groupId>
      <artifactId>$MAVEN_ARTIFACT_ARTIFACT_ID</artifactId>
      <version>$MAVEN_ARTIFACT_VERSION</version>
    </dependency>
  </dependencies>
</project>
EOF

docker run \
	--rm \
	--network none \
	-v $(realpath ~/trivy-cache):/root/.cache \
	-v $(realpath pom.xml):/my-app/pom.xml \
	aquasec/trivy fs \
	--security-checks vuln \
	--ignore-unfixed \
	--format json \
	/my-app

rm pom.xml
