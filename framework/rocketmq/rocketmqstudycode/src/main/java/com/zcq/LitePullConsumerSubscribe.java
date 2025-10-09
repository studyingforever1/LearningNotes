package com.zcq;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LitePullConsumerSubscribe {
    public static volatile boolean running = true;
    public static void main(String[] args) throws Exception {
        DefaultLitePullConsumer litePullConsumer = new DefaultLitePullConsumer("lite_pull_consumer_test");
        litePullConsumer.subscribe("TopicTest", "*");
        litePullConsumer.setPullBatchSize(20);
        litePullConsumer.start();
        try {
            while (running) {
                List<MessageExt> messageExts = litePullConsumer.poll();
                System.out.printf("%s%n", messageExts);
            }
        } finally {
            litePullConsumer.shutdown();
        }
    }

    public class LitePullConsumerAssign {
        public static volatile boolean running = true;
        public static void main(String[] args) throws Exception {
            DefaultLitePullConsumer litePullConsumer = new DefaultLitePullConsumer("please_rename_unique_group_name");
            litePullConsumer.setAutoCommit(false);
            litePullConsumer.start();
            Collection<MessageQueue> mqSet = litePullConsumer.fetchMessageQueues("TopicTest");
            List<MessageQueue> list = new ArrayList<>(mqSet);
            List<MessageQueue> assignList = new ArrayList<>();
            for (int i = 0; i < list.size() / 2; i++) {
                assignList.add(list.get(i));
            }
            litePullConsumer.assign(assignList);
            litePullConsumer.seek(assignList.get(0), 10);
            try {
                while (running) {
                    List<MessageExt> messageExts = litePullConsumer.poll();
                    System.out.printf("%s %n", messageExts);
                    litePullConsumer.commitSync();
                }
            } finally {
                litePullConsumer.shutdown();
            }
        }
    }
}