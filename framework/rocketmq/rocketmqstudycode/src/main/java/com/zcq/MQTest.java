package com.zcq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.util.ArrayList;
import java.util.List;

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
//        sendMessageOneWay();
//        sendMessageAsync();
//        sendOrderMessage();
//        sendDelayedMessage();
        sendBatchMessage();
        ackMessage();

        producer.shutdown();
    }

    private static void ackMessage() throws MQClientException {

        consumer.subscribe("TestTopic", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            System.out.println("消费消息" + msgs);
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
    }

    private static void sendMessageOneWay() throws Exception {
        for (int i = 0; i < 10; i++) {
            Message msg = new Message("TestTopic", "TagA", "keys", "Hello RocketMQ".getBytes());
            producer.sendOneway(msg);
            System.out.println("发送成功");
        }
    }


    private static void sendOrderMessage() throws Exception {
        String[] tags = new String[]{"TagA", "TagB", "TagC", "TagD", "TagE"};
        for (int i = 0; i < 100; i++) {
            int orderId = i % 10;
            Message msg =
                    new Message("TestTopic", tags[i % tags.length], "KEY" + i,
                            ("Hello RocketMQ " + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            SendResult sendResult = producer.send(msg, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                    System.out.println("mqs: " + mqs);
                    Integer id = (Integer) arg;
                    int index = id % mqs.size();
                    return mqs.get(1);
                }
            }, orderId);

            System.out.printf("%s%n", sendResult);
        }
    }


    private static void sendDelayedMessage() throws Exception {
        int totalMessagesToSend = 100;
        for (int i = 0; i < totalMessagesToSend; i++) {
            Message message = new Message("TestTopic", ("Hello scheduled message " + i).getBytes());
            // This message will be delivered to consumer 10 seconds later.
            message.setDelayTimeLevel(3);
            // Send the message
            producer.send(message);
        }

    }

    private static void sendBatchMessage() throws Exception {
        //If you just send messages of no more than 1MiB at a time, it is easy to use batch
        //Messages of the same batch should have: same topic, same waitStoreMsgOK and no schedule support
        String topic = "TestTopic";
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(topic, "Tag", "OrderID001", "Hello world 0".getBytes()));
        messages.add(new Message(topic, "Tag", "OrderID002", "Hello world 1".getBytes()));
        messages.add(new Message(topic, "Tag", "OrderID003", "Hello world 2".getBytes()));

        producer.send(messages);
    }
}
