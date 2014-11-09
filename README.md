# Storm case

A case for Eura Nova's recruitment process using Apache Storm and the JVM.

The original case document is `storm-case-v02.odt` (the PDF version is saved
from LibreOffice).

This repository has its history rewritten so it doesn't contain the above
document (which were added in the first commit).

Case received on Monday 13th, October ~ 03:15 pm.

## Preliminary notes

Storm's master node is called Nimbus. Communicating with it is done using the
`storm` command-line client. Code and topologies are submitted to Nimbus in
`jar`s.

`storm` is configured through `~/.storm/storm.yaml`.

Maven is recommended to install Storm for developement purpose (otherwise, the
`storm` client can be simply unpacked from a zip file). `leiningen` is also
mentionned.

From the Storm tutorial, it seems that the "fields grouping" would be
appropriate to count model items.

Something like

```
java TopologyBuilder builder = new TopologyBuilder();

builder.setSpout(“items”, new ReadOffCashRegisterMessageBusSpout());
builder.setBolt(“count”, new ModelCount(), 8).fieldsGrouping(“items”, new Fields(“model”));
```

"count" and "ModelCount" should be histogram or something similar (i.e. count
over a time window). At first I thought that the Bolt could query its own clock
but since a message bus is used maybe it would be better to timestamp each
message at its source (the cash register, or possibly when it enters the
queue). It makes it possible to discard old messages easily. A possible problem
is to discard a message that arrives out-of-order, after the "last" message of
a time window, and miss it in the current count.

It seems we also have to keep track of static data (past aggregated data). They
would be used as initial data when loading the graphical view before new values
are pushed to it. This is especially true if the time window is relatively
large. Another possibility is to push those data repeatedly even though they
are not actually updated. That seems awkward for really old/infrequently
updated data.

It seems that for coarser granularity (e.g. one hour, one day and more), a time
series database could provide a persistent store. I don't know at which scale
Storm would make sense but not storing data points in a time series database.

Computing the best models can be done in its own bolt. The computation can
happen purely driven by messages from the rolling count bolt. Identical values
(for a given model) can be sent once.

## Questions

Cash registers send messages to a message bus. It seems a Storm spout must be
made to read the messages off of the bus. We have to choose a message bus (or
does Storm have something to offer here ?). Kafka seems a possibility with
existing Spouts. It also uses ZooKeeper, as does Storm.

It seems that a web page meets the "on the move" and foreseen personal computer
browsing requirement. The real-time requirement suggests something like Web
Sockets.

Is overcounting a problem ? If yes, see
https://storm.apache.org/documentation/Transactional-topologies.html
(which itself actually directs to
https://storm.apache.org/documentation/Trident-tutorial.html).

What does real-time mean ? How often the "last hour" window must be updated ?
Should the current hour (or current week, etc) be updated before its ending ?

Would it make sense to have a spout serving as a tick (instead of having a bolt
querying its own clock, possibly in a thread) ? (Or injecting messages with
zero items if not enough actual messages are produced. We should make sure to
hit all relevant bolts.) I guess it is conceptually clearer to send messages
when they are actually needed for the computation, and rely on an internal
timer if we want the bolt to send messages at a different pace. Storm supports
so-called Tick tuples, which are exactly what's written on the tin.

Mmm... From a cursory glance at this blog post[0], it seems the exact same
thing as the case...

[0]: http://www.michael-noll.com/blog/2013/01/18/implementing-real-time-trending-topics-in-storm/#excursus-tick-tuples-in-storm-08

## Docker images

`noteed/kafka-websocket` is simply used to build the WebSocket server (as a
jar) serving Kafka messages. The jar is needed to build
`noteed/websocket-server`.

### Storm

`images/storm-starter` is an in-progress Docker image to try
https://github.com/apache/storm/tree/master/examples/storm-starter.

Run the image with:

    > docker run -t -i \
        -v `pwd`/topologies/euranova:/home/storm/src/examples/storm-starter/src/jvm/euranova \
        noteed/storm-starter bash

This makes the local `topologies/euranova` directory available within the
container's Storm examples.

Within the container, compile the examples (and the local solutions, if
present):

    > cd src/examples/storm-starter
    > mvn compile

To run the Exclamation example:

    > mvn exec:java -Dstorm.topology=storm.starter.ExclamationTopology

To run the simple solution:

    > mvn exec:java -Dstorm.topology=euranova.SimpleTopology

To save a `jar` that can be submitted to a cluster:

    > mvn package
    > # target/storm-starter-0.9.2-incubating-jar-with-dependencies.jar can be saved

`images/storm` containes a binary release of Storm. In particular it provides
the `storm` executable to submit a topology to a Storm cluster.

To run the jar built above against a cluster (using the noteed/storm image):

    > mkdir .storm
    > echo 'nimbus.host: "172.17.0.4"' > .storm/storm.yaml
    > ./release/bin/storm jar \
        /source/storm-starter-0.9.2-incubating-jar-with-dependencies.jar \
        euranova.SimpleTopology stormcase

Note: the above IP address is the Nimbus container address.

## Running

Running the Storm case is done in two steps. The first step launch a set of
containers to run Storm and Kafka. The second stop submits the Storm case
topology to Storm.

The first step is done by the `launch.sh` script:

    > ./launch.sh

This will spawn a few Docker containers:

- a ZooKeeper container, used for Kafka and for Storm,
- a Kafka container (the script also creates the two topics used by the Storm
  case),
- a Storm Nimbus container (its IP address will be used in the second step),
- a Storm supervisor container,
- a Storm UI container, accessible on <container-ip>:8080,
- a Webscocket server,
- and a Nginx server, accessible directly from the host on port 80.

While the containers are spawned, the script outputs the container IDs and
associated IP addresses.

The second step is done by:

    > docker run -v `pwd`:/source noteed/storm /submit.sh 172.17.0.4

Note: `172.17.0.4` is an example Nimbus address, as displayed by `launch.sh`.

The kafka image can also be used to generate messages:

    > docker run --rm --link zookeeper:zk -i -t wurstmeister/kafka:0.8.1.1-1 bash

Then within the container:

    > $KAFKA_HOME/bin/kafka-console-producer.sh --topic=tickets --broker-list=172.17.0.3:9092

Or to consume the messages:

    > $KAFKA_HOME/bin/kafka-console-consumer.sh --topic=tickets --zookeeper=$ZK_PORT_2181_TCP_ADDR

Build the websocket server:

    > docker run -t -i noteed/kafka-websocket bash

Then within the container:

    > cd kafka-websocket
    > mvn compile
    > mvn package
    > # target/kafka-websocket-0.8.1-SNAPSHOT-shaded.jar can be saved.

Run the websocket server:

    > # In the producer and consumer `.properties` files,
    > # set zookeeper.connect=172.17.0.2:2181
    > # and metadata.broker.list=172.17.0.3:9092
    > java -jar target/kafka-websocket-0.8.1-SNAPSHOT-shaded.jar

Run the static HTTP server:

    > docker run -d -p 80:80 \
        -v `pwd`/static:/usr/share/nginx/www \
        -v `pwd`/sites-enabled:/etc/nginx/sites-enabled \
        noteed/nginx

## TODOs

- Use something like https://github.com/joewalnes/reconnecting-websocket.
- Make the websocket-server Docker image based on the JRE only.
- Add missing Bootstrap files.
