all: .image_storm_starter_touched .image_kafka_websocket_touched .image_websocket_server_touched .image_storm_touched

.image_storm_starter_touched: images/storm-starter/Dockerfile
	docker build -t noteed/storm-starter images/storm-starter
	touch $@

.image_kafka_websocket_touched: images/kafka-websocket/Dockerfile
	docker build -t noteed/kafka-websocket images/kafka-websocket
	touch $@

.image_websocket_server_touched: images/websocket-server/Dockerfile images/websocket-server/run.sh
	docker build -t noteed/websocket-server images/websocket-server
	touch $@

.image_storm_touched: images/storm/Dockerfile
	docker build -t noteed/storm images/storm
	touch $@
