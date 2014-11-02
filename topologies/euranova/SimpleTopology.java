/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package euranova;

import backtype.storm.Config;
import backtype.storm.Constants;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

import java.lang.System;

import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import backtype.storm.spout.SchemeAsMultiScheme;
import storm.kafka.bolt.KafkaBolt;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;
import storm.kafka.ZkHosts;
import storm.kafka.StringScheme;

/**
 * This is a basic example of a Storm topology.
 */
public class SimpleTopology {

  public static class TestModelSpout extends BaseRichSpout {
    SpoutOutputCollector _collector;

    public TestModelSpout() {
      this(true);
    }

    public TestModelSpout(boolean isDistributed) {
    }

    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
      _collector = collector;
    }

    public void close() {
    }

    public void nextTuple() {
      Utils.sleep(100);
      final String[] models = new String[] {"nathan", "mike", "jackson", "golda", "bertels"};
      final Random rand = new Random();
      final String model = models[rand.nextInt(models.length)];
      final int count = rand.nextInt(10);
      _collector.emit(new Values(model, count));
    }

    public void ack(Object msgId) {
    }

    public void fail(Object msgId) {
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("model", "count"));
    }
  }

  /*
   * Combine this bolt with a KafkaSpout to extract a model and a count
   * (similar to TestModelSpout).
   */
  public static class ExtractModelCountBolt extends BaseRichBolt {
    OutputCollector _collector;

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
      _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
      final String s = tuple.getString(0);
      JSONParser parser = new JSONParser();
      try {
        JSONObject items = (JSONObject)parser.parse(s);
        Iterator<String> models = items.keySet().iterator();
        while (models.hasNext()) {
          String model = (String)models.next();
          int count = (int)(long)(Long)items.get(model);
          _collector.emit(tuple, new Values(model, count));
        }
      } catch(ParseException pe) {
        // TODO Other exceptions are possible.
        // TODO Log.
      }
      _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("model", "count"));
    }
  }

  public static class ModelCountBolt extends BaseBasicBolt {
    Map<String, Integer> counts = new HashMap<String, Integer>();

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      if (isTickTuple(tuple)) {
        // We emit the current sums.
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
          collector.emit(new Values(entry.getKey(), entry.getValue()));
        }

        // TODO Better to reset existing values ?
        counts = new HashMap<String, Integer>();
      } else {
        String model = tuple.getString(0);
        Integer count = counts.get(model);
        if (count == null)
          count = 0;
        count += tuple.getInteger(1);
        counts.put(model, count);
      }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("model", "count"));
    }

    // TODO This method in a common super class.
    private static boolean isTickTuple(Tuple tuple) {
      return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
        && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
  }

  public static class RollingModelCountBolt extends BaseBasicBolt {
    Map<String, Integer> counts = new HashMap<String, Integer>();
    static final int WINDOW_SIZE = 60; // In ticks
    Map<String, Deque<Integer>> ticks = new HashMap<String, Deque<Integer>>();

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      if (isTickTuple(tuple)) {
        // Transition to a new tick.

        // We emit the current sums.
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
          collector.emit(new Values(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, Deque<Integer>> entry : ticks.entrySet()) {
          entry.getValue().addLast(0);
          Integer f = entry.getValue().removeFirst();
          Integer c = counts.get(entry.getKey());
          c -= f;
          counts.put(entry.getKey(), c);

        // TODO Remove models whose count has reached zero.
        }
      } else {
        String model = tuple.getString(0);
        Integer tickCount = tuple.getInteger(1);

        Integer count = counts.get(model);
        Deque<Integer> fifo = ticks.get(model);

        if (count == null) {
          count = 0;
          counts.put(model, 0);
        }

        if (fifo == null) {
          fifo = new LinkedList<Integer>();
          for (int i=0 ; i<WINDOW_SIZE ; i++) {
            fifo.addFirst(0);
          }
          ticks.put(model, fifo);
        }

        Integer last = fifo.removeLast();
        last += tickCount;
        fifo.addLast(last);
        count = counts.get(model);
        count += tickCount;
        counts.put(model, count);
      }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("models", "count"));
    }

    // TODO This method in a common super class.
    private static boolean isTickTuple(Tuple tuple) {
      return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
        && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
  }

  // No tuple ?
  public static class Pair {
    String model;
    Integer count;

    public Pair(String model, Integer count) {
      super();
      this.model = model;
      this.count = count;
    }
  }

  public static class PairComparator implements Comparator<Pair> {
    @Override
    public int compare(Pair a, Pair b) {
      return b.count - a.count;
    }
  }

  public static class BestModelBolt extends BaseBasicBolt {
    LinkedList<Pair> counts = new LinkedList<Pair>();
    static final long N_BEST = 3; // How many best models should be reported.

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      if (isTickTuple(tuple)) {
        // Transition to a new tick.

        JSONArray list = new JSONArray();

        // We emit the best sums.
        for (Pair entry : counts) {
          JSONArray pair = new JSONArray();
          pair.add(entry.model);
          pair.add(entry.count);
          list.add(pair);
        }
        collector.emit(new Values(list.toJSONString()));
      } else {
        // Remove existing model if any, then add the new one, sort everything,
        // keep the n best ones.
        String model = tuple.getString(0);
        Integer count = tuple.getInteger(1);
        Iterator<Pair> it = counts.iterator();
        while (it.hasNext()) {
          Pair entry = it.next();
          if (entry.model.equals(model)) {
            it.remove();
            break;
          }
        }
        // TODO Remove models with a zero count.
        counts.add(new Pair(model, count));
        Collections.sort(counts, new PairComparator());
        if (counts.size() > N_BEST) {
          counts.removeLast();
        }
      }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("message")); // Matches KafkaBolt's expectation.
    }

    // TODO This method in a common super class.
    private static boolean isTickTuple(Tuple tuple) {
      return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
        && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
    }
  }

  public static void main(String[] args) throws Exception {
    SpoutConfig kafkaSpoutConf = new SpoutConfig(
      new ZkHosts("172.17.0.2:2181"), "tickets", "/kafka", "KafkaSpout");
    kafkaSpoutConf.scheme = new SchemeAsMultiScheme(new StringScheme());

    TopologyBuilder builder = new TopologyBuilder();

    builder.setSpout("from_kafka", new KafkaSpout(kafkaSpoutConf), 1);
    builder.setBolt("models", new ExtractModelCountBolt(), 2)
      .shuffleGrouping("from_kafka");
    builder.setBolt("sums", new ModelCountBolt(), 3)
      .addConfiguration(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, 1)
      .fieldsGrouping("models", new Fields("model"));
    builder.setBolt("rolling", new RollingModelCountBolt(), 3)
      .addConfiguration(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, 1)
      .fieldsGrouping("sums", new Fields("model"));
    builder.setBolt("best", new BestModelBolt())
      .addConfiguration(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, 1)
      .globalGrouping("rolling");
    builder.setBolt("to_kafka", new KafkaBolt())
      .globalGrouping("best");

    Config conf = new Config();
    conf.setDebug(true);

    // Configuration for the KafkaBolt.
    Properties props = new Properties();
    props.put("metadata.broker.list", "172.17.0.3:9092");
    props.put("request.required.acks", "1");
    props.put("serializer.class", "kafka.serializer.StringEncoder");
    conf.put(KafkaBolt.KAFKA_BROKER_PROPERTIES, props);
    conf.put(KafkaBolt.TOPIC, "best_models");

    if (args != null && args.length > 0) {
      conf.setNumWorkers(3);
      StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
    }
    else {
      LocalCluster cluster = new LocalCluster();
      cluster.submitTopology("test", conf, builder.createTopology());
      Utils.sleep(5 * 60 * 1000);
      cluster.killTopology("test");
      cluster.shutdown();
    }
  }
}
