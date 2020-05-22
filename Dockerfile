FROM ubuntu:20.04
LABEL maintainer="github.com/seeker/si2"

RUN apt-get update && \
    apt-get install -y openjdk-11-jre-headless

COPY node/target/node-* /node.jar
ENTRYPOINT ["java","-jar","node.jar"]
