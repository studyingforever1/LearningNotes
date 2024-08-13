# 一、Redis主从复制

## 1.1、主从复制概念

Redis支持主从复制功能，用户执行slaveof（replicaof）命令或配置slaveof（replicaof）来开启复制功能。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/d18e789a7c6941cd9cd6f11c2b68bfaa.png)

主从复制的作用：

1. 读写分离
   Master处理写请求，Slaver处理读请求。
   Master关闭持久化，Slaver执行持久化。
2. 数据容灾
   主服务器宕机后，从服务器可以继续响应读请求。利用哨兵可以实现主从切换，做到高可用。

## 1.2、主从复制相关的变量

server.h

### redisServer

```c
struct redisServer {
	char replid[CONFIG_RUN_ID_SIZE+1];  /* My current replication ID. */
	int repl_ping_slave_period;     /* Master pings the slave every N seconds */
	char *repl_backlog;             /* Replication backlog for partial syncs */
	long long repl_backlog_size;    /* Backlog circular buffer size */
	long long repl_backlog_histlen; /* Backlog actual data length */
    long long repl_backlog_idx;     /* Backlog circular buffer current offset ...*/
	long long repl_backlog_off;     /* Replication "master offset" of first ... */
	list *slaves, *monitors;    	/* List of slaves and MONITORs */
	int repl_good_slaves_count;     /* Number of slaves with lag <= max_lag. */
	int repl_min_slaves_to_write;   /* Min number of slaves to write. */
	int repl_min_slaves_max_lag;    /* Max lag of <count> slaves to write. */
	sds masterauth;                 /* AUTH with this password with master */
	char *masterhost;               /* Hostname of master */
	int masterport;                 /* Port of master */
	client *master;     /* Client that is master for this slave */
	int repl_state;          /* Replication status if the instance is a slave */
	int repl_serve_stale_data; /* Serve stale data when link is down? */
	int repl_slave_ro;          /* Slave is read only? */

	......

}
```

* replid：Redis服务器的运行ID，长度为CONFIG_RUN_ID_SIZE(40)的随机字符串。对应主服务器表示当前服务器的运行ID；对于从服务器表示其复制的主服务器的运行ID。
* repl_ping_slave_period：发送心跳包周期，默认为10。TCP长连接用心跳包检测连接有效性。
* repl_backlog：复制缓冲区，用于缓存主服务器已执行且待发送给从服务器的命令请求。
* repl_backlog_size：复制缓冲区大小，可配置，默认为1MB。
* repl_backlog_histlen：复制缓冲区中存储的命令请求数据长度。
* repl_backlog_idx：复制缓冲区中存储的命令请求最后一个字节索引位置。
* repl_backlog_off：复制缓冲区中第一个字节的复制偏移量。
* slaves：从服务器链表，节点为client类型
* repl_good_slaves_count：当前有效从服务器的数量，心跳不超时的服务器为有效服务器。
* repl_min_slaves_to_write：最小有效写从服务器数量。
* repl_min_slaves_max_lag：写入从服务器的最大时间间隔，默认为10秒
* masterauth：从服务器用于设置请求同步主服务器时的认证密码。主服务器设置了requirepass。
* masterhost：主服务器的IP或主机名，在从服务器上设置。
* masterport：主服务器端口，，在从服务器上设置。
* master：当主从服务器连接成功后，从服务器成为主服务器的客户端，主服务器也会成为从服务器的客户端(主client)。
* repl_state：用于从节点，标志从节点当前的复制状态
* repl_serve_stale_data：主服务器断开连接后，从服务器是否继续处理命令请求。默认为1。
* repl_slave_ro：从服务器是否为只读，默认为1。

### repl_state

```c
typedef enum {
    REPL_STATE_NONE = 0,            /* No active replication */
    REPL_STATE_CONNECT,             /* Must connect to master */
    REPL_STATE_CONNECTING,          /* Connecting to master */
    /* --- Handshake states, must be ordered --- */
    REPL_STATE_RECEIVE_PING_REPLY,  /* Wait for PING reply */
    REPL_STATE_SEND_HANDSHAKE,      /* Send handshake sequance to master */
    REPL_STATE_RECEIVE_AUTH_REPLY,  /* Wait for AUTH reply */
    REPL_STATE_RECEIVE_PORT_REPLY,  /* Wait for REPLCONF reply */
    REPL_STATE_RECEIVE_IP_REPLY,    /* Wait for REPLCONF reply */
    REPL_STATE_RECEIVE_CAPA_REPLY,  /* Wait for REPLCONF reply */
    REPL_STATE_SEND_PSYNC,          /* Send PSYNC */
    REPL_STATE_RECEIVE_PSYNC_REPLY, /* Wait for PSYNC reply */
    /* --- End of handshake states --- */
    REPL_STATE_TRANSFER,        /* Receiving .rdb from master */
    REPL_STATE_CONNECTED,       /* Connected to master */
} repl_state;
```

* REPL_STATE_NONE：无主从复制关系
* REPL_STATE_CONNECT：等待和主机连接
* REPL_STATE_CONNECTING：正在和主机连接
* REPL_STATE_RECEIVE_PING_REPLY：等待ping返回
* REPL_STATE_SEND_HANDSHAKE：发送握手序列
* REPL_STATE_RECEIVE_AUTH_REPLY：等待身份认证返回
* REPL_STATE_RECEIVE_PORT_REPLY：等待接收端口返回
* REPL_STATE_RECEIVE_IP_REPLY：等待接收IP返回
* REPL_STATE_RECEIVE_CAPA_REPLY：等待接收capa返回
* REPL_STATE_SEND_PSYNC：发送PSYNC命令
* REPL_STATE_RECEIVE_PSYNC_REPLY：等待PSYNC命令返回
* REPL_STATE_TRANSFER：正在接收rdb文件
* REPL_STATE_CONNECTED：rdb文件接收并载入完毕，主从同步数据完成

## 1.3、主从复制的过程

主从复制的主要过程为：

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/c1b3cb0dfe514a0eab4f9eadfd678ba2.png)

* 建立主从关系：主从连接成功后，从节点需要将自身信息（ip、port等）发送给主节点。
* 同步数据：从节点连接主节点后，需要先同步数据，数据一致后才可进行命令复制。
  全量同步：主节点生成RDB数据并发送给从节点
  增量同步：主节点把复制缓冲区中偏移量之后的命令发送给从节点。
* 命令传播：主节点在运行期间，将执行的写命令传播给从节点，从节点接收并执行命令。（异步执行）

### 建立主从关系

#### slaveof（replicaof）

用户可以通过执行slaveof(replicaof)命令开启主从复制功能

```
slaveof(replicaof) masterip masterport
```

#### replicaofCommand

流程如下：

1. 如果是集群的主节点，则返回
2. 如果是正在进行故障转移，则返回
3. 如果参数为no one ,如果是从机则调用replicationUnsetMaster()取消主从关系
4. 如果是从机客户端，则返回
5. 从参数中获得主机的ip和端口，如果是从机并且和配置的IP和端口相同，则返回
6. 调用replicationSetMaster()设置新的主从关系
7. 获取客户端信息，并释放内存

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/ff4260c8c0754a81b70ddaef32131e5c.png)

#### replicationUnsetMaster

取消主从关系：

1. 如果该服务器是主服务器，则返回
2. 如果连接状态是已连接，则触发解除主机连接模块事件
3. 将该服务器的主机信息置空
4. 解除握手和主从关系
5. 将该服务器置为主机
6. 如果设置允许aof并且aof状态为关闭，则重新启动aof同步

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/7b8238d3df834667bda80648378fe6f2.png)

#### replicationSetMaster

设置主从关系：

1. 设置主机变量
2. 清空旧的主机信息
3. 断开旧的主机连接
4. 断开所有阻塞客户端
5. 设置新的主机ip和端口
6. 断开从机连接
7. 取消握手
8. 如果是主机，则释放主机缓存
9. 触发角色转换模块事件
10. 如果是已连接，则触发主机连接模块事件
11. 设置状态为待连接
12. 调用connectWithMaster，发起主从网络连接

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/a72f22d5c5204ad4bf8813f49246c30c.png)

#### connectWithMaster

两个地方调用：

* 在replicationSetMaster调用
* 在replicationCron中调用
  ```
  int serverCron(struct aeEventLoop *eventLoop, long long id, void *clientData) {
  	......
  	if (server.failover_state != NO_FAILOVER) {
          	run_with_period(100) replicationCron();
      	} else {
          	run_with_period(1000) replicationCron();
      	}
  	......
  }
  void replicationCron(void) {
  	......
  	if (server.repl_state == REPL_STATE_CONNECT) {
          	serverLog(LL_NOTICE,"Connecting to MASTER %s:%d",
              	server.masterhost, server.masterport);
          	connectWithMaster();
      	}
  	......
  }


  ```

建立网络连接：

1. 建立socket连接
2. 调用connConnect()连接到主节点
3. 连接成功后设置syncWithMaster处理器
4. 设置最后一次传输时间
5. 设置状态为连接
6. 返回成功

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/ade4562d5b584be39f7bcdfadca707af.png)

#### syncWithMaster

网络连接成功后，从节点进入握手阶段：

1. 如果状态是非主从，则关闭连接并返回
2. 如果连接不是已连接，则跳转错误
3. 如果状态是正在连接，则注册读事件回调函数syncWithMaster
4. 发送ping命令，设置状态为等待接收ping应答
5. 如果状态为接收ping应答，则接收响应
6. 如果响应有noauth、noperm，则释放响应信息，跳转错误
7. 否则设置状态为发送握手序列
8. 如果状态为发送握手序列，如果主机需要做验证，则获取验证参数并比对
9. 获得监听端口，发送命令replconf listening-port
10. 释放内存
11. 如果有ip，则发送命令replconf ip-address
12. 发送命令replconf capa eof capa psync2
13. 设置状态为等待身份认证应答
14. 如果状态是等待身份认证应答并且不需要认证，则状态改为接收端口应答
15. 如果状态是接收端口应答，则接收响应，没有错误，则状态设为接收IP响应
16. 如果状态是接收IP响应，则接收响应，没有错误，则状态设为接收capa响应
17. 如果状态是接收capa响应，则接收响应，没有错误，则状态设为发送psync命令到主节点
18. 如果状态是发送psync命令，则调用slaveTryPartialResynchronization函数进行同步数据
19. 将状态设为接收psync命令响应
20. 设置发送属性

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/8eb1bfc30edf4334b40edb12929abde5.png)

### 同步数据

从节点同步数据，涉及的属性有：

* server.master：主节点客户端
* server.cached_master：主节点客户端缓存

#### slaveTryPartialResynchronization

发送psync命令及进行部分同步：

1. 如果read_reply为0，发送PSYNC命令
2. 如果cached_master不为空，则设置cached_master信息，进行部分同步
3. 否则，使用全量同步，发送 PSYNC  ?  -1
4. 如果是故障转移，则发送PSYNC FAILOVER命令
5. 否则发送PSYNC命令
6. 读取主节点响应数据
7. 如果响应数据长度为0，则释放响应内容，并返回PSYNC_WAIT_REPLY
8. 如果返回FULLERSYNC，则表示主节点要进行全量同步
9. 如果返回+CONTINUE，则进行部分同步

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/a4907fb37c1f4c3dbc45d2bc2c72ca78.png)

#### 全量同步

在syncWithMaster中，在REPL_STATE_RECEIVE_PSYNC状态后进行全量同步：

1. 调用slaveTryPartialResynchronization函数发送PSYNC命令
2. 如果响应为PSYNC_WAIT_REPLY，则返回
3. 如果响应为PSYNC_TRY_LATER，则跳转错误
4. 如果响应为PSYNC_NOT_SUPPORTED，则发送SYNC命令，并跳转错误
5. 设置读事件处理函数，接收主节点的RDB数据
6. 状态变为REPL_STATE_TRANSFER

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/bc6ddf50cbc44ecc87cb36e6eb98581f.png)

### 命令传播

主从同步数据完成后，进入复制阶段，是通过命令传播的方式实现的。

server.c  propagate

#### replicationFeedSlaves

propagate函数调用replicationFeedSlaves函数，将主节点接收的命令传播给从节点：

1. 如果不是主机，则返回
2. 如果复制缓冲区为空，则返回
3. 将命令写入复制缓冲区
4. 将命令写入所有从节点的客户端回复缓存区

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/2a00aa8148234e5c99a69e2795fab474.png)

## 1.7、设计思想和优势

* 主从复制的三个阶段为：建立主从关系、同步数据、命令传播
* 主从握手阶段，从节点将自身信息发送给主节点
* 主从复制是通过repl_state状态机来进行控制的
* 主节点通过将执行的写命令传播给从节点，从而达到数据复制的效果

# 二、Redis哨兵

## 2.1、哨兵概述

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/31103990e9764f3193571d39cebdf619.png)

* 哨兵（Sentinel）机制是Redis的一种HA的解决方案，用于解决主从机制的单点故障问题。
* Sentinel监控主从节点的运行状态，当主节点下线后进行故障转移

### 相关结构体

sentinel.c

sentinelState

```c
/* Main state. */
struct sentinelState {
    char myid[CONFIG_RUN_ID_SIZE+1]; /* This sentinel ID. */
    uint64_t current_epoch;         /* Current epoch. */
    dict *masters;      /* Dictionary of master sentinelRedisInstances.
                           Key is the instance name, value is the
                           sentinelRedisInstance structure pointer. */
    int tilt;           /* Are we in TILT mode? */
    int running_scripts;    /* Number of scripts in execution right now. */
    mstime_t tilt_start_time;       /* When TITL started. */
    mstime_t previous_time;         /* Last time we ran the time handler. */
    list *scripts_queue;            /* Queue of user scripts to execute. */
    char *announce_ip;  /* IP addr that is gossiped to other sentinels if
                           not NULL. */
    int announce_port;  /* Port that is gossiped to other sentinels if
                           non zero. */
    unsigned long simfailure_flags; /* Failures simulation. */
    int deny_scripts_reconfig; /* Allow SENTINEL SET ... to change script
                                  paths at runtime? */
    char *sentinel_auth_pass;    /* Password to use for AUTH against other sentinel */
    char *sentinel_auth_user;    /* Username for ACLs AUTH against other sentinel. */
    int resolve_hostnames;       /* Support use of hostnames, assuming DNS is well configured. */
    int announce_hostnames;      /* Announce hostnames instead of IPs when we have them. */
} sentinel;
```

* myid：标志id，用于区分不同的sentinel节点
* current_epoch：sentinel集群的当前纪元号
* master：监控的主节点字典
* tilt：是否处于TILT模式
* running_scripts：目前正在执行的脚本数量
* tilt_start_time：TILT模式开始时间
* previous_time：上次运行时间处理器的时间
* scripts_queue：用户脚本执行队列
* announce_ip：宣布的ip
* announce_port：宣布的端口
* simfailure_flags：模拟失败标志
* deny_scripts_reconfig：运行重新配置脚本
* sentinel_auth_pass：哨兵认证密码
* sentinel_auth_user：哨兵认证用户
* resolve_hostnames：处理主机名
* announce_hostnames：宣布的主机名

## 2.2、哨兵启动

Redis服务器main函数

```c
if (server.sentinel_mode) {
        initSentinelConfig();
        initSentinel();
}
```

### initSentinelConfig

初始化哨兵配置：

1. 将端口设置为26379
2. 关闭保护模式

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/d5b45894842d4a83bec17ab663bfbd41.png)

### initSentinel

初始化哨兵：

1. 清空命令字典
2. 清空ACL
3. 加载sentinel命令
4. 初始化sentinel属性

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/64219aee26084070bf9db0703db5a0bb.png)

## 2.3、主要逻辑

serverCron时间事件会检查服务器是否运行在sentinel模式下，如果是则调用定时器sentinelTimer

```c
if (server.sentinel_mode) sentinelTimer(); 
```

### sentinelTimer

sentinel定时器：

1. 检查是否需要进入TILT模式
2. 调用主逻辑函数sentinelHandleDictOfRedisInstances
3. 定时执行sentinel脚本
4. 随机化定时器下次执行时间

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/ae8f40e2544248318682c6091df899fa.png)

### sentinelHandleDictOfRedisInstances

1. 调用sentinel处理函数：sentinelHandleRedisInstance
2. 如果是主节点，则递归调用sentinelHandleDictOfRedisInstances处理该主节点下的slaves和sentinel

#### sentinelHandleRedisInstance

1. 建立网络连接
2. 发送定时消息
3. 检查是否存在主观下线节点
4. 如果是主节点，检查是否存在客观下线节点
5. 如果需要进行故障转移，则发起投票请求
6. 设置故障转移状态机
7. 询问其他sentinel对该主节点主观下线的判定结果

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/d611a91706484fd3b2f1c8185bb56c77.png)

## 2.3、故障转移

故障转移时sentinel机制的重要功能，也是Redis实现HA的基础。

### sentinelCheckSubjectivelyDown

检测某个节点是否主观下线：

1. 如果响应时间不为0，则计算时间差为当前时间-响应后时间
2. 否则如果连接中断，则计算时间差为当前时间-上次响应时间
3. 如果时间差大于down_after_period，则添加S_DOWN标志
4. 如果当前节点是主节点，并且在info响应中是从节点，并且响应时间超过down_after_period+info发送命令的时间间隔*2
5. 则添加S_DOWN标志
6. 否则清除S_DOWN标志

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/e11dbe7238bd44f0b7964db2718d0bd6.png)

### sentinelCheckObjectivelyDown

检测某个节点是否客观下线：

1. 如果主节点已经主观下线
2. 则遍历sentinel字典，统计判断主观下线的sentinel数量
3. 如果数量大于等于法定节点数，则判定客观下线odown=1
4. 如果odown=1，如果没有添加O_DOWN标志，则添加O_DOWN标志

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/83ffbb0ed8b14bfbba5c99d4f9dcf06f.png)

### sentinelStartFailoverIfNeeded

进行故障转移：

1. 如果主节点不是客观下线，则返回0
2. 如果正在进行故障转移，则返回0
3. 如果当前时间-上次故障转移时间小于故障转移超时时间的2倍，则不允许进行故障转移
4. 调用sentinelStartFailover函数启动故障转移

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/857d5595bfb549d9813bb8b11fd87b1c.png)

### sentinelAskMasterStateToOtherSentinels

询问判断结果，发送选举请求：

1. 遍历主节点的sentinel字典
2. 如果是主节点主观下线，则发送请求
3. 发送异步redis命令，进行投票和询问

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/a215310e53864b8f985e9803aff094d4.png)

## 2.4、设计思想和优势

1. Sentinel节点主要逻辑通过定时器触发
2. Sentinel节点经过判定主观下线和客观下线，确定某个主节点下线。
3. 当主节点下线后，Sentinel集群会选举的方式选出leader节点，进行故障转移

# 三、Redis集群

## 3.1、Cluster定义

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/f9191eb4d84c4e9e82af9b9a3e2ff6e5.png)

Redis Cluster是Redis提供的分布式数据库方案，主要包含三个部分：

* 数据分片
  Cluster会对数据进行分片，将不同分片的数据指派给集群不同的主节点。
* 主从复制
  Cluster使用Redis主从复制模型实现数据热备份
* 故障转移
  Cluster实现了故障转移，保证集群的HA，当集群中某个主节点下线后，Cluster会选择合适的从节点晋升为主节点。

## 3.2、槽位定义

cluster.h

### clusterState

redis Cluster中每个节点都维护一份自己视角下的当前整个集群的状态，该状态的信息存储在clusterState结构体中。

```c
typedef struct clusterState {
    clusterNode *myself;  /* This node */
    uint64_t currentEpoch;
    int state;            /* CLUSTER_OK, CLUSTER_FAIL, ... */
    int size;             /* Num of master nodes with at least one slot */
    dict *nodes;          /* Hash table of name -> clusterNode structures */
    dict *nodes_black_list; /* Nodes we don't re-add for a few seconds. */
    clusterNode *migrating_slots_to[CLUSTER_SLOTS];
    clusterNode *importing_slots_from[CLUSTER_SLOTS];
    clusterNode *slots[CLUSTER_SLOTS];
    uint64_t slots_keys_count[CLUSTER_SLOTS];
    rax *slots_to_keys;
    /* The following fields are used to take the slave state on elections. */
    mstime_t failover_auth_time; /* Time of previous or next election. */
    int failover_auth_count;    /* Number of votes received so far. */
    int failover_auth_sent;     /* True if we already asked for votes. */
    int failover_auth_rank;     /* This slave rank for current auth request. */
    uint64_t failover_auth_epoch; /* Epoch of the current election. */
    int cant_failover_reason;   /* Why a slave is currently not able to
                                   failover. See the CANT_FAILOVER_* macros. */
    /* Manual failover state in common. */
    mstime_t mf_end;            /* Manual failover time limit (ms unixtime).
                                   It is zero if there is no MF in progress. */
    /* Manual failover state of master. */
    clusterNode *mf_slave;      /* Slave performing the manual failover. */
    /* Manual failover state of slave. */
    long long mf_master_offset; /* Master offset the slave needs to start MF
                                   or -1 if still not received. */
    int mf_can_start;           /* If non-zero signal that the manual failover
                                   can start requesting masters vote. */
    /* The following fields are used by masters to take state on elections. */
    uint64_t lastVoteEpoch;     /* Epoch of the last vote granted. */
    int todo_before_sleep; /* Things to do in clusterBeforeSleep(). */
    /* Messages received and sent by type. */
    long long stats_bus_messages_sent[CLUSTERMSG_TYPE_COUNT];
    long long stats_bus_messages_received[CLUSTERMSG_TYPE_COUNT];
    long long stats_pfail_nodes;    /* Number of nodes in PFAIL status,
                                       excluding nodes without address. */
} clusterState;
```

* myself：自身节点
* currentEpoch：集群当前纪元号
* state：集群状态
* nodes：集群节点实例字典
* slots：槽位指派数组，数组索引对应槽位
* migrating_slots_to：迁出槽位
* importing_slots_from：迁入槽位
* slots_keys_count：每个槽位存储的键的数量

### clusterNode

集群节点

```c
typedef struct clusterNode {
    mstime_t ctime; /* Node object creation time. */
    char name[CLUSTER_NAMELEN]; /* Node name, hex string, sha1-size */
    int flags;      /* CLUSTER_NODE_... */
    uint64_t configEpoch; /* Last configEpoch observed for this node */
    unsigned char slots[CLUSTER_SLOTS/8]; /* slots handled by this node */
    sds slots_info; /* Slots info represented by string. */
    int numslots;   /* Number of slots handled by this node */
    int numslaves;  /* Number of slave nodes, if this is a master */
    struct clusterNode **slaves; /* pointers to slave nodes */
    struct clusterNode *slaveof; /* pointer to the master node. Note that it
                                    may be NULL even if the node is a slave
                                    if we don't have the master node in our
                                    tables. */
    mstime_t ping_sent;      /* Unix time we sent latest ping */
    mstime_t pong_received;  /* Unix time we received the pong */
    mstime_t data_received;  /* Unix time we received any data */
    mstime_t fail_time;      /* Unix time when FAIL flag was set */
    mstime_t voted_time;     /* Last time we voted for a slave of this master */
    mstime_t repl_offset_time;  /* Unix time we received offset for this node */
    mstime_t orphaned_time;     /* Starting time of orphaned master condition */
    long long repl_offset;      /* Last known repl offset for this node. */
    char ip[NET_IP_STR_LEN];  /* Latest known IP address of this node */
    int port;                   /* Latest known clients port (TLS or plain). */
    int pport;                  /* Latest known clients plaintext port. Only used
                                   if the main clients port is for TLS. */
    int cport;                  /* Latest known cluster port of this node. */
    clusterLink *link;          /* TCP/IP link with this node */
    list *fail_reports;         /* List of nodes signaling this as failing */
} clusterNode;
```

* flags：节点标志，表示节点的状态：CLUSTER_NODE_MASTER、CLUSTER_NODE_SLAVER、CLUSTER_NODE_PFAIL、CLUSTER_NODE_FAIL
* name：节点名称，即节点ID（40个字节随机字符串）
* configEpoch：最新写入数据文件的纪元号（最新执行故障转移成功的纪元）
* slots：槽位位图，记录该节点负责的槽位
* slaves：该节点的从节点列表
* slaveof：该节点的主节点实例
* ping_sent：上次给该节点发送ping请求的时间
* pong_received：上次该节点收到pong响应的时间
* data_received：上次该节点收到响应数据的时间
* fail_time：节点下线时间
* voted_time：上次该节点投票时间
* ip、port、cport：节点的ip、端口、集群端口
* link：节点连接
* fail_reports：下线报告列表，记录所有判定该节点主观下线的主节点

## 3.3、Cluster启动

### 节点启动

在initServer函数中：

```
if (server.cluster_enabled) clusterInit();
```

cluster.c

#### clusterInit

初始化集群：

1. 初始化集群变量
2. 如果锁住集群配置文件失败，则退出
3. 加载集群配置，创建自身节点实例并添加到实例字典中
4. 如果端口大于65535-10000，则退出
5. 将server的端口+10000，作为集群端口，开启监听，并注册回调函数

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/49a17932b90848489b3eac5dcecb39c8.png)

### 节点握手

```
cluster meet ip port 
```

#### clusterCommand

集群命令都在clusterCommand函数中处理 ，在clusterCommand调用clusterStartHandshake函数

#### clusterStartHandshake

1. 检测ip，不符合返回
2. 检测端口，不符合返回
3. 创建节点实例
4. 添加到实例字典中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/c43d395838704346a341e3ab866c12c9.png)

### 指派槽位

```
redis -cli cluster addslots {0..5460} 
```

在clusterCommand调用clusterAddSlot函数

#### clusterAddSlot

1. 更新实例槽位位图
2. 将自身实例添加到槽位指派数组对应的索引中

### 建立主从关系

```
cluster replicate master_nodeid 
```

在clusterCommand调用clusterSetMaster函数

#### clusterSetMaster

1. 如果当前节点是主节点，则切换为从节点
2. 否则清除slaveof属性
3. 设置主节点实例
4. 建立主从关系
5. 设置手动故障转移

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/ac9235fa3c144aac99a283e912a704e1.png)

## 3.4、节点通信

### Gossip算法

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/c99c8f2e0a8942ef96e54e6caada3cab.png)

* Gossip算法是一种去中心化的一致性算法，该算法无中心节点（leader节点）。
* Cluster机制是一个“简化”的Gossip算法，每个节点随机选择集群中的一个节点作为消息接收节点。
* 每个节点从自身实例字典中随机算账部分节点实例放入消息，在发送给目标节点。
* 节点同步数据需要一定时间，所以是最终一致性算法。

### 消息定义

消息结构体：clusterMsg

```c
typedef struct {
    char sig[4];        /* Signature "RCmb" (Redis Cluster message bus). */
    uint32_t totlen;    /* Total length of this message */
    uint16_t ver;       /* Protocol version, currently set to 1. */
    uint16_t port;      /* TCP base port number. */
    uint16_t type;      /* Message type */
    uint16_t count;     /* Only used for some kind of messages. */
    uint64_t currentEpoch;  /* The epoch accordingly to the sending node. */
    uint64_t configEpoch;   /* The config epoch if it's a master, or the last
                               epoch advertised by its master if it is a
                               slave. */
    uint64_t offset;    /* Master replication offset if node is a master or
                           processed replication offset if node is a slave. */
    char sender[CLUSTER_NAMELEN]; /* Name of the sender node */
    unsigned char myslots[CLUSTER_SLOTS/8];
    char slaveof[CLUSTER_NAMELEN];
    char myip[NET_IP_STR_LEN];    /* Sender IP, if not all zeroed. */
    char notused1[32];  /* 32 bytes reserved for future usage. */
    uint16_t pport;      /* Sender TCP plaintext port, if base port is TLS */
    uint16_t cport;      /* Sender TCP cluster bus port */
    uint16_t flags;      /* Sender node flags */
    unsigned char state; /* Cluster state from the POV of the sender */
    unsigned char mflags[3]; /* Message flags: CLUSTERMSG_FLAG[012]_... */
    union clusterMsgData data;
} clusterMsg;
```

消息头：

* sig：固定为RCmb，表示是cluster消息
* totlen：消息总长度
* ver：消息协议版本 ， 1
* type：消息类型，CLUSTER_NODE_MEET、CLUSTER_TYPE_PING、CLUSTER_TYPE_PONG、CLUSTER_TYPE_FAIL
* flags：发送节点标志
* currentEpoch：发送节点当前纪元
* configEpoch：发送节点最新写入文件纪元
* sender：发送节点名称
* myslots：槽位位图
* slaveof：主节点
* myip：ip
* cport：集群端口
* port：原始端口

消息体：

data，类型为clusterMsgData

```c
union clusterMsgData {
    /* PING, MEET and PONG */
    struct {
        /* Array of N clusterMsgDataGossip structures */
        clusterMsgDataGossip gossip[1];
    } ping;

    /* FAIL */
    struct {
        clusterMsgDataFail about;
    } fail;

    /* PUBLISH */
    struct {
        clusterMsgDataPublish msg;
    } publish;

    /* UPDATE */
    struct {
        clusterMsgDataUpdate nodecfg;
    } update;

    /* MODULE */
    struct {
        clusterMsgModule msg;
    } module;
};
```

ping：存放随机实例和主观下线节点的实例

fail：节点客观下线通知，存放客观下线节点名称

### 建立连接

如果当前节点运行在Cluster模式下，则serverCron时间事件会每隔100毫秒触发一次clusterCron函数

```c
 run_with_period(100) {
        if (server.cluster_enabled) clusterCron();
 }
```

#### clusterCron

1. 设置握手超时时长
2. 更新当前节点标志
3. 遍历实例字典的实例，如果有节点连接信息为空，则与该节点建立连接
4. 创建socket连接
5. 注册读事件回调函数clusterLinkConnectHandler
6. 发送CLUSTER_NODE_MEET或CLUSTERMSG_TYPE_MEET消息

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/cf25b426b31947a8ac7115b43b43a21b.png)

### 节点握手

在clusterLinkConnectHandler中设置读事件回调函数clusterReadHandler

在clusterReadHandler中调用clusterProcessPacket

#### clusterProcessPacket

处理握手过程：

1. 处理TCP拆包、粘包
2. 根据不同的消息类型计算消息体长度
3. 获得发送节点
4. 调用clusterProcessGossipSection处理消息体

## 3.5、节点下线

在Cluster中判定节点下线，需要经过"主观下线"和“客观下线”两个过程。

#### 主观下线

#### clusterCron

1. 计算节点上次返回响应后过去的时间
2. 如果该时间超过timeout，则判定该节点主观下线
3. 给该节点添加CLUSTER_NODE_PFAIL标志

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/fceab7bf98074b999ccdf540bbe7b468.png)

#### 客观下线

在clusterProcessGossipSection中如果发送节点为主节点并且判定已经主观下线，则调用markNodeAsFailingIfNeeded判定客观下线

#### markNodeAsFailingIfNeeded

1. 计算法定节点数
2. 如果节点为非主观下线，则返回
3. 如果节点为客观下线，则返回
4. 获得下线报告列表中主节点数量
5. 如果自己是主节点，则数量加1
6. 如果数量小于法定节点数，则返回
7. 判定节点客观下线，添加CLUSTER_NODE_FAIL标志
8. 广播该节点已客观下线

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1669606422025/8c435d388aec495a8db956037d4fbc1f.png)

## 3.6、设计思想和优势

* Cluster机制将数据划分到不同的槽位中，并将不同槽位指派给不同节点，实现分布式存储
* 集群命令都在clusterCommand函数中处理。
* 主观下线和客观下线的判定
