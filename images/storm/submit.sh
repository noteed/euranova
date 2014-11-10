#! /bin/bash

NIMBUS_HOST=$1
ZK_HOST=$2
KAFKA_BROKER=$3

mkdir .storm
echo "nimbus.host: \"$NIMBUS_HOST\"" > .storm/storm.yaml
./release/bin/storm jar \
  /source/topologies/target/stormcase-0.1.0-jar-with-dependencies.jar \
  com.noteed.stormcase.Topology $ZK_HOST $KAFKA_BROKER stormcase
