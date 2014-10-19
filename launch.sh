#! /bin/bash

# Run a bunch of Docker containers.

ZOOKEEPER_ID=$(docker run -d --name zookeeper jplock/zookeeper)
ZOOKEEPER_IP=$(docker inspect $ZOOKEEPER_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo zookeeper:
echo "  container: $ZOOKEEPER_ID"
echo "  address: $ZOOKEEPER_IP"

# TODO Remove hard-coded IP address.
KAFKA1_ID=$(docker run -d \
  --name kafka1 \
  --link zookeeper:zk \
  -e 'HOST_IP=172.17.42.1' \
  -e 'PORT=9092' \
  -e 'KAFKA_BROKER_ID=1' \
  -e 'KAFKA_ADVERTISED_PORT=9092' \
  -e 'KAFKA_ADVERTISED_HOST_NAME=172.17.0.3' \
  wurstmeister/kafka:0.8.1.1-1)
KAFKA1_IP=$(docker inspect $KAFKA1_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo kafka1:
echo "  container: $KAFKA1_ID"
echo "  address: $KAFKA1_IP"
