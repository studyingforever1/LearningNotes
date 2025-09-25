package com.zcq.kafkacodestudy;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class KafkacodestudyApplicationTests {

    @Resource
    KafkaTestRunner kafkaTestRunner;

    @Test
    void contextLoads() {
        kafkaTestRunner.run();
    }

}
