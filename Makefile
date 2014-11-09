all: .image_storm_starter_touched .image_kafka_websocket_touched .image_websocket_server_touched .image_maven_touched .image_storm_touched topologies/target/stormcase-0.1.0-jar-with-dependencies.jar

.image_storm_starter_touched: images/storm-starter/Dockerfile
	docker build -t noteed/storm-starter images/storm-starter
	touch $@

.image_kafka_websocket_touched: images/kafka-websocket/Dockerfile
	docker build -t noteed/kafka-websocket images/kafka-websocket
	touch $@

images/websocket-server/kafka-websocket-0.8.1-SNAPSHOT-shaded.jar: .image_kafka_websocket_touched
	docker run -v `pwd`/images/websocket-server:/source noteed/kafka-websocket cp /home/storm/kafka-websocket/target/kafka-websocket-0.8.1-SNAPSHOT-shaded.jar /source/

.image_websocket_server_touched: images/websocket-server/Dockerfile images/websocket-server/run.sh images/websocket-server/kafka-websocket-0.8.1-SNAPSHOT-shaded.jar
	docker build -t noteed/websocket-server images/websocket-server
	touch $@

.image_maven_touched: images/maven/Dockerfile
	docker build -t noteed/maven images/maven
	touch $@

.image_storm_touched: images/storm/Dockerfile images/storm/submit.sh
	docker build -t noteed/storm images/storm
	touch $@

topologies/target/classes/euranova/SimpleTopology.class: topologies/euranova/SimpleTopology.java
	mkdir -p m2
	docker run \
          -v `pwd`/m2:/home/storm/.m2 \
          -v `pwd`/topologies:/home/storm/topologies \
          noteed/maven sh -c 'cd topologies ; mvn compile'

topologies/target/stormcase-0.1.0-jar-with-dependencies.jar:
	docker run \
          -v `pwd`/m2:/home/storm/.m2 \
          -v `pwd`/topologies:/home/storm/topologies \
          noteed/maven sh -c 'cd topologies ; mvn package'

.PHONY: clean

clean:
	rm -rf topologies/target
	rm -rf m2
