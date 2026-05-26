# Java 线上问题排查手册：CPU / 内存 / GC / 死锁 / 慢接口

> 本文档持续更新，后续相关提问也会追加在文末。
>
> 每个问题统一按 **现象 → 排查链路 → 原因 → 解决方案** 四段式展开。

---

## 一、CPU 100% 怎么排查？

### 1.1 现象

- 监控告警：单核或整机 CPU 持续 90%+，业务线程响应变慢
- `top` 看到某个 Java 进程 `%CPU` 远超 100%（多核累加）
- 接口 RT 普遍抬升，但**不一定**伴随报错

### 1.2 排查链路

```
top                          → 找占 CPU 高的 Java 进程 PID
  │
  ▼
top -Hp <PID>                → 找进程内占 CPU 高的线程 TID
  │
  ▼
printf "%x\n" <TID>          → 十进制 TID 转十六进制 nid
  │
  ▼
jstack <PID> | grep -A 30 "nid=0x<hex>"   → 定位线程在执行哪段代码
```

完整命令：

```bash
top                                  # 大写 P 按 CPU 排序，记下 PID
top -Hp 12345                        # H 看线程，再按 P 排序，记下 TID
printf "%x\n" 12378                  # 12378 → 305a
jstack 12345 > jstack.log
grep -A 30 "nid=0x305a" jstack.log   # 看这个线程的栈
```

进阶（Arthas）：

```bash
[arthas]$ thread -n 3                # CPU Top 3 线程及栈
[arthas]$ profiler start; sleep 30; profiler stop --format html   # 火焰图
```

### 1.3 原因

| 线程栈特征 | 根因 |
|---|---|
| 单线程栈停在业务方法 | 死循环 / 递归无终止 |
| 栈停在正则 `Pattern.matcher` | **正则灾难性回溯** |
| 大量 `GC task` / `VM Thread` 高 | **GC 风暴**，转去看 [Full GC](#五full-gc-频繁怎么排查) |
| `C2 CompilerThread` 高 | JIT 编译中，启动期短暂正常 |
| 多业务线程同时高 | 大量序列化 / 加解密 / 压缩 / 复杂计算 |
| `top` 看 `sy` 高、`us` 低 | 频繁系统调用：大量 IO / 锁 / 上下文切换 |

### 1.4 解决方案

- **死循环 / 递归**：修复终止条件，加循环次数上限保护
- **正则回溯**：避免嵌套量词 `(a+)+`，必要时设超时（如 `Pattern.compile` + 协程超时）
- **GC 风暴**：转去看 Full GC 排查，调整堆大小 / GC 算法
- **CPU 密集计算**：移到独立线程池避免影响 IO 线程；考虑下沉到异步任务
- **应急**：限流、扩容、必要时重启（重启前先 jstack + jmap 留现场）

---

## 二、内存 OOM 怎么排查？

### 2.1 现象

- 日志中 `java.lang.OutOfMemoryError: ...`
- 进程突然消失（被 Linux OOM Killer 杀掉，`dmesg | grep -i kill` 可证实）
- 监控看到老年代或 Metaspace 持续接近 100%

### 2.2 排查链路

**第一步：看 OOM 后面的关键词，决定排查方向**

| OOM 类型 | 排查方向 |
|---|---|
| `Java heap space` | 堆 dump → MAT 分析 |
| `GC overhead limit exceeded` | 同上，98% 时间在 GC |
| `Metaspace` / `PermGen` | 类加载器泄漏（反射、动态代理、热部署） |
| `Direct buffer memory` | Netty / NIO `allocateDirect` 泄漏 |
| `unable to create new native thread` | 线程泄漏或 `ulimit -u` 太小 |
| `Requested array size exceeds VM limit` | 一次性申请超大数组 |
| 被 OOM Killer 杀（无 Java 异常） | RSS 超物理内存，看 dmesg |

**第二步：拿现场**

```bash
# 启动参数（务必提前加）
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/data/dump/

# 没加的话事后手动 dump
jmap -dump:format=b,file=heap.hprof <PID>

# 轻量看对象分布
jmap -histo:live <PID> | head -30
```

**第三步：离线分析**

- **MAT（Eclipse Memory Analyzer）**：Leak Suspects 自动给出可疑泄漏点
- 看 **Dominator Tree**：哪些对象占了 80% 以上堆
- 看 **GC Roots 引用链**：为什么对象回收不掉

### 2.3 原因

| 泄漏模式 | 典型表现 |
|---|---|
| 静态 `Map` 缓存无淘汰 | 某 HashMap 持有几百万 entry |
| `ThreadLocal` 没 `remove` + 线程池 | Thread → ThreadLocalMap → 业务大对象链 |
| 监听器 / 回调注册了不反注册 | 大量 EventListener |
| 一次性查全表 | List 包含百万条业务对象 |
| 类加载器泄漏 | Metaspace 持续增长，类数飙升 |
| 大对象（图片 / 长字符串 / 大数组） | dump 中单对象占比极高 |

### 2.4 解决方案

- **静态缓存**：换 Caffeine / Guava Cache，设大小上限或过期
- **ThreadLocal**：`try { ... } finally { tl.remove(); }`
- **监听器**：成对 `addListener` / `removeListener`
- **大查询**：分页 / 游标 / 流式 `ResultSet`
- **Metaspace**：避免反射热部署生成大量类；调 `-XX:MaxMetaspaceSize`
- **应急**：先重启恢复，再用现场 dump 定位

---

## 三、接口突然变慢怎么排查？

### 3.1 现象

- 监控 RT p99 飙升，QPS 不一定下降
- 部分接口超时、部分接口正常
- 客户端报 `Read timed out` / `504`

### 3.2 排查链路

**先分层定位**：从外到内，每一层都看监控

```
客户端 → 网关/LB → 应用 → 中间件(DB/Redis/MQ) → 下游服务
   ①        ②       ③          ④                  ⑤
```

| 监控指标 | 看什么 |
|---|---|
| RT p99、错误率 | 全部慢还是个别接口 |
| QPS | 是否流量突增 |
| 应用 CPU / GC | 自身问题还是外部问题 |
| DB / Redis RT | 中间件是否慢 |
| 网络重传 / 丢包 | 链路问题 |

**应用层命令链**：

```bash
# ① GC 是否拖慢
jstat -gcutil <PID> 1000 10

# ② 线程状态分布
jstack <PID> | grep "java.lang.Thread.State" | sort | uniq -c
#   - 大量 BLOCKED          → 锁竞争
#   - 大量 socketRead0       → 下游慢
#   - 大量 WAITING 同一锁    → 锁瓶颈

# ③ Arthas trace 找慢方法
[arthas]$ trace com.xxx.Service slowMethod '#cost > 200'
```

### 3.3 原因

| 根因 | 典型证据 |
|---|---|
| Full GC 频繁 | RT 周期性飙高，与 GC 日志对齐 |
| DB 慢 SQL | 线程停在 `socketRead`（等 DB 回包） |
| 下游服务慢 | 线程停在 RPC / HttpClient |
| 锁竞争 | 大量 BLOCKED 线程 |
| 连接池打满 | 报 `Cannot get a connection` |
| 缓存击穿 | 流量打到 DB，DB RT 飙升 |
| 新发布 | 时间点对得上发布，**回滚验证最快** |
| 大对象序列化 | CPU 高 + 单响应数据量大 |

### 3.4 解决方案

- **GC 引起**：见 Full GC 章节
- **慢 SQL**：加索引 / 改写 SQL / 拆分大事务
- **下游慢**：加超时、加熔断、并行调用、本地缓存
- **锁竞争**：缩小同步块；用 `ConcurrentHashMap`、`StampedLock`、CAS 替代
- **连接池满**：扩容连接数 / 缩短持有时间 / 看有没有泄漏未归还
- **缓存击穿**：互斥重建 + 逻辑过期；热点 key 永不过期
- **应急**：限流 + 降级，先保活；回滚最快

---

## 四、死锁怎么排查？

### 4.1 现象

- 部分线程长时间 `BLOCKED`，永远不释放
- 接口部分超时（涉及死锁资源的请求），整体未挂
- jstack 输出含 `Found one Java-level deadlock`

### 4.2 排查链路

**JStack 自带死锁检测**（一键定位）：

```bash
jstack <PID> | grep -A 50 "Found.*Java-level deadlock"
```

典型输出：

```
Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x... (a java.lang.Object),
  which is held by "Thread-0"
"Thread-0":
  waiting to lock monitor 0x... (a java.lang.Object),
  which is held by "Thread-1"
```

**其他工具**：

| 工具 | 用法 |
|---|---|
| `jconsole` | 线程 Tab → "检测死锁"按钮 |
| `jvisualvm` | 线程 Tab，红色提示 |
| Arthas | `thread -b` 显示阻塞他人的锁持有者 |

**数据库死锁**：

```sql
SHOW ENGINE INNODB STATUS;       -- 看 LATEST DETECTED DEADLOCK 段
```

### 4.3 原因

死锁四要素（同时满足才会发生）：

1. **互斥**：资源不可共享
2. **占有且等待**：持有锁的同时等待新锁
3. **不可剥夺**：锁不能被强制剥夺
4. **循环等待**：形成环路

常见场景：

- 两个线程以**相反顺序**申请两把锁
- 数据库两个事务以相反顺序更新两行（InnoDB 行锁死锁）
- 分布式锁未设超时，A 持锁挂掉 → B 永远等
- 锁升级：读锁 → 写锁互相等待（典型如 ReentrantReadWriteLock 升级）

### 4.4 解决方案

| 手段 | 破坏的条件 |
|---|---|
| **所有锁按全局固定顺序申请** | 循环等待 |
| `tryLock(timeout)` 超时放弃 | 占有且等待 |
| 一次性申请所有锁（不行就全部释放） | 占有且等待 |
| 用无锁结构（CAS / `ConcurrentHashMap`） | 互斥 |
| 分布式锁加 TTL + 看门狗续约 | 不可剥夺 |
| 数据库事务统一按主键升序更新 | 循环等待 |

> **应急**：死锁线程通常无法自愈，**只能重启**；事后必须改代码。

---

## 五、Full GC 频繁怎么排查？

### 5.1 现象

- 监控老年代占用曲线锯齿状高频抖动
- RT 周期性尖刺（Full GC STW 期间）
- GC 日志中 `Full GC` 频繁出现
- 严重时伴随 OOM

### 5.2 排查链路

**第一步：确认频率**

```bash
jstat -gcutil <PID> 1000

#  S0    S1    E     O     M    YGC   YGCT   FGC   FGCT
#  ...                            ↑              ↑
#                            Young GC 次数  Full GC 次数

# 完整 GC 日志（强烈建议启动加上）
-Xlog:gc*:file=gc.log:time,level,tags    # JDK 9+
-Xloggc:gc.log -XX:+PrintGCDetails        # JDK 8
```

判断标准：

- Full GC 间隔 < 1 小时 → 偏频繁
- Full GC 间隔 < 10 分钟 → 严重
- Full GC 后老年代占比仍 > 70% → **内存泄漏**

**第二步：看触发原因**

```bash
grep "Full GC" gc.log | head
# Ergonomics       → 老年代不够
# Metadata GC      → Metaspace 不够
# System.gc()      → 显式调用
# Allocation Failure → 晋升失败
```

**第三步：抓堆现场**

```bash
jmap -histo:live <PID> | head -30        # 看对象分布
jmap -dump:format=b,file=heap.hprof <PID>  # MAT 离线分析
```

### 5.3 原因

| 原因 | 说明 |
|---|---|
| **老年代空间不足**（最常见） | 内存泄漏 / 缓存膨胀 / 大对象 |
| **Metaspace 不足** | 反射 / 动态代理 / 热部署产类太多 |
| **System.gc() 显式调用** | RMI / 某些框架 / DirectByteBuffer 触发 |
| **CMS Concurrent Mode Failure** | 并发回收赶不上分配速度，退化为 Serial Old |
| **CMS Promotion Failed** | 老年代碎片，晋升时无连续空间 |
| **大对象直接进老年代** | `PretenureSizeThreshold` 不当 |
| **晋升年龄过低** | 短命对象进了老年代 |
| **G1 Humongous 过多** | 大对象 ≥ Region/2 |

### 5.4 解决方案

- **泄漏 / 缓存**：见 OOM / 内存泄漏章节
- **Metaspace**：调 `-XX:MaxMetaspaceSize`；排查类加载器泄漏
- **System.gc()**：加 `-XX:+DisableExplicitGC` 或 `+ExplicitGCInvokesConcurrent`
- **CMS Concurrent Mode Failure**：调 `-XX:CMSInitiatingOccupancyFraction` 让 CMS 更早启动
- **CMS 碎片**：定期重启 / 换 G1
- **G1 Humongous**：调大 Region size（`-XX:G1HeapRegionSize`）让大对象不再 Humongous
- **架构层**：换 G1 / ZGC / Shenandoah，停顿可控

---

## 六、线程池打满 / 接口全部超时怎么排查？

### 6.1 现象

- 接口大面积超时 / `RejectedExecutionException`
- 请求在客户端一直 pending 不返回
- 线程数监控曲线短时间内飙升后持平
- Tomcat `http-nio-exec-*` 线程全部占满

### 6.2 排查链路

```bash
# ① 看线程总数
jstack <PID> | grep "java.lang.Thread.State" | wc -l

# ② 按线程池前缀统计
jstack <PID> | grep "^\"" | awk -F'-' '{print $1}' | sort | uniq -c | sort -rn

# ③ 看线程在做什么（80% 都停在同一栈就是答案）
jstack <PID> > t.log
grep -A 20 "pool-1-thread-" t.log | head -100

# ④ Tomcat 实时指标
curl localhost:8080/actuator/metrics/tomcat.threads.busy
curl localhost:8080/actuator/metrics/tomcat.threads.config.max
```

Tomcat 线程模型：

```
maxThreads     200    ← 工作线程上限
acceptCount    100    ← OS 半连接队列
maxConnections 10000  ← 连接上限
```

工作线程满 → 进入 acceptCount 队列；队列也满 → 新连接被 OS RST。

### 6.3 原因

| 根因 | 证据 |
|---|---|
| 下游接口慢 / 卡死 | 大量线程停在 `socketRead0` / RPC |
| DB 慢 SQL / 锁等待 | 栈停在 `getConnection` / `executeQuery` |
| Redis 阻塞 | 栈停在 Jedis/Lettuce read |
| 死锁 | 见死锁章节 |
| 任务队列堆积 | `queue.size()` 持续增长 |
| 流量突增 | QPS 监控异常 |
| 线程泄漏 | 线程数单调增长 |

### 6.4 解决方案

- **下游慢**：加超时 + 熔断（Sentinel / Resilience4j）；隔离不同下游用独立线程池
- **DB / Redis 慢**：见对应章节
- **死锁**：见死锁章节
- **流量突增**：限流（令牌桶 / 漏桶）+ 扩容
- **线程泄漏**：审查 `Executors.newFixedThreadPool` 是否被反复 new
- **线程池配置**：**禁用 `Executors`**，统一用 `ThreadPoolExecutor` 显式设核心数、队列、拒绝策略
- **应急顺序**：限流降级 → 扩容 → 重启（重启前先 jstack + jmap）→ 回滚

---

## 七、内存泄漏（不一定 OOM）怎么排查？

### 7.1 现象

- 老年代占用曲线**单调上升**（每次 Full GC 后底部水位逐渐抬高）
- Full GC 越来越频繁
- 还没爆 OOM，但已经处于亚健康状态

### 7.2 排查链路

```bash
# ① 周期性抓 histo 对比对象数变化
jmap -histo:live <PID> > h1.txt
sleep 600
jmap -histo:live <PID> > h2.txt
diff h1.txt h2.txt | head    # 看哪些类实例数在增长

# ② 重点泄漏类找到后，dump 看引用链
jmap -dump:format=b,file=heap.hprof <PID>
#   MAT → Path to GC Roots → 看是谁持有它

# ③ 趋势对比图（监控）
#   把"Full GC 后老年代占用"画成时间序列，单调上升 = 泄漏
```

### 7.3 原因

与 OOM 章节一致：静态缓存、ThreadLocal、监听器、连接、类加载器等。

### 7.4 解决方案

- 越早发现越容易修：在亚健康期就 dump，比 OOM 后排查现场完整
- 修复后做对照实验：压测前后老年代曲线对比，确认底部水位不再抬升
- 长期：监控加 **"Full GC 后老年代占用率"** 告警，提前预警

---

## 八、频繁 Young GC 怎么排查？

### 8.1 现象

- `jstat` 看 YGC 列每秒增长 1+
- 接口 RT 周期性小幅抖动（每次 Young GC 几十毫秒）
- 单次 Young GC 不长，但**频率累加成大停顿**

### 8.2 排查链路

```bash
# 看 GC 频率
jstat -gcutil <PID> 1000

# Arthas 火焰图，分配视角
[arthas]$ profiler start --event alloc
[arthas]$ profiler stop --format html
# 火焰图中"宽柱"就是分配热点方法
```

### 8.3 原因

- **新生代太小**：Eden 一会儿就满
- **对象分配过快**：频繁 new 短命对象（大 String、流式调用、装箱）
- **TLAB 不够**：高并发线程多时

### 8.4 解决方案

- 调大新生代 `-Xmn` 或调整 `-XX:NewRatio`
- 代码层减少短命对象：复用 StringBuilder、避免装箱、慎用 `stream().collect()` 链
- 高分配点改为对象池（如 Netty 的 PooledByteBufAllocator）

---

## 九、磁盘 IO 高 / 服务卡顿怎么排查？

### 9.1 现象

- 整机 load 高，但 CPU 不高
- 接口响应慢，业务线程多停在 IO 调用
- `top` 看到 `wa`（IO wait）列偏高

### 9.2 排查链路

```bash
# ① 整体 IO
iostat -x 1
#   %util 接近 100% → 磁盘饱和
#   await 高         → IO 延迟大

# ② 哪个进程在读写
iotop -oP
pidstat -d 1

# ③ Java 进程内哪个线程
top -Hp <PID>           # 找 IO 高的线程
jstack <PID> | ...      # 同 CPU 排查链路

# ④ 是否在 swap
vmstat 1                # si / so 列非 0 = 在 swap
```

### 9.3 原因

- 日志**同步刷盘**（Logback 默认 `immediateFlush=true` 高并发是灾难）
- 大量小文件读写
- 业务机和 DB 共用磁盘
- 操作系统 swap 频繁

### 9.4 解决方案

- 日志改异步：`AsyncAppender` / `LMAX Disruptor`
- 合并小文件 / 批量写
- DB 与业务机分离
- 关闭 swap 或调小 `vm.swappiness`
- 磁盘升级 SSD / NVMe

---

## 十、网络问题（连接超时 / RST / 大量 CLOSE_WAIT）怎么排查？

### 10.1 现象

- 客户端 `Connection refused` / `Connection reset` / `Read timed out`
- `netstat` 看 `CLOSE_WAIT` / `TIME_WAIT` 数量异常
- 接口偶发超时但 DB / 应用监控正常

### 10.2 排查链路

```bash
# 连接状态分布
ss -ant state all | awk '{print $1}' | sort | uniq -c
netstat -ant | awk '{print $6}' | sort | uniq -c

# 丢包重传
netstat -s | grep -i retrans
ss -i | grep retrans

# 抓包
tcpdump -i any -nn host 1.2.3.4 -w cap.pcap
#  Wireshark 打开看握手 / RST / 重传
```

### 10.3 原因

| 状态 | 含义 / 根因 |
|---|---|
| **CLOSE_WAIT 堆积** | 应用层漏调 `close()`（连接池配置不当 / 异常路径未 finally 释放） |
| **TIME_WAIT 大量** | 主动关闭方正常状态，2MSL 后消失；只有端口耗尽时才需处理 |
| **SYN_RECV 大量** | 半连接队列堆积，可能 SYN flood 或 `backlog` 太小 |
| **ESTABLISHED 飙升** | 连接泄漏，没用连接池 |
| **Connection refused** | 对端端口没监听 / 全连接队列满 |
| **Connection reset** | 对端进程崩 / 防火墙 / NAT 超时 |

### 10.4 解决方案

- **CLOSE_WAIT**：审查代码，所有连接 `try-with-resources` 或 `finally close()`
- **TIME_WAIT**：业务端用长连接 / 连接池；服务端开 `tcp_tw_reuse`（**不要开 tcp_tw_recycle**，NAT 环境会丢包）
- **SYN_RECV**：调大 `net.core.somaxconn` 和应用 `backlog`
- **连接泄漏**：连接池配监控（active / idle），观察是否持续增长
- **抓包**：定位是握手失败、RST、还是重传，再决策

---

## 十一、服务雪崩 / 级联失败怎么排查？

### 11.1 现象

- 一个下游服务慢，导致调用它的上游线程占满
- 链路上每一跳依次变慢、不可用
- 监控大盘看到错误率从下游往上游"扩散"

### 11.2 排查链路

```
下游 A 慢 → 上游 B 线程被占满 → B 对外接口慢
        → 调 B 的 C 线程也被占满 → C 也挂
        → 整条链路全部不可用
```

排查工具：

- 分布式链路追踪：SkyWalking / Zipkin / Jaeger，找到**最初慢的那一跳**
- 各节点 jstack：看线程停在调用哪个下游
- 监控大盘：按时间线看哪个服务最先报错

### 11.3 原因

- 下游接口**没有超时**（最常见，单点慢拖垮全链路）
- 没有熔断 / 限流
- 没有资源隔离（同一线程池调用所有下游）
- 重试雪崩（下游慢 → 上游重试 → 流量翻倍 → 下游更慢）

### 11.4 解决方案

| 阶段 | 动作 |
|---|---|
| **事中应急** | 限流、降级、熔断；摘掉故障下游 |
| **事后排查** | 链路追踪定位最初故障点 |
| **事前预防** | Sentinel / Resilience4j 配熔断与资源隔离；**所有 RPC/HTTP/DB/Redis 必须设超时**；下游隔离线程池；重试用指数退避并设上限 |

> **核心一句话**：没有超时的远程调用是雪崩之源。

---

## 十二、慢 SQL 怎么排查？

### 12.1 现象

- 接口慢，应用栈停在 `executeQuery` / `socketRead`
- DB CPU / IO 飙高
- 慢日志中有大量 query

### 12.2 排查链路

```sql
-- ① 正在执行的 SQL
SHOW FULL PROCESSLIST;
SELECT * FROM information_schema.PROCESSLIST WHERE TIME > 10;

-- ② 锁等待
SELECT * FROM information_schema.INNODB_TRX;          -- 当前事务
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
SHOW ENGINE INNODB STATUS;                            -- 死锁信息

-- ③ 慢日志
SHOW VARIABLES LIKE 'slow_query_log%';
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;

-- ④ 看执行计划
EXPLAIN SELECT ...;
--   type=ALL                          → 全表扫描
--   rows 很大                          → 扫描行数多
--   Extra=Using filesort / temporary  → 排序或临时表
```

### 12.3 原因

| 原因 | 表现 |
|---|---|
| 没走索引 | `type=ALL` |
| 索引失效 | 函数包列、隐式类型转换、`!=`、`OR`、前导 `%LIKE` |
| 回表过多 | 二级索引 + `SELECT *` |
| 大事务长锁 | 事务里夹了远程调用 / 大循环更新 |
| 深分页 | `LIMIT 1000000, 20` |
| 统计信息过时 | 优化器选错索引 |

### 12.4 解决方案

- **加索引** / 改写 SQL 让索引生效
- **覆盖索引**：把 `SELECT *` 改成 `SELECT 需要的列`
- **大事务拆小**，事务内禁止远程调用
- **深分页改游标分页**：`WHERE id > last_id ORDER BY id LIMIT 20`
- `ANALYZE TABLE` 更新统计信息
- 必要时用 `FORCE INDEX` 强制走索引

---

## 十三、Redis 慢 / 抖动怎么排查？

### 13.1 现象

- 客户端 `JedisConnectionException: Read timed out`
- 监控 Redis RT 突增
- 应用线程多停在 Jedis/Lettuce 的 read

### 13.2 排查链路

```bash
# 慢日志
redis-cli SLOWLOG GET 20
redis-cli CONFIG SET slowlog-log-slower-than 10000   # 微秒

# 大 key
redis-cli --bigkeys
redis-cli MEMORY USAGE key_name
redis-cli DEBUG OBJECT key_name

# 实时命令（谨慎用，生产高 QPS 会爆）
redis-cli MONITOR

# 延迟监控
redis-cli --latency
redis-cli --latency-history -i 1

# 热 key（需 LFU 策略）
redis-cli --hotkeys

# 内存与淘汰
redis-cli INFO memory       # 看 evicted_keys
redis-cli INFO persistence  # RDB / AOF rewrite 状态
```

### 13.3 原因

| 根因 | 表现 |
|---|---|
| 大 key | `--bigkeys` 命中 |
| 慢命令 | `KEYS *` / `HGETALL` / `SMEMBERS` / `SORT` |
| RDB / AOF rewrite | fork 期间 RT 抖动 |
| 内存满触发淘汰 | `evicted_keys` 持续增长 |
| 网络 / CPU 抖动 | OS 层 iostat / top |
| 热 key | 单 key QPS 占绝大多数 |
| 集群迁移 / 主从同步 | INFO replication 看延迟 |

### 13.4 解决方案

- **大 key**：拆分；删除用 `UNLINK` 而非 `DEL`
- **慢命令**：禁用 `KEYS`，用 `SCAN`；大 Hash 用 `HSCAN`
- **持久化抖动**：调度到低峰；调 `no-appendfsync-on-rewrite yes`
- **淘汰**：调大内存或调 maxmemory-policy；业务侧做容量评估
- **热 key**：本地缓存 + Redis 双层；多副本分散
- **集群**：避免 mget 跨槽位；用 hash tag 让 key 落同槽

---

## 十四、服务僵死 / 假死怎么排查？

### 14.1 现象

- 进程在，端口在监听
- 所有请求 hang，不返回也不报错
- 监控显示线程数高位，但 CPU 不高

### 14.2 排查链路

```bash
# 一键采集现场（不重启）
jstack -l <PID>  > jstack.log    # -l 显示锁信息
jmap -histo <PID> > histo.log
jstat -gcutil <PID> 1000 30 > gc.log

# Native 栈
pstack <PID>     # 看是否卡在 JNI / native call

# 是否在 safepoint 等待
# 启动加：-XX:+PrintSafepointStatistics -XX:PrintSafepointStatisticsCount=1
```

排查清单：

1. **GC 风暴？** `jstat` 看是否在 Full GC
2. **业务线程都在等同一资源？** jstack 看是否全停在 DB / 下游 / 锁
3. **JNI / native 卡住？** `pstack` 看 native 栈
4. **Safepoint 等待？** 长循环里没 safepoint 检查点会让所有线程停等

### 14.3 原因

- Full GC 风暴
- 下游 / DB / Redis 全部 hang，业务线程全等
- 死锁（见死锁章节）
- JNI 调用阻塞（如本地库 socket 调用没设超时）
- Safepoint 死等（无 safepoint 的 counted loop）

### 14.4 解决方案

- **GC**：见 Full GC 章节
- **资源全 hang**：所有调用必须有超时；用熔断快速失败
- **死锁**：修复后重启
- **JNI**：检查本地库调用是否有超时机制
- **Safepoint**：避免巨型 counted loop，或用 `-XX:+UseCountedLoopSafepoints`
- **应急**：留完现场（jstack/jmap/pstack）后重启

---

## 排查工具速查表

| 工具 | 用途 | 一句话 |
|---|---|---|
| `top` / `top -Hp` | CPU、线程级 CPU | 找占 CPU 高的进程/线程 |
| `vmstat 1` | 系统级 CPU / IO / swap | `si/so` 看是否在 swap |
| `iostat -x 1` | 磁盘 IO | `%util`、`await` |
| `ss` / `netstat` | TCP 连接 | 看连接状态分布 |
| `jps` / `jcmd` | JVM 进程 | `jcmd <PID> Thread.print` 等效 jstack |
| `jstack` | 线程栈 | 死锁、慢、卡住都靠它 |
| `jmap` | 堆 dump / histo | OOM 现场必备 |
| `jstat` | GC 实时数据 | `-gcutil` 最常用 |
| `jinfo` | JVM 启动参数 | 看堆大小、GC 算法 |
| **Arthas** | 在线诊断 | `thread` / `trace` / `watch` / `profiler` 全能 |
| **MAT** | dump 离线分析 | Leak Suspects 自动推断泄漏 |
| **async-profiler** | 火焰图 | 找 CPU / 分配 / 锁热点 |
| `tcpdump` / `Wireshark` | 抓包 | 网络问题的终极武器 |

---

## 排查心法：三段式

```
1. 保留现场：dump、jstack、GC 日志、监控截图——先留住证据，再处理
2. 应急止血：限流 / 降级 / 重启 / 回滚——优先让业务恢复
3. 复盘根因：基于现场离线分析；产出预防措施（监控、压测、代码规范）
```

> 面试官想听的不是"我会用 jstack"，而是：
> **看到现象 → 怀疑哪几类原因 → 用什么命令排除 → 最终定位代码 → 给出修复与预防方案** 的完整链路。

---

<!-- 后续追加问题请从这里继续 -->
