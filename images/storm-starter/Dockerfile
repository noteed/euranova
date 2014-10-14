FROM ubuntu:14.04

# Inspired by https://github.com/wurstmeister/storm-docker.

RUN apt-get update
RUN apt-get install -q -y git
RUN apt-get install -q -y openjdk-7-jre-headless
RUN apt-get -q -y -f install --no-install-recommends openjdk-7-jdk
RUN update-alternatives --config java
RUN apt-get install -q -y maven
RUN apt-get install -q -y unzip
RUN apt-get install -q -y vim
RUN apt-get install -q -y wget

# Storm development release
# RUN wget -q -O - \
#  http://people.apache.org/~ptgoetz/apache-storm-0.9.3-rc1/apache-storm-0.9.3-rc1.tar.gz \
#  | tar -xzf - -C /opt

ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64
#ENV STORM_HOME /opt/apache-storm-0.9.3-rc1

RUN groupadd storm ; \
  useradd --gid storm --home-dir /home/storm --create-home --shell /bin/bash storm

#  mkdir /var/log/storm ; \
#  chown -R storm:storm $STORM_HOME ; \
#  chown -R storm:storm /var/log/storm

USER storm
ENV HOME /home/storm
WORKDIR /home/storm
RUN wget -q -O - \
  http://apache.cu.be/incubator/storm/apache-storm-0.9.2-incubating/apache-storm-0.9.2-incubating-src.tar.gz \
  | tar -xzf -
RUN mv apache-storm-0.9.2-incubating src
