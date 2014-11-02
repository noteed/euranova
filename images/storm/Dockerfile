FROM ubuntu:14.04

RUN apt-get update
RUN apt-get install -q -y git
RUN apt-get install -q -y openjdk-7-jre-headless
RUN apt-get install -q -y python2.7
RUN apt-get install -q -y unzip
RUN apt-get install -q -y vim
RUN apt-get install -q -y wget

RUN ln -s /usr/bin/python2.7 /usr/bin/python

RUN groupadd storm ; \
  useradd --gid storm --home-dir /home/storm --create-home --shell /bin/bash storm

USER storm
ENV HOME /home/storm
WORKDIR /home/storm
RUN wget -q -O - \
  http://apache.cu.be/incubator/storm/apache-storm-0.9.2-incubating/apache-storm-0.9.2-incubating.tar.gz \
  | tar -xzf -
RUN mv apache-storm-0.9.2-incubating release
