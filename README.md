This is the code I have produced for EURA NOVA's recruitment process last year
(i.e. october 2014). This was fun as I learned about Storm. This was also a lot
of work since

- Storm or Kafka were new to me
- I haven't work with the JVM for a long time
- parts of Storm are in Clojure
- in addition to implementing the core elements (the Storm topology), they also
  asked for some real-time UI, that the result could be deployed on their own
  laptops easily, ...

I also felt it was quite abusive since in addition to the large amount of work,
the process was quite obscure and lack any concrete feedback.


# Storm case

A case for Eura Nova's recruitment process using Apache Storm and the JVM.

The original case document is `storm-case-v02.odt` (the PDF version is saved
from LibreOffice).

This repository has its history rewritten so it doesn't contain the above
document (which were added in the first commit).

Case received on Monday 13th, October ~ 03:15 pm.

This implementation of the case uses Kafka as a messaged queue and a
WebSocket-based web interface to display the top-selling models and to simulate
cash register tickets. The different parts of the project are built and
deployed using Docker images. One of the image, using Nginx, is not JVM-based
but is only used to serve two HTML pages.

A Makefile is provided to build the different images specific to this project.
Other images are available from Docker's public registry.

The topology is linear as follow:

    Spout  "from_kafka"
    Bolt "models"
    Bolt "sums"
    Bolt "rolling"
    Bolt "best_intermediate"
    Bolt "best"
    Bolt "to_kafka"

- "from_kafka" reads messages from Kafka,
- "models" extract models and counts information from the Spout tuple
- "sums" sums counts of the same models. It is not strictly necessary as the
  next bolt also performs the sums but is present as a mean to rate-limit
  messages at beginning of the topology.
- "rolling" performs sums of the same model over a sliding window.
- "best_intermediate" keeps the 10 best-selling models. It is similar to the
  next bolt and serves to lower the load on that next bolt.
- "best" does the same computation as "best_intermediate" but its parallelism
  must be set to one.
- "to_kafka" is the final bolt in the topology and writes its tuples to Kafka.

## Docker images

`images/storm-starter` is not necessary to build or run the project. It was
used at the beginning to create the solution which was moved out of the
storm-starter example directory later.

`images/kafka-websocket` is simply used to build the WebSocket server (as a
jar) serving Kafka messages. The jar is needed to build
`images/websocket-server`.

`images/websocket-server` containes the above jar. It connects to Kafka to send
the simulated cash register messages and also to receive the best-selling
models.

`images/maven` is a Docker image with the JDK and Maven to build the Storm
topolgy.

Run the image with:

    > docker run -t -i \
        -v `pwd`/topologies:/home/storm/topologies \
        noteed/maven bash

This makes the local `topologies` directory available within the home
directory.

Within the container, compile the solution with:

    > cd topologies
    > mvn compile

To run the solution:

    > mvn exec:java -Dstorm.topology=com.noteed.stormcase.Topology \
        -Dexec.args="172.17.0.2 172.17.0.3"

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
        com.noteed.stormcase.Topology 172.17.0.2 172.17.0.3 stormcase

Note: the above IP addresses are the Nimbus, ZooKeeper, and Kafka container
addresses.

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

    > docker run -v `pwd`:/source noteed/storm /submit.sh <nimbus> <zk> <kafka> 

Note: `<nimbus>` is Nimbus address, `<zk>` is ZooKeeper address, and `<kafka>`
is Kafka address, as displayed by `launch.sh`.

The kafka image can also be used to generate messages:

    > docker run --rm --link zookeeper:zk -i -t wurstmeister/kafka:0.8.1.1-1 bash

Then within the container:

    > $KAFKA_HOME/bin/kafka-console-producer.sh --topic=tickets --broker-list=172.17.0.3:9092

Or to consume the messages:

    > $KAFKA_HOME/bin/kafka-console-consumer.sh --topic=tickets --zookeeper=$ZK_PORT_2181_TCP_ADDR

## TODOs

- Use something like https://github.com/joewalnes/reconnecting-websocket.
- Make the websocket-server Docker image based on the JRE only.
- Add missing Bootstrap files.
