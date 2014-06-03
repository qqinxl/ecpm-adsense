package jp.co.sshin.storm.demo;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

/**
 * @author s.shin
 */
public final class WordCounterTopologyMain {

    /**  */
    private static final int SLEEP_TIME = 1000;
    /**  */
    private static final String TOPOLOGY_NAME = "WordCounterTopology";

    /** */
    private WordCounterTopologyMain() { }

    /**
     * @param args args
     * @throws InterruptedException InterruptedException
     */
    public static void main(final String[] args) throws InterruptedException {
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("word-reader", new WordReader());
        builder.setBolt("word-normalizer", new WordNormalizer()).shuffleGrouping("word-reader");
        builder.setBolt("word-counter", new WordCounter(), 1).fieldsGrouping("word-normalizer", new Fields("word"));

        Config conf = new Config();
        conf.setDebug(true);
        conf.setNumWorkers(2);
        conf.put("wordsFile", "/root/workspace1/com.jd.storm.demo/src/main/resources/words.txt");
        conf.setDebug(true);
        conf.put(Config.TOPOLOGY_MAX_SPOUT_PENDING, 1);

        try {
            StormSubmitter.submitTopology(TOPOLOGY_NAME, conf, builder.createTopology());
        } catch (AlreadyAliveException e) {
            e.printStackTrace();
        } catch (InvalidTopologyException e) {
            e.printStackTrace();
        }
    }

    /**
     * @author s.shin
     */
    private static class WordReader extends BaseRichSpout {

        /**  */
        private static final long serialVersionUID = 1L;

        /**  */
        private transient SpoutOutputCollector collector;
        /**  */
        private transient FileReader fileReader;
        /**  */
        private boolean completed = false;

        //初期化処理
        @SuppressWarnings("unchecked")
        @Override
        public void open(final Map conf, final TopologyContext context, final SpoutOutputCollector collector) {
            String filePath   = conf.get("wordsFile").toString();
            try {
                this.fileReader = new FileReader(filePath);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Error reading file [" + conf.get("wordFile") + "]");
            }

            this.collector = collector;
        }

        //Tuple転送処理。無限Loop
        @Override
        public void nextTuple() {
            if (completed) {
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return;
            }

            String str;
            BufferedReader reader = new BufferedReader(fileReader);
            try {
                while ((str = reader.readLine()) != null) {
                    System.out.println("WordReader:Read a line data from file：" + str);
                    this.collector.emit(new Values(str), str);
                    System.out.println("WordReader:Send a line data to Tuple：" + str);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reading tuple", e);
            } finally {
                completed = true;
            }
        }

        @Override
        public void declareOutputFields(final OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("line"));
        }

        @Override
        public void ack(final Object msgId) {
            super.ack(msgId);
            System.out.println("OK : " + msgId);
        }

        @Override
        public void fail(final Object msgId) {
            super.fail(msgId);
            System.out.println("NG : " + msgId);
        }

        @Override
        public void close() {
            super.close();
            try {
                this.fileReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * @author s.shin
     */
    private static class WordNormalizer extends BaseBasicBolt {

        /**  */
        private static final long serialVersionUID = 1L;

        @Override
        public void execute(final Tuple input, final BasicOutputCollector collector) {
            String sentence = input.getString(0);
            if (sentence == null) {
                return;
            }
            String[] words = sentence.split(" ");
            System.out.println("WordNormalizer:Read a line data from Tuple "
                                + input.getSourceComponent() + " : " + sentence);

            for (String word : words) {
                word = word.trim().toLowerCase();
                if (!word.isEmpty()) {
                    collector.emit(new Values(word));
                    System.out.println("WordNormalizer:Read:Send a word data to Tuple：" + word);
                }
            }
        }

        @Override
        public void declareOutputFields(final OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("word"));
        }

        @Override
        public void cleanup() {
            super.cleanup();
            System.out.println("WordNormalizer:Deal with a line data and send words to Tuple over.");
        }

    }

    /**
     * @author s.shin
     */
    private static class WordCounter extends BaseBasicBolt {

        /**  */
        private static final long serialVersionUID = 1L;
        /**  */
        private transient TopologyContext context;
        /**  */
        private final Map<String, Integer> counters = new HashMap<String, Integer>();

        @SuppressWarnings("unchecked")
        @Override
        public void prepare(final Map stormConf, final TopologyContext context) {
            super.prepare(stormConf, context);
            this.context = context;
        }

        @Override
        public void execute(final Tuple input, final BasicOutputCollector collector) {
            String word = input.getString(0);
            System.out.println("WordCounter:Read a word from Tuple： "
                                + input.getSourceComponent() + " : " + word);
            Integer count = counters.get(word);
            counters.put(word, (count == null) ? 1 : count + 1);
            collector.emit(new Values(word, count));
        }

        @Override
        public void declareOutputFields(final OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("word", "count"));
        }

        @Override
        public void cleanup() {
            System.out.println("-- Word Counter ["
                                + context.getStormId() + " - "
                                + context.getThisComponentId() + " - "
                                + context.getThisTaskId() + " - "
                                + context.getThisWorkerPort() + " - "
                                + context.getThisTaskIndex() + " - "
                                + context.getCodeDir() +  "] --");

            System.out.println(counters);
        }

    }

}

