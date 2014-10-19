FROM ubuntu:14.04

RUN apt-get update
RUN apt-get install -q -y git
RUN apt-get install -q -y openjdk-7-jre-headless
RUN apt-get -q -y -f install --no-install-recommends openjdk-7-jdk
RUN update-alternatives --config java
RUN apt-get install -q -y maven
RUN apt-get install -q -y unzip
RUN apt-get install -q -y vim
RUN apt-get install -q -y wget

ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64

RUN groupadd storm ; \
  useradd --gid storm --home-dir /home/storm --create-home --shell /bin/bash storm

RUN su storm -c 'cd ; git clone https://github.com/b/kafka-websocket.git'

USER storm
ENV HOME /home/storm
WORKDIR /home/storm
