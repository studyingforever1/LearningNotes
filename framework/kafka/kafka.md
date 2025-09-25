# kafka

**消息队列**（英语：Message queue）是一种[进程间通信](https://zh.wikipedia.org/wiki/进程间通信)或同一进程的不同[线程](https://zh.wikipedia.org/wiki/线程)间的通信方式，[软件](https://zh.wikipedia.org/wiki/軟體)的[贮列](https://zh.wikipedia.org/wiki/貯列)用来处理一系列的[输入](https://zh.wikipedia.org/wiki/输入)，通常是来自用户。消息队列提供了[异步](https://zh.wikipedia.org/wiki/異步)的[通信协议](https://zh.wikipedia.org/wiki/通信协议)，每一个贮列中的纪录包含详细说明的资料，包含发生的时间，输入设备的种类，以及特定的输入参数，也就是说：消息的发送者和接收者不需要同时与消息队列交互。消息会保存在[队列](https://zh.wikipedia.org/wiki/队列)中，直到接收者取回它。

- 消息：Record。Kafka是消息引擎嘛，这里的消息就是指Kafka处理的主要对象。
- 主题：Topic。主题是承载消息的逻辑容器，在实际使用中多用来区分具体的业务。
- 分区：Partition。一个有序不变的消息序列。每个主题下可以有多个分区。
- 消息位移：Offset。表示分区中每条消息的位置信息，是一个单调递增且不变的值。
- 副本：Replica。Kafka中同一条消息能够被拷贝到多个地方以提供数据冗余，这些地方就是所谓的副本。副本还分为领导者副本和追随者副本，各自有不同的角色划分。副本是在分区层级下的，即每个分区可配置多个副本实现高可用。
- 生产者：Producer。向主题发布新消息的应用程序。
- 消费者：Consumer。从主题订阅新消息的应用程序。
- 消费者位移：Consumer Offset。表征消费者消费进度，每个消费者都有自己的消费者位移。
- 消费者组：Consumer Group。多个消费者实例共同组成的一个组，同时消费多个分区以实现高吞吐。
- 重平衡：Rebalance。消费者组内某个消费者实例挂掉后，其他消费者实例自动重新分配订阅主题分区的过程。Rebalance是Kafka消费者端实现高可用的重要手段。

![](D:\doc\my\studymd\LearningNotes\framework\kafka\images\58c35d3ab0921bf0476e3ba14069d291.jpg)



