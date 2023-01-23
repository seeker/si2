#!/bin/sh/

mkdir -p target/generated-sources/protobuf/
protoc --proto_path=src/main/resources/protobuf/ src/main/resources/protobuf/*.proto --java_out=target/generated-sources/protobuf/
