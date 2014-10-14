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
import java.util.Random;

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

  public static class ExclamationBolt extends BaseRichBolt {
    OutputCollector _collector;

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
      _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
      final String marks = new String(new char[tuple.getInteger(1)]).replace("\0", "!");
      _collector.emit(tuple, new Values(tuple.getString(0) + marks));
      _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("model"));
    }
  }

  public static class ModelCountBolt extends BaseBasicBolt {
    Map<String, Integer> counts = new HashMap<String, Integer>();
    long t0 = 0; // Beginning of current tick (frame ?)
    static final long TICK_SIZE = 1000; // In milliseconds

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      if (t0 == 0)
        t0 = System.currentTimeMillis();
      long t1 = System.currentTimeMillis();

      if (t1 - t0 > TICK_SIZE) {
        // Transition to a new tick.

        // We emit the current sums. This assumes that this execute() method
        // is called frequently to emit the sums in a timely manner.
        // To ensure this is the case, the spout can emit additional messages
        // or a thread could be added to this bolt.
        // Would it be better for each sum to have its own t0 (i.e. its own
        // tick window), instead of emitting all the sums at once ?
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
          collector.emit(new Values(entry.getKey(), entry.getValue()));
        }

        // TODO Better to reset existing values ?
        counts = new HashMap<String, Integer>();

        // Advance to the beginning of the new tick. If execute() is called
        // frequently as suggested above, this is a single iteration.
        while (t1 - t0 > TICK_SIZE) {
          t0 += TICK_SIZE;
        }
      }

      String model = tuple.getString(0);
      Integer count = counts.get(model);
      if (count == null)
        count = 0;
      count += tuple.getInteger(1);
      counts.put(model, count);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("models", "count"));
    }
  }

  public static class RollingModelCountBolt extends BaseBasicBolt {
    Map<String, Integer> counts = new HashMap<String, Integer>();
    long t0 = 0; // Beginning of current tick (frame ?)
    static final long TICK_SIZE = 1000; // In milliseconds
    static final int WINDOW_SIZE = 5; // In ticks
    Map<String, Deque<Integer>> ticks = new HashMap<String, Deque<Integer>>();

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      if (t0 == 0)
        t0 = System.currentTimeMillis();
      long t1 = System.currentTimeMillis();

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

      if (t1 - t0 > TICK_SIZE) {
        // Transition to a new tick.

        // We emit the current sums. This assumes that this execute() method
        // is called frequently to emit the sums in a timely manner.
        // To ensure this is the case, the spout can emit additional messages
        // or a thread could be added to this bolt.
        // Would it be better for each sum to have its own t0 (i.e. its own
        // tick window), instead of emitting all the sums at once ?
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
          collector.emit(new Values(entry.getKey(), entry.getValue()));
        }

        // Advance to the beginning of the new tick. If execute() is called
        // frequently as suggested above, this is a single iteration.
        while (t1 - t0 > TICK_SIZE) {
          t0 += TICK_SIZE;
          for (Map.Entry<String, Deque<Integer>> entry : ticks.entrySet()) {
            entry.getValue().addLast(0);
            Integer f = entry.getValue().removeFirst();
            Integer c = counts.get(entry.getKey());
            c -= f;
            counts.put(entry.getKey(), c);
          }
        }
      }

      Integer last = fifo.removeLast();
      last += tickCount;
      fifo.addLast(last);
      count = counts.get(model);
      count += tickCount;
      counts.put(model, count);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("models", "count"));
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
    long t0 = 0; // Beginning of current tick (frame ?)
    static final long TICK_SIZE = 1000; // In milliseconds
    static final long N_BEST = 3; // How many best models should be reported.
    static int generation = 0; // Simple way to group messages together.
                               // TODO See to use a single (bigger) message.

    @Override
    public void execute(Tuple tuple, BasicOutputCollector collector) {
      if (t0 == 0)
        t0 = System.currentTimeMillis();
      long t1 = System.currentTimeMillis();

      if (t1 - t0 > TICK_SIZE) {
        // Transition to a new tick.

        generation += 1;

        // We emit the best sums.
        for (Pair entry : counts) {
          collector.emit(new Values(entry.model, entry.count, generation));
        }

        while (t1 - t0 > TICK_SIZE) {
          t0 += TICK_SIZE;
        }
      }

      // Remove existing model if any, then add the new one, sort everything,
      // keep the n best ones.
      String model = tuple.getString(0);
      Integer count = tuple.getInteger(1);
      Iterator<Pair> it = counts.iterator();
      while (it.hasNext()) {
        Pair entry = it.next();
        if (entry.model == model) {
          it.remove();
          break;
        }
      }
      counts.add(new Pair(model, count));
      if (counts.size() > N_BEST) {
        Collections.sort(counts, new PairComparator());
        counts.removeLast();
      }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("models", "count", "generation"));
    }
  }

  public static void main(String[] args) throws Exception {
    TopologyBuilder builder = new TopologyBuilder();

    builder.setSpout("models", new TestModelSpout(), 10);
    builder.setBolt("exclamations", new ExclamationBolt(), 3)
      .fieldsGrouping("models", new Fields("model"));
    builder.setBolt("sums", new ModelCountBolt(), 3)
      .fieldsGrouping("models", new Fields("model"));
    builder.setBolt("rolling", new RollingModelCountBolt(), 3)
      .fieldsGrouping("models", new Fields("model"));
    builder.setBolt("best", new BestModelBolt(), 1)
      .globalGrouping("rolling");

    Config conf = new Config();
    conf.setDebug(true);

    if (args != null && args.length > 0) {
      conf.setNumWorkers(3);

      StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
    }
    else {

      LocalCluster cluster = new LocalCluster();
      cluster.submitTopology("test", conf, builder.createTopology());
      Utils.sleep(10000);
      cluster.killTopology("test");
      cluster.shutdown();
    }
  }
}
