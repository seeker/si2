FROM ubuntu:20.04
LABEL maintainer="github.com/seeker/si2"

RUN apt-get update && \
    apt-get install -y openjdk-11-jre-headless
RUN apt-get install -y \
apt-transport-https \
ca-certificates \
curl \
gnupg-agent \
software-properties-common

RUN curl -sL 'https://getenvoy.io/gpg' | apt-key add -
RUN add-apt-repository \
"deb [arch=amd64] https://dl.bintray.com/tetrate/getenvoy-deb \
$(lsb_release -cs) \
stable"
RUN apt-get update && apt-get install -y getenvoy-envoy

COPY node/target/node-* /node.jar
ENTRYPOINT ["java","-jar","node.jar"]
