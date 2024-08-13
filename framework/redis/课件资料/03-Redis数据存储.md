# 一、RedisObject

## 1.1、Redis数据存储

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/888f47c129e64eb99a446a0003a3756f.png)

## 1.2、RedisObject的数据结构

redis的value都封装在redisObject中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/30aba6d423364e889e299d8349d7cf77.png)

redisObject的底层实现：

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/78678b6f70e74e4097b35e96fe4e09d0.png)

redisObject的数据结构如下：

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/63c7ae7b08af4ea0beffe02be6332969.png)

server.h

```
typedef struct redisObject {
    unsigned type:4;
    unsigned encoding:4;
    unsigned lru:LRU_BITS; /* LRU time (relative to global lru_clock) or
                            * LFU data (least significant 8 bits frequency
                            * and most significant 16 bits access time). */
    int refcount;
    void *ptr;
} robj;
```

* type：4bit，表示value的类型
  obj_string:字符串 0
  obj_list:列表 1
  obj_set :集合 2
  obj_zset : 有序集合 3
  obj_hash: 散列 4
  obj_module: 模块 5
  obj_stream 流 6
* encoding：4bit，表示value内部存储的编码
  10大编码
  String: raw(0)、int(1)、embstr(8)
  hash: ht(2)、zipmap(3)、ziplist(5)
  list: quicklist(9)
  zet: intset(6)、ziplist(5)
  zset: skiplist(7)
  stream: listpack(10)
* lru（lfu）：24bit，lru表示最后一次访问时间，lfu高16位表示分钟级别的访问时间，低8位表示访问频率
* refcount：被引用的计数
  共享对象：共享值相同的对象

  ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/5be5ed8cd82949c797a8f4568be9d918.png)

  共享对象一般是整数，不做字符串的

  整数判断相同 判断值 : O(1)

  字符串判断相同 判断内容 O(n)
* ptr：指向具体数据的指针

## 1.3、API解析

object.c

### createObject

创建对象：

1. 申请内存
2. 初始化属性
3. 如果内存策略是lfu，则赋值lru属性为unix时间取低16位
4. 否则取unix秒级时间戳
5. 返回对象指针

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/a0216b95b7484902a72ba8bc2d836520.png)

### freeXXXObject

释放对象，具体跟类型有关

如果是这个类型，则调用该类型的API进行释放

* freeStringObject
* freeListObject
* freeSetObject
* freeZsetObject

  ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/8ae60a94a9d74634b612a65c780dd673.png)
* freeHashObject
* freeModuleObject
* freeStreamObject

### incrRefCount

自增引用计数

如果引用计数不等于int的最大值 则引用计数自增1

### decrRefCount

自减引用计数

1. 如果引用计数等于1，则要释放对象
2. 否则引用计数减1

### checkType

检测type类型

如果对象类型不是要检测类型，则响应错误

### tryObjectEncoding

压缩编码redisObject

1. 如果有引用则不能进行编码压缩
2. 如果长度小于20并且是整数
3. 如果数字小于9999，则对象引用计数减1，共享整数引用计数加1
4. 返回共享数字
5. 否则转化整数并赋值
6. 如果不是整数，则返回对象

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/ff46cef290004a7ba5abe1aabc5e110a.png)

### getDecodedObject

获取解码后的redisObject

1. 如果使用对象，则计数加1
2. 返回对象、
3. 如果类别是字符串并且编码是int，则转整数并返回

### objectCommand：object对象命令

* OBJECT REFCOUNT KEY ：查看当前键的引用计数
* OBJECT ENCODING KEY：查看当前键的编码
* OBJECT IDLETIME KEY：查看当前键的空闲时间
* OBJECT FREQ KEY：查看当前键最近访问频率的对数
* OBJECT HELP：查看OBJECT命令的帮助信息

1. 获得命令参数
2. 如果是help，则响应帮助信息
3. 如果是refcount key，则去键空间中查找该键的robj对象，响应refcount
4. 如果是encoding key，则查找该键的robj对象，响应取出robj的encoding
5. 如果是idletime key，则查找该键的robj对象，如果是lfu淘汰策略，则响应错误
6. 否则响应idle time
7. 如果是freq key，如果不是lfu淘汰策略，则响应错误
8. 否则响应访问频次
9. 如果都不是以上命令，则响应子命令语法错误

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/0dd2d97b51c04d80a72e5c45fdaa5925.png)

## 1.4、zipmap

当redis存储hash时，如果元素个数比较少，则采用zipmap存储

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/9271201618c84b5cb8958e3898ac4d1c.png)

* zmlen：1byte，表示元素个数
* len：1byte或5byte，表示key或value的长度
* free：value后的空闲长度

zipmap.c

### zipmapSet

设置键值对：

1. 获取存储键值对所需的内存大小
2. 判断key是否存在于zipmap中
3. 如果key不存在，则申请内存存储新创建的kv
4. 如果key存在，则获取原始kv的freelen
5. 如果freelen小于reqlen，则需要扩大zipmap的内存
6. 如果freelen大于reqlen，如果两者的差大于254，则需要缩小zipmap的内存
7. 如果两者的差小于254，则存储在free中
8. 将新的键值对拷贝到zipmap中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/d9d3faf9875d4568b495fc4334d357bd.png)

## 1.5、设计思想和优势

1. redis的value都封装在redisObject中
   ptr指针指向具体对象
2. 对象的统一处理方式：引用计数、类别检查、缓存淘汰
   redisObject中有这些属性
3. 不同的编码转换可以节省内存和优化查询
4. 共享对象的使用可以有效的减少内存占用
   整数 共享

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/743ebe4f1e4c45a580301fec2eaf4909.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/16d9dbe43d5c40889fc52c09c1e93ffe.png)

# 二、RedisDB

## 2.1、redisDb的数据结构

server.h

RedisDb就是redis的数据库，主要结构如下：

```c
typedef struct redisDb {
    dict *dict;                 /* The keyspace for this DB */
    dict *expires;              /* Timeout of keys with a timeout set */
    dict *blocking_keys;        /* Keys with clients waiting for data (BLPOP)*/
    dict *ready_keys;           /* Blocked keys that received a PUSH */
    dict *watched_keys;         /* WATCHED keys for MULTI/EXEC CAS */
    int id;                     /* Database ID */
    long long avg_ttl;          /* Average TTL, just for stats */
    unsigned long expires_cursor; /* Cursor of the active expire cycle. */
    list *defrag_later;         /* List of key names to attempt to defrag one by one, gradually. */
} redisDb;
```

* dict *dict：数据库键空间，保存着数据库中的所有键值对
* dict *expires：键的过期时间，key是键，value是过期时间
* dict *blocking_keys：处于阻塞状态的key和client
* dict *ready_keys：解除阻塞状态的key和client
* dict *watched_keys：watch的key和client
* int id：数据库id
* long long avg_ttl：数据库内所有键的平均生存时间
* list *defrag_later：尝试碎片整理的key列表

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/cd1acb5bcde443c097f96c5efc215253.png)

## 2.2、数据库键空间

### 键空间初始化

在initServer中，创建redisdb并初始化键空间

1. 循环依次在16个db中创建键空间
2. 调用dictCreate,创建键空间

db.c

### 键空间的常见操作

* lookupkey：从数据库中取出key对应的value对象
* lookupkeyRead和lookupkeyWrite：以读操作的方式从数据库中读取指定key的value对象和以写操作的方式从数据库中读取指定key的value对象
* dbAdd：添加键值对到指定db
* dbOverwrite：修改指定键的值
* setKey：指定指定键的值
* dbDelete：删除指定key
* dbExists：判断指定key是否存在
* dbRandomKey：随机返回数据库中的key

#### lookupKey

查找指定键的value对象:

1. 调用dictFind在字典中查找匹配的节点
2. 如果找到了，则取出该key对应的value
3. 如果没有进行持久化并且flag不是不修改最近访问时间
4. 如果缓存淘汰策略是LFU，则更新lfu的计数和时间戳（分钟）到value的lru中
5. 否则获得lru时间戳并更新到value的lru中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/180a4c6d73044a81a9248c0a1eb68670.png)

#### dbAdd

添加键值对：

1. 复制键名
2. 调用dictAdd添加键值对
3. 如果value的类别是list或zset，则添加key到server和db的ready_keys中
4. 如果开启了集群，则将key保存到slot中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/0cc0c118fcf24fb998027efc6539ff5f.png)

#### dbRandomKey

随机获得键:

1. 设置最大循环100次
2. 死循环从db中找到一个不过期的key
3. 如果已经过期，则把key从db中删除
4. 如果整个实例都设置过期并且是在从机上访问最多循环100次

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/857c46c7da2846ff9302bef62da3dbdb.png)

## 2.4、相关API

### dbSyncDelete

键值对同步删除：

1. 如果过期字典size大于0，则删除过期字典中的key
2. 删除字典中的key
3. 如果服务器开启了集群功能，则调用slotToKeyDel删除slot 和 key 映射表中key记录

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/6627636dd45a407282fc8d432f459ff0.png)

### dbAsyncDelete

键值对异步删除：

1. 如果过期字典size大于0，则删除过期字典中的key
2. unlink 该键在字典中对应的entry
3. 获取entry的val，再预估空间大小
4. 如果空间大于阈值并且值没有其他引用，则添加到懒删除列表中
5. 调用bioCreateBackgroundJob创建后台任务来释放value
6. 调用dictFreeUnlinkedEntry释放key、value和entry
7. 如果服务器开启了集群功能，则调用slotToKeyDel删除slot 和 key 映射表中key记录

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/dea7f6e78d154fa2b0f48b44d0915df0.png)

### emptyDb

清空db:

1. 赋值异步清空
2. 如果dbnum小于-1或者大于16则返回错误
3. 如果dbnum是-1，则做所有数据库的全遍历
4. 否则只遍历dbnum的数据库
5. 记录被删除键的数量
6. 如果是异步清空，则调用emptyDbAsync进行异步清空
7. 否则删除所有键值对，删除所有键的过期时间
8. 如果开启了集群模式，如果是异步清空，则调用slotToKeyFlushAsync移除槽记录
9. 如果是清空所有数据库，则要清空从机数据库
10. 返回被删除键的数量

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/9ecd288db9e7438dbcb56b6d7302dc1e.png)

### selectDb

选择db:

1. 如果id小于0或者大于等于16，则返回错误
2. 将server指定id的地址指向client的db

## 2.5、设计思想和优势

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/f5bcab985bed4418b6656a9079aafacb.png)

1. 使用大量的dict存储键值对
   key和value (key是sds value不同)
   查找方便
2. 键空间操作
   创建、添加、删除、修改、查询
   调用dict的api
3. 懒删除
   dict、expires 同步删除
   free  是可以异步的，提升性能

# 三、Redis主存储

## 3.1、存储相关结构体

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/7f54559642ec4bcc9eb06dfcbeca4cd0.png)

### redisServer：服务器

server.h

```c
struct redisServer {
    /* General */
    pid_t pid;                  /* Main process pid. */
    pthread_t main_thread_id;         /* Main thread id */
    char *configfile;           /* Absolute config file path, or NULL */
    char *executable;           /* Absolute executable file path. */
    char **exec_argv;           /* Executable argv vector (copy). */
    int dynamic_hz;             /* Change hz value depending on # of clients. */
    int config_hz;              /* Configured HZ value. May be different than
                                   the actual 'hz' field value if dynamic-hz
                                   is enabled. */
    mode_t umask;               /* The umask value of the process on startup */
    int hz;                     /* serverCron() calls frequency in hertz */
    int in_fork_child;          /* indication that this is a fork child */
    redisDb *db;
    dict *commands;             /* Command table */
    dict *orig_commands;        /* Command table before command renaming. */
    aeEventLoop *el;
    rax *errors;                /* Errors table */
    redisAtomic unsigned int lruclock; /* Clock for LRU eviction */
    volatile sig_atomic_t shutdown_asap; /* SHUTDOWN needed ASAP */
    int activerehashing;        /* Incremental rehash in serverCron() */
    int active_defrag_running;  /* Active defragmentation running (holds current scan aggressiveness) */
    char *pidfile;              /* PID file path */
    int arch_bits;              /* 32 or 64 depending on sizeof(long) */
    int cronloops;              /* Number of times the cron function run */
    char runid[CONFIG_RUN_ID_SIZE+1];  /* ID always different at every exec. */
    int sentinel_mode;          /* True if this instance is a Sentinel. */
    size_t initial_memory_usage; /* Bytes used after initialization. */
    int always_show_logo;       /* Show logo even for non-stdout logging. */
    int in_eval;                /* Are we inside EVAL? */
    int in_exec;                /* Are we inside EXEC? */
    int propagate_in_transaction;  /* Make sure we don't propagate nested MULTI/EXEC */
    char *ignore_warnings;      /* Config: warnings that should be ignored. */
    int client_pause_in_transaction; /* Was a client pause executed during this Exec? */
    /* Modules */
    dict *moduleapi;            /* Exported core APIs dictionary for modules. */
    dict *sharedapi;            /* Like moduleapi but containing the APIs that
                                   modules share with each other. */
    list *loadmodule_queue;     /* List of modules to load at startup. */
    int module_blocked_pipe[2]; /* Pipe used to awake the event loop if a
                                   client blocked on a module command needs
                                   to be processed. */
    pid_t child_pid;            /* PID of current child */
    int child_type;             /* Type of current child */
    client *module_client;      /* "Fake" client to call Redis from modules */
    /* Networking */
    int port;                   /* TCP listening port */
    int tls_port;               /* TLS listening port */
    int tcp_backlog;            /* TCP listen() backlog */
    char *bindaddr[CONFIG_BINDADDR_MAX]; /* Addresses we should bind to */
    int bindaddr_count;         /* Number of addresses in server.bindaddr[] */
    char *unixsocket;           /* UNIX socket path */
    mode_t unixsocketperm;      /* UNIX socket permission */
    socketFds ipfd;             /* TCP socket file descriptors */
    socketFds tlsfd;            /* TLS socket file descriptors */
    int sofd;                   /* Unix socket file descriptor */
    socketFds cfd;              /* Cluster bus listening socket */
    list *clients;              /* List of active clients */
    list *clients_to_close;     /* Clients to close asynchronously */
    list *clients_pending_write; /* There is to write or install handler. */
    list *clients_pending_read;  /* Client has pending read socket buffers. */
    list *slaves, *monitors;    /* List of slaves and MONITORs */
    client *current_client;     /* Current client executing the command. */
    rax *clients_timeout_table; /* Radix tree for blocked clients timeouts. */
    long fixed_time_expire;     /* If > 0, expire keys against server.mstime. */
    rax *clients_index;         /* Active clients dictionary by client ID. */
    pause_type client_pause_type;      /* True if clients are currently paused */
    list *paused_clients;       /* List of pause clients */
    mstime_t client_pause_end_time;    /* Time when we undo clients_paused */
    char neterr[ANET_ERR_LEN];   /* Error buffer for anet.c */
    dict *migrate_cached_sockets;/* MIGRATE cached sockets */
    redisAtomic uint64_t next_client_id; /* Next client unique ID. Incremental. */
    int protected_mode;         /* Don't accept external connections. */
    int gopher_enabled;         /* If true the server will reply to gopher
                                   queries. Will still serve RESP2 queries. */
    int io_threads_num;         /* Number of IO threads to use. */
    int io_threads_do_reads;    /* Read and parse from IO threads? */
    int io_threads_active;      /* Is IO threads currently active? */
    long long events_processed_while_blocked; /* processEventsWhileBlocked() */

    /* RDB / AOF loading information */
    volatile sig_atomic_t loading; /* We are loading data from disk if true */
    off_t loading_total_bytes;
    off_t loading_rdb_used_mem;
    off_t loading_loaded_bytes;
    time_t loading_start_time;
    off_t loading_process_events_interval_bytes;
    /* Fast pointers to often looked up command */
    struct redisCommand *delCommand, *multiCommand, *lpushCommand,
                        *lpopCommand, *rpopCommand, *zpopminCommand,
                        *zpopmaxCommand, *sremCommand, *execCommand,
                        *expireCommand, *pexpireCommand, *xclaimCommand,
                        *xgroupCommand, *rpoplpushCommand, *lmoveCommand;
    /* Fields used only for stats */
    time_t stat_starttime;          /* Server start time */
    long long stat_numcommands;     /* Number of processed commands */
    long long stat_numconnections;  /* Number of connections received */
    long long stat_expiredkeys;     /* Number of expired keys */
    double stat_expired_stale_perc; /* Percentage of keys probably expired */
    long long stat_expired_time_cap_reached_count; /* Early expire cylce stops.*/
    long long stat_expire_cycle_time_used; /* Cumulative microseconds used. */
    long long stat_evictedkeys;     /* Number of evicted keys (maxmemory) */
    long long stat_keyspace_hits;   /* Number of successful lookups of keys */
    long long stat_keyspace_misses; /* Number of failed lookups of keys */
    long long stat_active_defrag_hits;      /* number of allocations moved */
    long long stat_active_defrag_misses;    /* number of allocations scanned but not moved */
    long long stat_active_defrag_key_hits;  /* number of keys with moved allocations */
    long long stat_active_defrag_key_misses;/* number of keys scanned and not moved */
    long long stat_active_defrag_scanned;   /* number of dictEntries scanned */
    size_t stat_peak_memory;        /* Max used memory record */
    long long stat_fork_time;       /* Time needed to perform latest fork() */
    double stat_fork_rate;          /* Fork rate in GB/sec. */
    long long stat_total_forks;     /* Total count of fork. */
    long long stat_rejected_conn;   /* Clients rejected because of maxclients */
    long long stat_sync_full;       /* Number of full resyncs with slaves. */
    long long stat_sync_partial_ok; /* Number of accepted PSYNC requests. */
    long long stat_sync_partial_err;/* Number of unaccepted PSYNC requests. */
    list *slowlog;                  /* SLOWLOG list of commands */
    long long slowlog_entry_id;     /* SLOWLOG current entry ID */
    long long slowlog_log_slower_than; /* SLOWLOG time limit (to get logged) */
    unsigned long slowlog_max_len;     /* SLOWLOG max number of items logged */
    struct malloc_stats cron_malloc_stats; /* sampled in serverCron(). */
    redisAtomic long long stat_net_input_bytes; /* Bytes read from network. */
    redisAtomic long long stat_net_output_bytes; /* Bytes written to network. */
    size_t stat_current_cow_bytes;  /* Copy on write bytes while child is active. */
    monotime stat_current_cow_updated;  /* Last update time of stat_current_cow_bytes */
    size_t stat_current_save_keys_processed;  /* Processed keys while child is active. */
    size_t stat_current_save_keys_total;  /* Number of keys when child started. */
    size_t stat_rdb_cow_bytes;      /* Copy on write bytes during RDB saving. */
    size_t stat_aof_cow_bytes;      /* Copy on write bytes during AOF rewrite. */
    size_t stat_module_cow_bytes;   /* Copy on write bytes during module fork. */
    double stat_module_progress;   /* Module save progress. */
    uint64_t stat_clients_type_memory[CLIENT_TYPE_COUNT];/* Mem usage by type */
    long long stat_unexpected_error_replies; /* Number of unexpected (aof-loading, replica to master, etc.) error replies */
    long long stat_total_error_replies; /* Total number of issued error replies ( command + rejected errors ) */
    long long stat_dump_payload_sanitizations; /* Number deep dump payloads integrity validations. */
    long long stat_io_reads_processed; /* Number of read events processed by IO / Main threads */
    long long stat_io_writes_processed; /* Number of write events processed by IO / Main threads */
    redisAtomic long long stat_total_reads_processed; /* Total number of read events processed */
    redisAtomic long long stat_total_writes_processed; /* Total number of write events processed */
    /* The following two are used to track instantaneous metrics, like
     * number of operations per second, network traffic. */
    struct {
        long long last_sample_time; /* Timestamp of last sample in ms */
        long long last_sample_count;/* Count in last sample */
        long long samples[STATS_METRIC_SAMPLES];
        int idx;
    } inst_metric[STATS_METRIC_COUNT];
    /* Configuration */
    int verbosity;                  /* Loglevel in redis.conf */
    int maxidletime;                /* Client timeout in seconds */
    int tcpkeepalive;               /* Set SO_KEEPALIVE if non-zero. */
    int active_expire_enabled;      /* Can be disabled for testing purposes. */
    int active_expire_effort;       /* From 1 (default) to 10, active effort. */
    int active_defrag_enabled;
    int sanitize_dump_payload;      /* Enables deep sanitization for ziplist and listpack in RDB and RESTORE. */
    int skip_checksum_validation;   /* Disables checksum validateion for RDB and RESTORE payload. */
    int jemalloc_bg_thread;         /* Enable jemalloc background thread */
    size_t active_defrag_ignore_bytes; /* minimum amount of fragmentation waste to start active defrag */
    int active_defrag_threshold_lower; /* minimum percentage of fragmentation to start active defrag */
    int active_defrag_threshold_upper; /* maximum percentage of fragmentation at which we use maximum effort */
    int active_defrag_cycle_min;       /* minimal effort for defrag in CPU percentage */
    int active_defrag_cycle_max;       /* maximal effort for defrag in CPU percentage */
    unsigned long active_defrag_max_scan_fields; /* maximum number of fields of set/hash/zset/list to process from within the main dict scan */
    size_t client_max_querybuf_len; /* Limit for client query buffer length */
    int dbnum;                      /* Total number of configured DBs */
    int supervised;                 /* 1 if supervised, 0 otherwise. */
    int supervised_mode;            /* See SUPERVISED_* */
    int daemonize;                  /* True if running as a daemon */
    int set_proc_title;             /* True if change proc title */
    char *proc_title_template;      /* Process title template format */
    clientBufferLimitsConfig client_obuf_limits[CLIENT_TYPE_OBUF_COUNT];
    /* AOF persistence */
    int aof_enabled;                /* AOF configuration */
    int aof_state;                  /* AOF_(ON|OFF|WAIT_REWRITE) */
    int aof_fsync;                  /* Kind of fsync() policy */
    char *aof_filename;             /* Name of the AOF file */
    int aof_no_fsync_on_rewrite;    /* Don't fsync if a rewrite is in prog. */
    int aof_rewrite_perc;           /* Rewrite AOF if % growth is > M and... */
    off_t aof_rewrite_min_size;     /* the AOF file is at least N bytes. */
    off_t aof_rewrite_base_size;    /* AOF size on latest startup or rewrite. */
    off_t aof_current_size;         /* AOF current size. */
    off_t aof_fsync_offset;         /* AOF offset which is already synced to disk. */
    int aof_flush_sleep;            /* Micros to sleep before flush. (used by tests) */
    int aof_rewrite_scheduled;      /* Rewrite once BGSAVE terminates. */
    list *aof_rewrite_buf_blocks;   /* Hold changes during an AOF rewrite. */
    sds aof_buf;      /* AOF buffer, written before entering the event loop */
    int aof_fd;       /* File descriptor of currently selected AOF file */
    int aof_selected_db; /* Currently selected DB in AOF */
    time_t aof_flush_postponed_start; /* UNIX time of postponed AOF flush */
    time_t aof_last_fsync;            /* UNIX time of last fsync() */
    time_t aof_rewrite_time_last;   /* Time used by last AOF rewrite run. */
    time_t aof_rewrite_time_start;  /* Current AOF rewrite start time. */
    int aof_lastbgrewrite_status;   /* C_OK or C_ERR */
    unsigned long aof_delayed_fsync;  /* delayed AOF fsync() counter */
    int aof_rewrite_incremental_fsync;/* fsync incrementally while aof rewriting? */
    int rdb_save_incremental_fsync;   /* fsync incrementally while rdb saving? */
    int aof_last_write_status;      /* C_OK or C_ERR */
    int aof_last_write_errno;       /* Valid if aof write/fsync status is ERR */
    int aof_load_truncated;         /* Don't stop on unexpected AOF EOF. */
    int aof_use_rdb_preamble;       /* Use RDB preamble on AOF rewrites. */
    redisAtomic int aof_bio_fsync_status; /* Status of AOF fsync in bio job. */
    redisAtomic int aof_bio_fsync_errno;  /* Errno of AOF fsync in bio job. */
    /* AOF pipes used to communicate between parent and child during rewrite. */
    int aof_pipe_write_data_to_child;
    int aof_pipe_read_data_from_parent;
    int aof_pipe_write_ack_to_parent;
    int aof_pipe_read_ack_from_child;
    int aof_pipe_write_ack_to_child;
    int aof_pipe_read_ack_from_parent;
    int aof_stop_sending_diff;     /* If true stop sending accumulated diffs
                                      to child process. */
    sds aof_child_diff;             /* AOF diff accumulator child side. */
    /* RDB persistence */
    long long dirty;                /* Changes to DB from the last save */
    long long dirty_before_bgsave;  /* Used to restore dirty on failed BGSAVE */
    struct saveparam *saveparams;   /* Save points array for RDB */
    int saveparamslen;              /* Number of saving points */
    char *rdb_filename;             /* Name of RDB file */
    int rdb_compression;            /* Use compression in RDB? */
    int rdb_checksum;               /* Use RDB checksum? */
    int rdb_del_sync_files;         /* Remove RDB files used only for SYNC if
                                       the instance does not use persistence. */
    time_t lastsave;                /* Unix time of last successful save */
    time_t lastbgsave_try;          /* Unix time of last attempted bgsave */
    time_t rdb_save_time_last;      /* Time used by last RDB save run. */
    time_t rdb_save_time_start;     /* Current RDB save start time. */
    int rdb_bgsave_scheduled;       /* BGSAVE when possible if true. */
    int rdb_child_type;             /* Type of save by active child. */
    int lastbgsave_status;          /* C_OK or C_ERR */
    int stop_writes_on_bgsave_err;  /* Don't allow writes if can't BGSAVE */
    int rdb_pipe_read;              /* RDB pipe used to transfer the rdb data */
                                    /* to the parent process in diskless repl. */
    int rdb_child_exit_pipe;        /* Used by the diskless parent allow child exit. */
    connection **rdb_pipe_conns;    /* Connections which are currently the */
    int rdb_pipe_numconns;          /* target of diskless rdb fork child. */
    int rdb_pipe_numconns_writing;  /* Number of rdb conns with pending writes. */
    char *rdb_pipe_buff;            /* In diskless replication, this buffer holds data */
    int rdb_pipe_bufflen;           /* that was read from the the rdb pipe. */
    int rdb_key_save_delay;         /* Delay in microseconds between keys while
                                     * writing the RDB. (for testings). negative
                                     * value means fractions of microsecons (on average). */
    int key_load_delay;             /* Delay in microseconds between keys while
                                     * loading aof or rdb. (for testings). negative
                                     * value means fractions of microsecons (on average). */
    /* Pipe and data structures for child -> parent info sharing. */
    int child_info_pipe[2];         /* Pipe used to write the child_info_data. */
    int child_info_nread;           /* Num of bytes of the last read from pipe */
    /* Propagation of commands in AOF / replication */
    redisOpArray also_propagate;    /* Additional command to propagate. */
    int replication_allowed;        /* Are we allowed to replicate? */
    /* Logging */
    char *logfile;                  /* Path of log file */
    int syslog_enabled;             /* Is syslog enabled? */
    char *syslog_ident;             /* Syslog ident */
    int syslog_facility;            /* Syslog facility */
    int crashlog_enabled;           /* Enable signal handler for crashlog.
                                     * disable for clean core dumps. */
    int memcheck_enabled;           /* Enable memory check on crash. */
    int use_exit_on_panic;          /* Use exit() on panic and assert rather than
                                     * abort(). useful for Valgrind. */
    /* Replication (master) */
    char replid[CONFIG_RUN_ID_SIZE+1];  /* My current replication ID. */
    char replid2[CONFIG_RUN_ID_SIZE+1]; /* replid inherited from master*/
    long long master_repl_offset;   /* My current replication offset */
    long long second_replid_offset; /* Accept offsets up to this for replid2. */
    int slaveseldb;                 /* Last SELECTed DB in replication output */
    int repl_ping_slave_period;     /* Master pings the slave every N seconds */
    char *repl_backlog;             /* Replication backlog for partial syncs */
    long long repl_backlog_size;    /* Backlog circular buffer size */
    long long repl_backlog_histlen; /* Backlog actual data length */
    long long repl_backlog_idx;     /* Backlog circular buffer current offset,
                                       that is the next byte will'll write to.*/
    long long repl_backlog_off;     /* Replication "master offset" of first
                                       byte in the replication backlog buffer.*/
    time_t repl_backlog_time_limit; /* Time without slaves after the backlog
                                       gets released. */
    time_t repl_no_slaves_since;    /* We have no slaves since that time.
                                       Only valid if server.slaves len is 0. */
    int repl_min_slaves_to_write;   /* Min number of slaves to write. */
    int repl_min_slaves_max_lag;    /* Max lag of <count> slaves to write. */
    int repl_good_slaves_count;     /* Number of slaves with lag <= max_lag. */
    int repl_diskless_sync;         /* Master send RDB to slaves sockets directly. */
    int repl_diskless_load;         /* Slave parse RDB directly from the socket.
                                     * see REPL_DISKLESS_LOAD_* enum */
    int repl_diskless_sync_delay;   /* Delay to start a diskless repl BGSAVE. */
    /* Replication (slave) */
    char *masteruser;               /* AUTH with this user and masterauth with master */
    sds masterauth;                 /* AUTH with this password with master */
    char *masterhost;               /* Hostname of master */
    int masterport;                 /* Port of master */
    int repl_timeout;               /* Timeout after N seconds of master idle */
    client *master;     /* Client that is master for this slave */
    client *cached_master; /* Cached master to be reused for PSYNC. */
    int repl_syncio_timeout; /* Timeout for synchronous I/O calls */
    int repl_state;          /* Replication status if the instance is a slave */
    off_t repl_transfer_size; /* Size of RDB to read from master during sync. */
    off_t repl_transfer_read; /* Amount of RDB read from master during sync. */
    off_t repl_transfer_last_fsync_off; /* Offset when we fsync-ed last time. */
    connection *repl_transfer_s;     /* Slave -> Master SYNC connection */
    int repl_transfer_fd;    /* Slave -> Master SYNC temp file descriptor */
    char *repl_transfer_tmpfile; /* Slave-> master SYNC temp file name */
    time_t repl_transfer_lastio; /* Unix time of the latest read, for timeout */
    int repl_serve_stale_data; /* Serve stale data when link is down? */
    int repl_slave_ro;          /* Slave is read only? */
    int repl_slave_ignore_maxmemory;    /* If true slaves do not evict. */
    time_t repl_down_since; /* Unix time at which link with master went down */
    int repl_disable_tcp_nodelay;   /* Disable TCP_NODELAY after SYNC? */
    int slave_priority;             /* Reported in INFO and used by Sentinel. */
    int replica_announced;          /* If true, replica is announced by Sentinel */
    int slave_announce_port;        /* Give the master this listening port. */
    char *slave_announce_ip;        /* Give the master this ip address. */
    /* The following two fields is where we store master PSYNC replid/offset
     * while the PSYNC is in progress. At the end we'll copy the fields into
     * the server->master client structure. */
    char master_replid[CONFIG_RUN_ID_SIZE+1];  /* Master PSYNC runid. */
    long long master_initial_offset;           /* Master PSYNC offset. */
    int repl_slave_lazy_flush;          /* Lazy FLUSHALL before loading DB? */
    /* Replication script cache. */
    dict *repl_scriptcache_dict;        /* SHA1 all slaves are aware of. */
    list *repl_scriptcache_fifo;        /* First in, first out LRU eviction. */
    unsigned int repl_scriptcache_size; /* Max number of elements. */
    /* Synchronous replication. */
    list *clients_waiting_acks;         /* Clients waiting in WAIT command. */
    int get_ack_from_slaves;            /* If true we send REPLCONF GETACK. */
    /* Limits */
    unsigned int maxclients;            /* Max number of simultaneous clients */
    unsigned long long maxmemory;   /* Max number of memory bytes to use */
    int maxmemory_policy;           /* Policy for key eviction */
    int maxmemory_samples;          /* Precision of random sampling */
    int maxmemory_eviction_tenacity;/* Aggressiveness of eviction processing */
    int lfu_log_factor;             /* LFU logarithmic counter factor. */
    int lfu_decay_time;             /* LFU counter decay factor. */
    long long proto_max_bulk_len;   /* Protocol bulk length maximum size. */
    int oom_score_adj_base;         /* Base oom_score_adj value, as observed on startup */
    int oom_score_adj_values[CONFIG_OOM_COUNT];   /* Linux oom_score_adj configuration */
    int oom_score_adj;                            /* If true, oom_score_adj is managed */
    int disable_thp;                              /* If true, disable THP by syscall */
    /* Blocked clients */
    unsigned int blocked_clients;   /* # of clients executing a blocking cmd.*/
    unsigned int blocked_clients_by_type[BLOCKED_NUM];
    list *unblocked_clients; /* list of clients to unblock before next loop */
    list *ready_keys;        /* List of readyList structures for BLPOP & co */
    /* Client side caching. */
    unsigned int tracking_clients;  /* # of clients with tracking enabled.*/
    size_t tracking_table_max_keys; /* Max number of keys in tracking table. */
    /* Sort parameters - qsort_r() is only available under BSD so we
     * have to take this state global, in order to pass it to sortCompare() */
    int sort_desc;
    int sort_alpha;
    int sort_bypattern;
    int sort_store;
    /* Zip structure config, see redis.conf for more information  */
    size_t hash_max_ziplist_entries;
    size_t hash_max_ziplist_value;
    size_t set_max_intset_entries;
    size_t zset_max_ziplist_entries;
    size_t zset_max_ziplist_value;
    size_t hll_sparse_max_bytes;
    size_t stream_node_max_bytes;
    long long stream_node_max_entries;
    /* List parameters */
    int list_max_ziplist_size;
    int list_compress_depth;
    /* time cache */
    redisAtomic time_t unixtime; /* Unix time sampled every cron cycle. */
    time_t timezone;            /* Cached timezone. As set by tzset(). */
    int daylight_active;        /* Currently in daylight saving time. */
    mstime_t mstime;            /* 'unixtime' in milliseconds. */
    ustime_t ustime;            /* 'unixtime' in microseconds. */
    size_t blocking_op_nesting; /* Nesting level of blocking operation, used to reset blocked_last_cron. */
    long long blocked_last_cron; /* Indicate the mstime of the last time we did cron jobs from a blocking operation */
    /* Pubsub */
    dict *pubsub_channels;  /* Map channels to list of subscribed clients */
    dict *pubsub_patterns;  /* A dict of pubsub_patterns */
    int notify_keyspace_events; /* Events to propagate via Pub/Sub. This is an
                                   xor of NOTIFY_... flags. */
    /* Cluster */
    int cluster_enabled;      /* Is cluster enabled? */
    mstime_t cluster_node_timeout; /* Cluster node timeout. */
    char *cluster_configfile; /* Cluster auto-generated config file name. */
    struct clusterState *cluster;  /* State of the cluster */
    int cluster_migration_barrier; /* Cluster replicas migration barrier. */
    int cluster_allow_replica_migration; /* Automatic replica migrations to orphaned masters and from empty masters */
    int cluster_slave_validity_factor; /* Slave max data age for failover. */
    int cluster_require_full_coverage; /* If true, put the cluster down if
                                          there is at least an uncovered slot.*/
    int cluster_slave_no_failover;  /* Prevent slave from starting a failover
                                       if the master is in failure state. */
    char *cluster_announce_ip;  /* IP address to announce on cluster bus. */
    int cluster_announce_port;     /* base port to announce on cluster bus. */
    int cluster_announce_tls_port; /* TLS port to announce on cluster bus. */
    int cluster_announce_bus_port; /* bus port to announce on cluster bus. */
    int cluster_module_flags;      /* Set of flags that Redis modules are able
                                      to set in order to suppress certain
                                      native Redis Cluster features. Check the
                                      REDISMODULE_CLUSTER_FLAG_*. */
    int cluster_allow_reads_when_down; /* Are reads allowed when the cluster
                                        is down? */
    int cluster_config_file_lock_fd;   /* cluster config fd, will be flock */
    /* Scripting */
    lua_State *lua; /* The Lua interpreter. We use just one for all clients */
    client *lua_client;   /* The "fake client" to query Redis from Lua */
    client *lua_caller;   /* The client running EVAL right now, or NULL */
    char* lua_cur_script; /* SHA1 of the script currently running, or NULL */
    dict *lua_scripts;         /* A dictionary of SHA1 -> Lua scripts */
    unsigned long long lua_scripts_mem;  /* Cached scripts' memory + oh */
    mstime_t lua_time_limit;  /* Script timeout in milliseconds */
    monotime lua_time_start;  /* monotonic timer to detect timed-out script */
    mstime_t lua_time_snapshot; /* Snapshot of mstime when script is started */
    int lua_write_dirty;  /* True if a write command was called during the
                             execution of the current script. */
    int lua_random_dirty; /* True if a random command was called during the
                             execution of the current script. */
    int lua_replicate_commands; /* True if we are doing single commands repl. */
    int lua_multi_emitted;/* True if we already propagated MULTI. */
    int lua_repl;         /* Script replication flags for redis.set_repl(). */
    int lua_timedout;     /* True if we reached the time limit for script
                             execution. */
    int lua_kill;         /* Kill the script if true. */
    int lua_always_replicate_commands; /* Default replication type. */
    int lua_oom;          /* OOM detected when script start? */
    /* Lazy free */
    int lazyfree_lazy_eviction;
    int lazyfree_lazy_expire;
    int lazyfree_lazy_server_del;
    int lazyfree_lazy_user_del;
    int lazyfree_lazy_user_flush;
    /* Latency monitor */
    long long latency_monitor_threshold;
    dict *latency_events;
    /* ACLs */
    char *acl_filename;           /* ACL Users file. NULL if not configured. */
    unsigned long acllog_max_len; /* Maximum length of the ACL LOG list. */
    sds requirepass;              /* Remember the cleartext password set with
                                     the old "requirepass" directive for
                                     backward compatibility with Redis <= 5. */
    int acl_pubsub_default;      /* Default ACL pub/sub channels flag */
    /* Assert & bug reporting */
    int watchdog_period;  /* Software watchdog period in ms. 0 = off */
    /* System hardware info */
    size_t system_memory_size;  /* Total memory in system as reported by OS */
    /* TLS Configuration */
    int tls_cluster;
    int tls_replication;
    int tls_auth_clients;
    redisTLSContextConfig tls_ctx_config;
    /* cpu affinity */
    char *server_cpulist; /* cpu affinity list of redis server main/io thread. */
    char *bio_cpulist; /* cpu affinity list of bio thread. */
    char *aof_rewrite_cpulist; /* cpu affinity list of aof rewrite process. */
    char *bgsave_cpulist; /* cpu affinity list of bgsave process. */
    /* Sentinel config */
    struct sentinelConfig *sentinel_config; /* sentinel config to load at startup time. */
    /* Coordinate failover info */
    mstime_t failover_end_time; /* Deadline for failover command. */
    int force_failover; /* If true then failover will be foreced at the
                         * deadline, otherwise failover is aborted. */
    char *target_replica_host; /* Failover target host. If null during a
                                * failover then any replica can be used. */
    int target_replica_port; /* Failover target port */
    int failover_state; /* Failover state */
};
```

* char *configfile：配置文件绝对路径
* int hz：serverCron的执行频次
* int dbnum：数据库的数量
* redisDb *db：数据库数组
* dict *commands：命令字典
* aeEventLoop *el：事件循环
* int port：服务器监听端口
* char *bindaddr[]：绑定的ip地址
* int ipfd[]：针对ip地址创建的socket文件描述符
* list *clients：当前连接到redis服务器的所有客户端
* char *unixsocket：socket路径
* int maxidletime：客户端超时时间
* int aof_state：aof开启标志

### redisDb：数据库

### redisObject：值的封装

### redisCommand：命令封装

```
struct redisCommand {
    char *name;
    redisCommandProc *proc;
    int arity;
    char *sflags;   /* Flags as string representation, one char per flag. */
    uint64_t flags; /* The actual flags, obtained from the 'sflags' field. */
    /* Use a function to determine keys arguments in a command line.
     * Used for Redis Cluster redirect. */
    redisGetKeysProc *getkeys_proc;
    /* What keys should be loaded in background when calling this command? */
    int firstkey; /* The first argument that's a key (0 = no keys) */
    int lastkey;  /* The last argument that's a key */
    int keystep;  /* The step between first and last key */
    long long microseconds, calls, rejected_calls, failed_calls;
    int id;     /* Command ID. This is a progressive ID starting from 0 that
                   is assigned at runtime, and is used in order to check
                   ACLs. A connection is able to execute a given command if
                   the user associated to the connection has this command
                   bit set in the bitmap of allowed commands. */
};
```

* char *name：命令名
* redisCommandProc *proc：命令执行函数
* int arity：参数个数（负数表示参数个数>=N）
* char *sflags：命令的sflags属性字符串
* int flags：从sflags获得的整数值
* redisGetKeysProc *getkeys_proc：获取key参数的可选函数
* int firstkey：第一个key的位置（0表示没有key）
* int lastkey：最后一个key的位置
* int keystep：第一个和最后一个key之间的跨步(k1 v1 , k2 v2 , k3 v3)
* long long microseconds, calls, rejected_calls, failed_calls：命令从服务启动到现在的执行时间，执行次数，拒绝次数，执行失败次数
* int id：命令id

#### redisCommandTable[]

server.c

Redis 所有命令的执行函数，保存在redisCommandTable[]中

```c
struct redisCommand redisCommandTable[] = {
{"set",setCommand,-3,
     "write use-memory @string",
     0,NULL,1,1,1,0,0,0},
{"get",getCommand,2,
     "read-only fast @string",
     0,NULL,1,1,1,0,0,0},
.....
}
```

### client：客户端

```c
typedef struct client {
    uint64_t id;            /* Client incremental unique ID. */
    connection *conn;
    int resp;               /* RESP protocol version. Can be 2 or 3. */
    redisDb *db;            /* Pointer to currently SELECTed DB. */
    robj *name;             /* As set by CLIENT SETNAME. */
    sds querybuf;           /* Buffer we use to accumulate client queries. */
    size_t qb_pos;          /* The position we have read in querybuf. */
    sds pending_querybuf;   /* If this client is flagged as master, this buffer
                               represents the yet not applied portion of the
                               replication stream that we are receiving from
                               the master. */
    size_t querybuf_peak;   /* Recent (100ms or more) peak of querybuf size. */
    int argc;               /* Num of arguments of current command. */
    robj **argv;            /* Arguments of current command. */
    int original_argc;      /* Num of arguments of original command if arguments were rewritten. */
    robj **original_argv;   /* Arguments of original command if arguments were rewritten. */
    size_t argv_len_sum;    /* Sum of lengths of objects in argv list. */
    struct redisCommand *cmd, *lastcmd;  /* Last command executed. */
    user *user;             /* User associated with this connection. If the
                               user is set to NULL the connection can do
                               anything (admin). */
    int reqtype;            /* Request protocol type: PROTO_REQ_* */
    int multibulklen;       /* Number of multi bulk arguments left to read. */
    long bulklen;           /* Length of bulk argument in multi bulk request. */
    list *reply;            /* List of reply objects to send to the client. */
    unsigned long long reply_bytes; /* Tot bytes of objects in reply list. */
    size_t sentlen;         /* Amount of bytes already sent in the current
                               buffer or object being sent. */
    time_t ctime;           /* Client creation time. */
    long duration;          /* Current command duration. Used for measuring latency of blocking/non-blocking cmds */
    time_t lastinteraction; /* Time of the last interaction, used for timeout */
    time_t obuf_soft_limit_reached_time;
    uint64_t flags;         /* Client flags: CLIENT_* macros. */
    int authenticated;      /* Needed when the default user requires auth. */
    int replstate;          /* Replication state if this is a slave. */
    int repl_put_online_on_ack; /* Install slave write handler on first ACK. */
    int repldbfd;           /* Replication DB file descriptor. */
    off_t repldboff;        /* Replication DB file offset. */
    off_t repldbsize;       /* Replication DB file size. */
    sds replpreamble;       /* Replication DB preamble. */
    long long read_reploff; /* Read replication offset if this is a master. */
    long long reploff;      /* Applied replication offset if this is a master. */
    long long repl_ack_off; /* Replication ack offset, if this is a slave. */
    long long repl_ack_time;/* Replication ack time, if this is a slave. */
    long long repl_last_partial_write; /* The last time the server did a partial write from the RDB child pipe to this replica  */
    long long psync_initial_offset; /* FULLRESYNC reply offset other slaves
                                       copying this slave output buffer
                                       should use. */
    char replid[CONFIG_RUN_ID_SIZE+1]; /* Master replication ID (if master). */
    int slave_listening_port; /* As configured with: REPLCONF listening-port */
    char *slave_addr;       /* Optionally given by REPLCONF ip-address */
    int slave_capa;         /* Slave capabilities: SLAVE_CAPA_* bitwise OR. */
    multiState mstate;      /* MULTI/EXEC state */
    int btype;              /* Type of blocking op if CLIENT_BLOCKED. */
    blockingState bpop;     /* blocking state */
    long long woff;         /* Last write global replication offset. */
    list *watched_keys;     /* Keys WATCHED for MULTI/EXEC CAS */
    dict *pubsub_channels;  /* channels a client is interested in (SUBSCRIBE) */
    list *pubsub_patterns;  /* patterns a client is interested in (SUBSCRIBE) */
    sds peerid;             /* Cached peer ID. */
    sds sockname;           /* Cached connection target address. */
    listNode *client_list_node; /* list node in client list */
    listNode *paused_list_node; /* list node within the pause list */
    RedisModuleUserChangedFunc auth_callback; /* Module callback to execute
                                               * when the authenticated user
                                               * changes. */
    void *auth_callback_privdata; /* Private data that is passed when the auth
                                   * changed callback is executed. Opaque for
                                   * Redis Core. */
    void *auth_module;      /* The module that owns the callback, which is used
                             * to disconnect the client if the module is
                             * unloaded for cleanup. Opaque for Redis Core.*/

    /* If this client is in tracking mode and this field is non zero,
     * invalidation messages for keys fetched by this client will be send to
     * the specified client ID. */
    uint64_t client_tracking_redirection;
    rax *client_tracking_prefixes; /* A dictionary of prefixes we are already
                                      subscribed to in BCAST mode, in the
                                      context of client side caching. */
    /* In clientsCronTrackClientsMemUsage() we track the memory usage of
     * each client and add it to the sum of all the clients of a given type,
     * however we need to remember what was the old contribution of each
     * client, and in which categoty the client was, in order to remove it
     * before adding it the new value. */
    uint64_t client_cron_last_memory_usage;
    int      client_cron_last_memory_type;
    /* Response buffer */
    int bufpos;
    char buf[PROTO_REPLY_CHUNK_BYTES];
}
```

* uint64_t id：客户端唯一ID
* int fd：客户端socket的文件描述符
* redisDb *db：客户端选择的数据库对象
* robj *name：客户端名称
* time_t lastinteraction：客户端上次与服务器交互的时间
* sds querybuf：输入缓冲区
* int argc：参数个数
* robj **argv：参数内容
* redisCommand *cmd：待执行的客户端命令
* list *reply：输出链表
* unsigned long long reply_bytes：输出链表中所有节点的存储空间总和
* size_t sentlen：已返回给客户端的字节数
* char buf[]:输出缓冲区

## 3.2、存储流程

数据存储流程就是命令执行流程(命令指的是数据存取的命令)

### 服务器初始化

1. initServer
2. 创建eventloop
3. 注册周期时间事件处理器
4. 在监听的socket上创建文件事件处理器
5. 监听socket建立连接的事件，处理函数为acceptTcpHandler

networking.c : readQueryFromClient

### 命令接收

1. 当客户端向 Redis 建立 socket时，aeEventLoop 会调用 acceptTcpHandler 处理函数
2. 服务器会为每个链接创建一个 Client 对象
3. 创建相应文件事件来监听socket的可读事件
4. 当客户端通过 socket 发送来数据后，Redis 会调用 readQueryFromClient 方法
5. readQueryFromClient调用processInputBuffer进行命令解析

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/9f547df554504157b798d78036e11e58.png)

### 命令解析

1. processInputBuffer根据命令类型分别调用processInlineBuffer和processMultibulkBuffer进行命令解析
2. 然后调用processCommandAndResetClient 方法来执行命令
3. 调用processCommand执行具体的命令

### 命令执行(processCommand)

1. 调用lookupCommand，从命令字典查找指定命令(在initServerConfig中创建commands)
2. 找到命令后，进行参数校验
3. 设置命令类型
4. 检查用户认证
5. 检查访问控制列表
6. 如果是开启事务，则命令入队列
7. 否则调用call函数进行命令执行
8. 如果有监视器 monitor，则需要将命令发送给监视器
9. 调用 redisCommand 的proc 方法，调用命令处理函数，执行对应具体的命令逻辑
10. 执行成功后，如果是主机客户端，还需要更新同步偏移量 reploff 属性
11. 重置 client，让client可以接收下一条命令

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/d966f61689994719b68b7d16784bf54e.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/9b077050e8154b15a99d8e2a7353d867.png)


## 3.3、相关命令解析

### setCommand

```
 SET key value [NX] [XX] [EX <seconds>] [PX <milliseconds>]
```

1. 循环参数拆分
2. 如果是nx或NX，则flag是set_nx
3. 如果是xx或XX，则flag是set_xx
4. 如果是ex或EX，则flag是set_ex，赋值unit为秒，并获得过期时间
5. 如果是px或PX，则flag是set_px，赋值unit为毫秒，并获得过期时间，否则返回语法错误
6. 将value尝试转换为数字
7. 调用setGenericCommand，并传递参数

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/a7991b57068b4af0aa415725e6ab1a84.png)

#### setGenericCommand

1. 如果有过期时间，则获得过期时间的毫秒数
2. 如果时间是秒，则将获得的数*1000
3. 如果是nx，并且key已经存在则客户端返回失败
4. 或者如果是xx，并且key不存在则客户端返回失败
5. 将key和value添加到数据字典中
6. 如果有过期时间，则将过期key和过期时间添加到过期字典中
7. 发送键空间通知
8. 客户端返回成功

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/3c7b7dd12d064f3f854db3a22f324474.png)

### getCommand

```
GET key
```

调用getGenericCommand

#### getGenericCommand

1. 调用lookupKeyReadOrReply，查找key对应的值
2. 如果找不到，则返回
3. 找到了，判断值类型，如果是String，则返回
4. 否则调用addReplyBulk块应答

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/9ed9f951da064077956fb46b1502a556.png)

### 3.4、设计思想和优势

1. 数据存储结构体

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/5e1a1877444b4783803bebd758b4307b.png)
2. 数据存储流程（命令处理流程）

   服务器初始化--> 命令表初始化---->命令接收---->命令解析--->命令执行--->应答
3. 统一的命令处理模板

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1666149091069/e66b6ed6c9464ab3ad4da586cdc6d78e.png)
