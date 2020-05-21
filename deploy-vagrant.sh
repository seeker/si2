#!/bin/sh

# Build the Docker image with the node binary and push it to the registry
mvn clean package -Dmaven.test.skip=true
docker build -t 10.0.2.15:5000/si2-node:latest .
docker push 10.0.2.15:5000/si2-node:latest
