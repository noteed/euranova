#! /bin/bash

# Run a bunch of Docker containers.

ZOOKEEPER_ID=$(docker run -d --name zookeeper jplock/zookeeper)
ZOOKEEPER_IP=$(docker inspect $ZOOKEEPER_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo zookeeper:
echo "  container: $ZOOKEEPER_ID"
echo "  address: $ZOOKEEPER_IP"

# TODO Remove hard-coded IP address.
# TODO I think the --name and --link are no longer needed.
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

# Wait for ZK.
sleep 5

# Create Kafka topics.

docker run --rm --link zookeeper:zk -i -t wurstmeister/kafka:0.8.1.1-1 \
  /opt/kafka_2.8.0-0.8.1.1/bin/kafka-topics.sh --create --topic tickets \
    --partitions 4 --zookeeper $ZOOKEEPER_IP --replication-factor 1

docker run --rm --link zookeeper:zk -i -t wurstmeister/kafka:0.8.1.1-1 \
  /opt/kafka_2.8.0-0.8.1.1/bin/kafka-topics.sh --create --topic best_models \
    --partitions 4 --zookeeper $ZOOKEEPER_IP --replication-factor 1

docker run --rm --link zookeeper:zk -i -t wurstmeister/kafka:0.8.1.1-1 \
  /opt/kafka_2.8.0-0.8.1.1/bin/kafka-topics.sh --describe --topic tickets \
    --zookeeper $ZOOKEEPER_IP

docker run --rm --link zookeeper:zk -i -t wurstmeister/kafka:0.8.1.1-1 \
  /opt/kafka_2.8.0-0.8.1.1/bin/kafka-topics.sh --describe --topic best_models \
    --zookeeper $ZOOKEEPER_IP

NIMBUS_ID=$(docker run -d \
  --name nimbus \
  --link zookeeper:zk \
  wurstmeister/storm-nimbus:0.9.2)
NIMBUS_IP=$(docker inspect $NIMBUS_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo nimbus:
echo "  container: $NIMBUS_ID"
echo "  address: $NIMBUS_IP"

SUPERVISOR_ID=$(docker run -d \
  --link nimbus:nimbus \
  --link zookeeper:zk \
  wurstmeister/storm-supervisor:0.9.2)
SUPERVISOR_IP=$(docker inspect $SUPERVISOR_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo supervisor:
echo "  container: $SUPERVISOR_ID"
echo "  address: $SUPERVISOR_IP"

UI_ID=$(docker run -d \
  --link nimbus:nimbus \
  --link zookeeper:zk \
  wurstmeister/storm-ui:0.9.2)
UI_IP=$(docker inspect $UI_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo ui:
echo "  container: $UI_ID"
echo "  address: $UI_IP"

WEBSOCKET_ID=$(docker run -d \
  -p 7080:7080 \
  noteed/websocket-server /home/storm/run.sh $ZOOKEEPER_IP $KAFKA1_IP)
WEBSOCKET_IP=$(docker inspect $WEBSOCKET_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo websocket:
echo "  container: $WEBSOCKET_ID"
echo "  address: $WEBSOCKET_IP"

NGINX_ID=$(docker run -d \
  -p 80:80 \
  -v `pwd`/static:/usr/share/nginx/www \
  -v `pwd`/sites-enabled:/etc/nginx/sites-enabled \
  noteed/nginx)
NGINX_IP=$(docker inspect $NGINX_ID | grep IPAddress | awk '{ print $2 }' | tr -d ',"')
echo nginx:
echo "  container: $NGINX_ID"
echo "  address: $NGINX_IP"
