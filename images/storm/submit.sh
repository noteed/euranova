#! /bin/bash

NIMBUS_HOST=$1

mkdir .storm
echo "nimbus.host: \"$NIMBUS_HOST\"" > .storm/storm.yaml
./release/bin/storm jar \
  /source/topologies/target/stormcase-0.1.0-jar-with-dependencies.jar \
  euranova.SimpleTopology stormcase
