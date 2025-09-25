package com.zcq.kafkacodestudy;

import com.zcq.kafkacodestudy.producer.KafkaProducer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class KafkaTestRunner implements CommandLineRunner {

    private final KafkaProducer producer;

    public KafkaTestRunner(KafkaProducer producer) {
        this.producer = producer;
    }

    @Override
    public void run(String... args) {
        producer.sendMessage("test-topic", "Hello Kafka from Spring Boot!");
    }
}
