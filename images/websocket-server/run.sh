#! /bin/bash

ZOOKEEPER_CONNECT=$1
BROKER_LIST=$2

sed -i -e "s/zookeeper.connect=.*/zookeeper.connect=$ZOOKEEPER_CONNECT:2181/" conf/consumer.properties
sed -i -e "s/metadata.broker.list=.*/metadata.broker.list=$BROKER_LIST:9092/" conf/producer.properties
java -jar kafka-websocket-0.8.1-SNAPSHOT-shaded.jar
