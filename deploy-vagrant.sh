#!/bin/sh

# Connect to dockerd in Vagrant
export DOCKER_HOST="tcp://127.0.0.1:2375"

# Build the Docker image with the node binary and push it to the registry
mvn clean package -Dmaven.test.skip=true
docker build -t docker-registry.service.consul:5000/si2-node:latest .
docker push docker-registry.service.consul:5000/si2-node:latest
