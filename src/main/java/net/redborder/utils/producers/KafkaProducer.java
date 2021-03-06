package net.redborder.utils.producers;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Joiner;
import com.google.gson.Gson;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class KafkaProducer implements IProducer {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class);
    private final static MetricRegistry metrics = new MetricRegistry();

    private String brokersString;
    private String topic;
    private String partitionKey = "";
    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;
    private final Meter messages = metrics.meter("messages");

    // Connects to ZK, reads the broker data there, and builds
    // a string like the following: host1:9092,host2:9092.
    // After that, creates a kafka producer with that data.
    public KafkaProducer(String zkConnect, String topic, String partitionKey) {
        // Set the topic
        this.topic = topic;
        if(partitionKey != null) this.partitionKey = partitionKey;

        // Connect to ZooKeeper
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(zkConnect, retryPolicy);
        client.start();

        // Get the current kafka brokers and its IDs from ZK
        List<String> ids = Collections.emptyList();
        List<String> hosts = new ArrayList<>();

        try {
            ids = client.getChildren().forPath("/brokers/ids");
        } catch (Exception ex) {
            log.error("Couldn't get brokers ids", ex);
        }

        // Get the host and port from each of the brokers
        for (String id : ids) {
            String jsonString = null;

            try {
                jsonString = new String(client.getData().forPath("/brokers/ids/" + id), "UTF-8");
            } catch (Exception ex) {
                log.error("Couldn't parse brokers data", ex);
            }

            if (jsonString != null) {
                try {
                    Gson gson = new Gson();
                    Map json = gson.fromJson(jsonString, Map.class);
                    Double port = (Double) json.get("port");
                    String host = json.get("host") + ":" + port.intValue();
                    hosts.add(host);
                } catch (NullPointerException e) {
                    log.error("Failed converting a JSON tuple to a Map class", e);
                }
            }
        }

        // Close the zookeeper connection
        client.close();

        // Builds the brokers string
        brokersString = Joiner.on(',').join(hosts);

        // Set the kafka properties for the producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokersString);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "1000");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "500");
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "net.redborder.utils.producers.SimplePartitioner");

        // Create the producer
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);

        // Report the metrics of messages produced
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(5, TimeUnit.SECONDS);
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    @Override
    public void send(String message, String key) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);
        producer.send(record, new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
                if(exception == null)  {
                    messages.mark();
                }
            }
        });
    }
}
