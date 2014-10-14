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

Somthing like

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

## Docker images

`images/storm-starter` is an in-progress Docker image to try
https://github.com/apache/storm/tree/master/examples/storm-starter.

Run the image with:

    > docker run -t -i noteed/storm-starter bash

Within the container, compile the examples

    > cd src/examples/storm-starter
    > mvn compile

To run the Exclamation example:

    > mvn exec:java -Dstorm.topology=storm.starter.ExclamationTopology
