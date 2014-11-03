#! /bin/bash

mkdir .storm
echo 'nimbus.host: "172.17.0.4"' > .storm/storm.yaml
./release/bin/storm jar \
  /source/topologies/target/stormcase-0.1.0-jar-with-dependencies.jar \
  euranova.SimpleTopology stormcase
