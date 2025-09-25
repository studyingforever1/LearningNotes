package com.zcq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class MQTest {
    private static final DefaultMQProducer producer = new DefaultMQProducer("DemoProducerGroup");
    private static final DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("DemoConsumerGroup");

    static {
        producer.setNamesrvAddr("10.1.24.136:9876");
        try {
            producer.start();
        } catch (MQClientException e) {
            throw new RuntimeException(e);
        }

        consumer.setNamesrvAddr("10.1.24.136:9876");
    }

    public static void main(String[] args) throws Exception {
//        sendMessage();
        sendMessageOneWay();
//        sendMessageAsync();
        ackMessage();

        String decode = URLDecoder.decode("https://oss-dataoper-repo.oss-cn-hangzhou.aliyuncs.com/sales/contract/997ED9E0C07049E4B667B68CF125F2E2.pdf?Expires=1758784463&OSSAccessKeyId=LTAI4Fo68Hmv3cCMPMWr6zKD&Signature=GxPpfVxvLvY2Nc7UIcR8%2FTtkdWw%3D&response-content-disposition=attachment%3Bfilename%3D%E6%97%A0%E5%90%8D%E7%A7%B0", StandardCharsets.UTF_8);
        System.out.println(decode);

    }

    private static void ackMessage() throws MQClientException {

        consumer.subscribe("TestTopic", "TagA");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            System.out.println(msgs);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        System.out.println("Consumer Started.");
    }

    private static void sendMessage() throws MQClientException, RemotingException, MQBrokerException, InterruptedException {

        // 创建消息
        Message msg = new Message("TestTopic", "TagA", "keys", 1, "Hello RocketMQ".getBytes(), false);

        // 发送消息
        SendResult sendResult = producer.send(msg);
        System.out.println("发送结果: " + sendResult);

        // 关闭生产者
        producer.shutdown();
    }

    private static void sendMessageAsync() throws Exception {
        for (int i = 0; i < 10; i++) {
            Message msg = new Message("TestTopic", "TagA", "keys", "Hello RocketMQ".getBytes());
            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                        System.out.println(sendResult);
                    } else {
                        System.out.println("发送失败" + sendResult);
                    }
                }

                @Override
                public void onException(Throwable e) {
                    System.out.println("发送异常" + e);
                }
            });
        }
        Thread.sleep(5000);
        producer.shutdown();
    }

    private static void sendMessageOneWay() throws Exception {
        for (int i = 0; i < 10; i++) {
            Message msg = new Message("TestTopic", "TagA", "keys", "Hello RocketMQ".getBytes());
            producer.sendOneway(msg);
            System.out.println("发送成功");
        }
    }
}
