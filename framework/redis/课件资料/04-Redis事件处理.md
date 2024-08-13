# 一、Epoll与网络通信

Redis事件处理机制

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/66c9ea2d18b04988b5ce8d814d2838a3.png)

## 1.1、I/O多路复用模型

### 网络IO与进程阻塞

#### 网络IO

IO：网络IO

数据到达（接收数据）：网卡会把接收到的数据写入内存中（DMA），网卡向CPU发出一个中断信号，CPU就知道数据到了，所以可以读取数据。

cpu在接到中断信号后，执行中断处理程序：

1. 将数据写入socket的接收缓冲区（内核空间到用户空间）
2. 将进程放入工作队列中

#### 进程阻塞

进程在等待某个事件（数据到达）发生之前的等待状态。

```c
s=socket(ip,port)
    bind()
    listen()
int c=accept(s) //client连接
    data=recv(c)//接收client发送的数据  
```

recv就是阻塞方法，执行到要等待数据达到。

recv的工作模式：

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/e229c945839b4509bd914ea0edd15441.png)

**采用IO多路复用的方式，来做到一个线程监听多个socket。**

### I/O多路复用

Redis利用I/O多路复用来实现网络通信。

1. I/O多路复用建立在多路事件分离函数select，poll，epoll之上。
2. 将需要进行IO操作的socket添加到select中
3. 然后阻塞等待select函数调用返回
4. 当数据到达时，socket被激活，select函数返回
5. 用户线程发起read请求，读取数据并继续执行。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/61988fc0b59c49ce99c76963ec288172.png)

### Reactor设计模式

IO多路复用模型使用Reactor设计模式实现

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/770620ba19e74187b480d2fbec560a89.png)

Handle：handle在linux中一般称为文件描述符（fd），而在window称为句柄，两者的含义一样。handle是事件的发源地。比如一个网络socket、磁盘文件等。而发生在handle上的事件可以有connection、ready for read、ready for write等。

Reactor：反应器，也叫事件分发器，负责事件的注册、删除与转发event handler。

Synchronous Event Demultiplexer：同步事件分离器，本质上是系统调用。比如linux中的select、poll、epoll等。比如，select方法会一直阻塞直到handle上有事件发生时才会返回。

Event Handler：事件处理器，其会定义一些回调方法或者称为钩子函数，当handle上有事件发生时，回调方法便会执行，一种事件处理机制。

Concrete Event Handler：具体的事件处理器，实现了Event Handler。在回调方法中会实现具体的业务逻辑。

处理流程：

1. 当应用向Reactor注册Concrete Event Handler时，应用会标识出该事件处理器希望Reactor在某种类型的事件发生发生时向其通知事件与handle关联。
2. Reactor要求注册在其上面的Concrete Event Handler传递内部关联的handle，该handle会向操作系统标识。
3. 当所有的Concrete Event Handler都注册到Reactor上后，应用会调用handle_events方法来启动Reactor的事件循环，这时Reactor会将每个Concrete Event Handler关联的handle合并，并使用Synchronous Event Demultiplexer来等待这些handle上事件的发生。
4. 当与某个事件源对应的handle变为ready时，Synchronous Event Demultiplexer便会通知 Reactor。
5. Reactor会触发事件处理器的回调方法。当事件发生时， Reactor会将被一个“key”（表示一个激活的handle）定位和分发给特定的Event Handler的回调方法。
6. Reactor调用特定的Concrete Event Handler的回调方法来响应其关联的handle上发生的事件。

示意图：

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/c75ae31b356147f5b2ae65f9d252b64c.png)

时序图：

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/0e42221696214d49bcffcdb6a2e65800.png)

### select

select模式是I/O多路复用模式的一种早期实现。也是支持操作系统最多的模式(windows)。

#### 工作模式

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/9254db602a664273ba03e9c2a0506c16.png)

#### 工作流程

1. 应用进程在调用select之前告诉select 应用进程需要监控哪些fd可读、可写、异常事件，这些分别都存在一个fd_set数组中。
2. 然后应用进程调用select的时候把fd_set传给内核（这里也就产生了一次fd_set在用户空间到内核空间的复制）
3. 内核收到fd_set后对fd_set进行遍历，然后一个个去扫描对应fd是否满足可读写事件。
4. 如果发现了有对应的fd有读写事件后，内核会把fd_set里没有事件状态的fd句柄清除，然后把有事件的fd返回给应用进程（这里又会把fd_set从内核空间复制用户空间）。
5. 最后应用进程收到了select返回的活跃事件类型的fd句柄后，再向对应的fd发起数据读取或者写入数据操作。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/777e3170853342e9ba320f4bae446788.png)

#### select缺点

* 单个进程能够监视的文件描述符的数量存在最大限制（1024）
* 内核 / 用户空间内存拷贝问题，select需要复制大量的句柄数据结构
* select返回的是含有整个句柄的数组，应用程序需要遍历整个数组才能发现哪些句柄发生了事件
* 添加等待队列后就会阻塞，每次调用都有这两个步骤

## 1.2、Epoll网络编程

Epoll是对Select的优化(poll)

* 操作的优化：功能分离，添加等待队列和阻塞操作分开
* 就绪列表的优化：内核中维护一个就绪列表(rdllist)

### Epoll的工作模式

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/faa203ba714f4ff3af9089f600f63ab9.png)

### Epoll的工作流程

不同于select 和poll的直接调用方式，epoll采用的是一组方法调用的方式，它的工作流程大致如下：

1. 调用epoll_create，创建eventpoll对象
2. 调用epoll_ctl，将eventpoll添加到socket的等待队列
3. 当进程执行到epoll_wait时，如果rdlist已经引用了socket，那么epoll_wait直接返回，如果rdllist为空，内核会将进程放入eventpoll的等待队列中，阻塞进程。
4. 当socket收到数据后，中断程序会给eventpoll的rdllist添加socket引用并唤醒eventpoll等待队列中的进程

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/5e38cd7567454d4ea8baeeb5d4b53738.png)

```
eventpoll
wait_queue_head_t wq：等待队列
struct list_head rdllist：就绪列表（双向链表）
struct rb_root rbr：红黑树，用于管理监视的socket连接

epitem
struct rb_node rbn：对应的红黑树节点
struct list_head rdllink：事件的就绪队列
struct epoll_filefd ffd：对应的fd和文件指针
struct eventpoll *ep：从属的eventpoll
```

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/66ecd82dc54c49afa66bde1d10922abb.png)

epoll：
1、将添加（epoll_ctl）和阻塞(epoll_wait)分离，使阻塞时间变少

2、使用socket引用。不做拷贝

3、不做socket列表遍历，直接访问就绪列表

4、利用双向链表（插入）和红黑树（检索）做优化

## 1.3、Redis中Epoll的实现

### I/O多路复用实现对应表

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/db189d01a436464882f29ce2abcf9461.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/582761bdb0204e3ca3e8178a14e196a8.png)

### 结构体和API

Redis采用统一的API实现各种I/O多路复用

* aeApiState：统一存放数据
* aeApiCreate：创建底层ae的apiData
* aeApiResize：重新分配底层ae的事件集合内存大小
* aeApiAddEvent：添加对应的监听对象
* aeApiDelEvent：删除对应的监听对象
* aeApiPoll：阻塞等待事件发生（数据到达）
* aeApiName：获得ae的名称

### aeApiState

aeApiState负责存放epoll的数据

```c
typedef struct aeApiState {
    int epfd;
    struct epoll_event *events;
} aeApiState;
```

* int epfd：epoll事件对应的文件描述符
* struct epoll_event *events：epoll事件结构体，用于接收已就绪的事件数组

### epoll_event

epoll事件结构体

_uint32_t events：事件类型（epollin、epollout）

epoll_data_t data：文件描述符相关数据

### aeApiCreate

创建epoll对象：

1. 给epoll的数据存储对象分配内存
2. 给epoll事件数组分配内存，用于存放已就绪事件
3. 调用epoll_create，创建eventpoll，返回epoll对应的文件描述符
4. 赋值el的IO多路复用为epoll对象

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/8937be08a3f54e4da061f6ff46665f51.png)

### aeApiAddEvent

添加指定fd的监听：

1. 获得fd的监听标识
2. 如果fd下没有监听，则添加监听，否则修改监听
3. 将ae事件转化为epoll事件，如果是ae_readable则转化为epollin，
4. 如果是ae_writable则转化为epollout
5. 调用epoll_ctl函数，向epoll实例中添加或修改监听对象

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/c12dce581a594020a730a6ae61778af7.png)

可以监视的事件：

epollin：缓冲区可读

epollout：缓冲区可写

epollerr：错误

epollhup：连接中断

epolllet：边缘触发

边缘触发: 到了触发 就触发一次

条件触发：达到条件就触发 触发多次

### aeApiDelEvent

修改或删除指定fd的监听：

1. 获得fd的监听标识和删除标识
2. 如果是ae_readable，则转化为epollin
3. 如果是ae_writable则转化为epollout
4. 如果有监听，则调用epoll_ctl，进行修改
5. 否则调用epoll_ctl进行删除

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/3d018eb3ae9b43fbb9983d20e0a87553.png)

### aeApiPoll

阻塞等待事件发生：

1. 获得epoll对应的aeApiState
2. 调用epoll_wait,阻塞等待事件发生
3. 当事件到达时，将已到达的事件存储在aeApiState的events中
4. 遍历已就绪事件，转化epoll事件类型为ae的事件类型
5. 将已就绪的事件fd写入ae的已就绪事件fd中
6. 将已就绪的事件类型写入ae的已就绪事件类型中

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/16ef951871ff4ed3b554be9c2d1ae52a.png)

## 1.4、Redis网络通信

### 服务器初始化

1. Server启动后，初始化事件驱动框架：aeCreateEventLoop
2. 注册时间事件：aeCreateTimeEvent，处理器：serverCron
3. 注册多个文件事件：aeCreateFileEvent，处理器：acceptTcpHandler（连接应答处理器）
4. 开启IO线程和bio线程
5. 开启事件循环，等待事件发生

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/41ce62a9b53e45e1ab3911210bbb135a.png)

### 客户端发起连接

1. IO多路复用程序会监听多个socket，将socket放入一个队列中排列,每次从队列中取出一个socket给事件分派器,事件分派器把socket给对应的事件处理器
2. 客户端跟redis发起连接,此时会产生一个AE_READABLE事件
3. acceptTcpHandler处理客户端建立的连接
4. 创建Client并注册一个读回调函数readQueryFromClient（命令请求处理器）

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/9ae0ba32b1e246e7b1799d83bbb1bbd0.png)

### 客户端发起请求

1. 首先就会在socket产生一个AE_READABLE事件
2. 文件事件分派器会把事件分派给readQueryFromClient
3. readQueryFromClient会从socket中读取请求相关数据,然后进行执行和处理(多线程)
   IO线程：命令的接收、命令的解析
   主线程：命令的执行

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/39f1328b77ef43dd9fea5e5f0451ad90.png)

#### Redis的多线程

在 Redis 6 以前，从命令接收到执行，主要由主线程来完成。

Redis 6 引入了 IO 多线程。IO 线程功能是接收命令、解析命令、发送结果。

除此之外，Redis 还有后台线程，用来处理耗时的任务，称为 bio 线程家族。bio 线程功能目前有 3 点：

* close fd ：关闭文件描述符
* AOF fsync ：fsync 刷盘
* Lazy Free ：异步释放对象内存

IO 线程和 bio 线程通过「生产者-消费者」模型来执行任务。如下图所示

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/99865df30c0c48c1a69ddac2e5df19a7.png)

主线程会将「就绪读」、「就绪写」客户端列表，分发到 IO 线程队列 io_threads_list 中。IO 线程通过 IOThreadMain 函数消费。

### 服务器端响应

1. 将客户端添加到待响应列表中
2. Redis将回复写入buf，buf不够写入replyList
3. 在beforeSleep中调用handleClientsWithPendingWrites(handleClientsWithPendingWritesUsingThread)向客户端socket写入回复
4. 如果没有发送完，则注册命令回复处理器sendReplyToClient
5. 当客户端准备好读取相应数据时,就会在socket上产生一个AE_WRITABLE事件，sendReplyToClient会向客户端socket写入回复

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/1c7aea56104b475eb9c4a4091b3849a3.png)

## 设计思想和优势

1. I/O多路复用，单线程监听多个socket
   Reactor模式  将请求通过事件分离器转发到具体处理器
2. Epoll实现是对select的优化
   Epoll的优势：不用socket列表的拷贝、不用遍历socket列表、操作分离： 添加和阻塞
3. Redis的统一接口，类似多态的实现方式

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/88af6c9f9adb4aeda25d1174477ca6a0.png)

# 二、Redis事件机制

Redis服务器是典型的事件驱动系统

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/16c492413efe41e1bf12d1786438ec4c.png)

* 是通过IO多路复用实现的
* 事件分为文件事件和时间事件
* 在一个循环中不断的监听事件
* 当事件发生后，通过回调函数的方式进行处理

## 2.1、文件事件

ae.h

### aeFileEvent

```c
typedef struct aeFileEvent {
    int mask; /* one of AE_(READABLE|WRITABLE|BARRIER) */
    aeFileProc *rfileProc;
    aeFileProc *wfileProc;
    void *clientData;
} aeFileEvent;
```

* int mask：文件事件类型（AE_NONE，AE_READABLE，AE_WRITABLE）
* aeFileProc *rfileProc：可读处理函数
* aeFileProc *wfileProc：可写处理函数
* void *clientData：客户端传入的数据
* aeFileProc：是回调函数，如果当前文件事件所指定的事件类型发生时，则会调用对应的回调函数处理该事件

### aeFiredEvent

```c
typedef struct aeFiredEvent {
    int fd;
    int mask;
} aeFiredEvent;
```

* int fd：就绪事件的文件描述符
* int mask：就绪事件类型：(AE_NONE，AE_READABLE，AE_WRITABLE)

## 2.2、时间事件

```c
typedef struct aeTimeEvent {
    long long id; /* time event identifier. */
    monotime when;
    aeTimeProc *timeProc;
    aeEventFinalizerProc *finalizerProc;
    void *clientData;
    struct aeTimeEvent *prev;
    struct aeTimeEvent *next;
    int refcount; /* refcount to prevent timer events from being
  		   * freed in recursive time event calls. */
} aeTimeEvent;
```

* long long id：时间事件的id
* monotime when：时间事件到达的时间的毫秒
* aeTimeProc *timeProc：时间事件处理的回调函数
* aeEventFinalizerProc *finalizerProc：时间事件删除时的回调函数
* void *clientData：客户端传入的数据
* aeTimeEvent *prev：指向前一个时间事件
* aeTimeEvent *next：指向后一个时间事件
* refcount: 引用计数

## 2.3、aeEventLoop

```c
typedef struct aeEventLoop {
    int maxfd;   /* highest file descriptor currently registered */
    int setsize; /* max number of file descriptors tracked */
    long long timeEventNextId;
    aeFileEvent *events; /* Registered events */
    aeFiredEvent *fired; /* Fired events */
    aeTimeEvent *timeEventHead;
    int stop;
    void *apidata; /* This is used for polling API specific data */
    aeBeforeSleepProc *beforesleep;
    aeBeforeSleepProc *aftersleep;
    int flags;
} aeEventLoop;
```

* int maxfd：当前注册的最大文件描述符
* int setsize：监听的网络事件fd的个数
* long long timeEventNextId：下一个时间事件的ID
* aeFileEvent *events：存放所有注册的读写事件，是大小为setsize的数组
* aeFiredEvent *fired：存放就绪的读写事件。同样是setsize大小的数组
* aeTimeEvent *timeEventHead：存放时间事件，指向链表头
* int stop：事件处理开关
* void *apidata：指向IO多路复用对象
* aeBeforeSleepProc *beforesleep：事件循环在每次迭代前调用beforesleep执行一些异步处理
* aeBeforeSleepProc *aftersleep：执行处理事件之后的回调函数

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/09be590e29cc427399d3d0229490f237.png)

## 内容小结

* Redis是典型的事件驱动系统![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/dc250e2195f44160834a4590ee011a1c.png)
* 文件事件和时间事件
  文件事件：aeFileEvent、aeFiredEvent
* 事件采用aeEventLoop进行封装

  ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/3a42d0384c35485ca9e945275b0c2aa0.png)

## 2.4、相关API

ae.c

### 事件管理相关

#### aeCreateEventLoop

创建事件循环：

1. 申请eventloop的内存空间
2. 创建events和fired的数组
3. 初始化其他属性
4. 根据操作系统环境，创建IO多路复用
5. 循环初始化文件事件数组的标识为AE_NONE

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/696aea7fc7a740ce950ce96972a6b1f9.png)

#### aeCreateFileEvent

创建文件事件：

1. 如果事件的fd大于最大数，则返回错误
2. 添加fd的事件监听
3. 设置事件类型（事件掩码）
4. 如果是读事件，则设置事件的读回调函数
5. 如果是写事件，则设置事件的写回调函数
6. 设置客户端数据
7. 如果fd大于当前最大fd
8. 则更新最大fd为当前fd
9. 返回ok

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/f7e33a1403a249ff9b8d1e87f84d0034.png)

#### aeCreateTimeEvent

创建时间事件：

1. 设置id等于el的下一个id+1
2. 申请时间事件的内存空间
3. 设置处理事件的时间
4. 初始化其他属性
5. 将新事件插入链表头

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/6a7cbb3d71c0473981d9b5281ced523d.png)

#### aeResizeSetSize

重新分配内存空间：

1. 如果要设置的size等于当前的size，则返回ok
2. 如果当前最大的fd大于要设置的size，则返回错误
3. 如果调用IO多路复用的resize失败，则返回错误
4. 重新分配events数组的大小，大小为size
5. 重新分配fired数组的大小，大小为size
6. 设置el的最大size为当前设置的size
7. 循环依次将新生成的events的事件类型设置为ae_none

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/bc5590b8cdc847bc922d53cd3343230c.png)

#### aeDeleteFileEvent

删除文件事件：

1. 如果fd大于最大fd，则返回错误
2. 获得文件事件，如果该事件标识为ae_none,则返回
3. 如果要移除写事件，则移除AE_BARRIER
4. 删除fd上的事件
5. 如果fd是最大fd并且没有事件
6. 则将最大fd更新为最大的不是无事件的fd

AE_BARRIER ： 反转

正常的读写顺序：是先读后写

AE_BARRIER：该事件是AE_BARRIER，则需要先写后读

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/e68b23520c964912819882da591b3867.png)

#### aeDeleteTimeEvent

删除时间事件：

1. 获得时间事件
2. 循环链表
3. 如果id=输入id，则标记为删除

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/b29710970d704c27af3eb3c4cc27acb4.png)

#### aeDeleteEventLoop

删除事件循环：

1. 释放IO多路复用对象
2. 释放events数组
3. 释放fired数组
4. 循环时间事件链表，依次释放节点
5. 释放eventLoop对象

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/30c7561905584698a961816574be49b4.png)

#### aeStop

停止事件循环

### 内容小结

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/c54301f5a56b48d68f8d9e0bda6db88e.png)

### 事件处理回调函数

1. 声明回调函数
2. 定义函数指针变量注册回调函数
3. 调用函数指针变量
4. 触发回调函数

networking.c

#### acceptTcpHandler

连接应答回调函数：

1. 设置最多处理连接数为1000
2. 针对每个连接，调用anetTcpAccept函数进行accept，并将客户端地址记录到cip以及cport中
3. 连接后的socket描述符为cfd，根据该值调用acceptCommonHandler建立Client

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/b30fdd34248446de8ed3b6b523425aad.png)

##### anetTcpAccept

1. anet是redis对网络通信(socket)的简单封装和一些状态设置的封装
2. 调用anetGenericAccept接收连接，
3. 连接后如果是ipv4，则以ipv4的方式记录客户端IP和端口
4. 否则以ipv6的方式记录客户端IP和端口
5. 返回连接后socekt的fd

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/6bfc5f3fb32d45639c0203b8a8938864.png)

##### acceptCommonHandler

1. 如果状态不是正常连接，则关闭连接并返回
2. 如果客户端数+集群节点数大于最大客户端数，则关闭连接并返回
3. 调用createClient创建新的客户端
4. 更新客户端标志
5. 调用clientAcceptHandler，异步处理要释放的客户端
6. 如果异步释放错误并且连接有错误，则调用freeClient同步释放客户端

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/17f9352092af4fc69a20a16c3190daa9.png)

##### createClient

1. 为创建客户端申请空间
2. 如果有连接则可以创建客户端
3. 设置fd为非阻塞状态
4. 将tcp连接设为非延迟（屏蔽nagle算法）
5. 创建文件事件，绑定读事件到eventLoop，并设置回调函数为readQueryFromClient
6. 选择0数据库
7. 设置client的属性
8. 如果有连接，则添加到服务器的客户端链表中
9. 初始化客户端的事务状态

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/6acff7f8e89a43d99d060d5751af29ec.png)

##### freeClient

1. 如果客户端是被保护的，则采用异步释放客户端
2. 如果client连接主机，则要缓存client
3. 如果client连接从机，则与从机断开连接
4. 清空输入缓冲区
5. 如果客户端阻塞，则解开阻塞
6. 释放阻塞的字典空间
7. 清空watch信息
8. 退订频道和模式
9. 释放reply数据结构
10. 调用unlinkClient，关闭socket，移除对该client的监听并移除所有的引用
11. 清空主从服务器连接
12. 从client_to_close中删除该client节点
13. 释放client的名称和参数列表
14. 清空事务状态
15. 释放client

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/2c6033a618494535a1956aea61810397.png)

#### readQueryFromClient

命令请求回调函数：

1. 从连接中获得客户端数据
2. 调用 postponeClientRead()判断是否需要IO线程读取数据
3. 如果需要则返回
4. 设置读入长度为16K
5. 如果是多条请求，则根据读入长度设置读入长度
6. 获取查询缓冲区中当前内容的长度
7. 如果峰值小于当前长度，则将峰值更新为当前长度
8. 为当前缓冲区扩容
9. 从socket中读取数据
10. 如果读取错误则异步释放client
11. 如果读取完成，则异步释放client
12. 如果是主client正在读取数据，则更新复制查询缓冲区
13. 增加输入缓冲区大小
14. 更新最后一次服务器与client交互时间
15. 如果是主client则更新复制操作的偏移量
16. 增加网络输入的字节数
17. 如果输入缓冲区长度超过最大缓冲区长度
18. 则清空缓冲区并释放客户端
19. 调用processInputBuffer进行命令解析

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/66733051b8374c5cbfb75c4851094a16.png)

##### processInputBuffer

命令解析：

1. 从查询缓冲区中依次读取数据
2. 如果从*开始则为普通模式请求，否则为内联模式请求
3. 如果是内联模式请求，则调用processInlineBuffer()进行解析
4. 如果是普通模式请求，则调用processMultibulkBuffer()进行解析
5. 如果client包含client_pending_read标识，则给client设置client_pending_command标识，并返回
6. 调用processCommandAndResetClient()进行命令的执行

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/c34456e2c9f144c386b430a54128fc16.png)

##### processCommandAndResetClient

1. 调用processCommand执行命令
2. 如果是主client则需要更新复制偏移量
3. 如果client不是阻塞，则重置client
4. 如果是主client则进行命令传播

#### sendReplyToClient

命令响应回调函数：调用writeToClient处理

##### writeToClient

1. 写入客户端
2. 如果client的回复缓冲区有数据，就循环写入socket
3. 如果输出缓冲区中有数据
4. 将缓冲区的数据写到fd中
5. 如果写失败则跳出循环
6. 更新发送的数据计数器
7. 如果发送的数据等于buf的偏移量，则表示发送完成，重置发送位置和计算器
8. 如果输出缓冲区中没有数据，则发送回复链表中的内容
9. 取出回复链表的第一条回复对象
10. 如果对象为空则删除
11. 将对象的值写到fd中
12. 如果写入失败则跳出
13. 更新发送数据计数器
14. 发送完成则删除节点并重置发送数据长度，更新回复链表总字节数
15. 如果发送长度超过最大写入长度或者内存不足，则跳出循环
16. 更新网络输入字节数
17. 如果写入失败则异步释放client
18. 如果回复缓冲区中没有数据
19. 删除client的可写事件监听
20. 如果设置写入后关闭标志，则异步释放client

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/a802ecc498bd47328e22b8dc312b3a45.png)

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/5ea66decaf2b4980812b58622f8c99a5.png)

### 内容小结

* acceptTcpHandler
  连接应答回调函数
  连接：anetTcpAccept
  建立Client：acceptCommonHandler，调用createClient创建新的客户端
  createClient：注册readQueryFromClient回调函数
* readQueryFromClient

  命令请求回调函数

  调用 postponeClientRead()判断是否需要IO线程读取数据
  调用processInputBuffer进行命令解析，调用processCommandAndResetClient命令执行
* sendReplyToClient
  命令响应回调函数

  调用writeToClient处理


### 多线程读取和写入数据

IO线程的详细流程：

1. Redis 启动的时候会调用 `InitServerLast()`初始化 IO 线程(用户设置了线程数量，且允许多线程读)
2. 每次有新的客户端请求时，主线程会执行 `readQueryFromClient()`，在该函数中会调用postponeClientRead()将client 对象添加到 `server.clients_pending_read` 列表中。
3. 在 `beforeSleep()`中，Redis 主线程会调用 `handleClientsWithPendingReadsUsingThreads()` 方法，把 `server.clients_pending_read` 列表中的 client 对象依次分配到 `io_threads_list` 队列数组中。
4. 在主线程等待的过程中，IOThreadMain()会从对应的 `io_threads_list` 队列中获取client对象，依次调用 `readQueryFromClient()`方法读取数据并按照RESP协议解析参数。
5. 等所有IO线程执行完毕后，主线程会调用 `processCommandAndResetClient()` 方法，该方法会调用 `processCommand()` 执行具体的命令，并把执行结果写入到client对象的输出缓冲区中。
6. 在 `beforeSleep()` 中，Redis主线程会调用 `handleClientsWithPendingWritesUsingThreads()`方法，把所有需要返回数据的client 对象分配到 `io_threads_list` 队列数组中。
7. IOThreadMain()会从对应的 `io_threads_list` 队列中获取client对象，依次调用 `writeToClient()` 方法把client对象输出缓冲区中的数据通过socket返回给客户端。

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/b6d613f9db404142b92d4ed6fe620cf9.png)

#### **initThreadedIO**

IO线程初始化：

1. 设置IO线程为未激活
2. 如果IO线程数量为1，则返回
3. 如果设置的IO线程数大于128，则退出
4. 依次初始化IO线程
5. 创建 `io_threads_list`用于放置要处理的client
6. 设置互斥锁
7. 创建IO线程并绑定执行方法IOThreadMain

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/0045b8872f0248d6935837a251056202.png)

#### postponeClientRead

将数据放到IO线程中执行：

1. 如果IO线程激活并且可以使用IO读并且client对象不包含CLIENT_MASTER、CLIENT_SLAVE、CLIENT_PENDING_READ标识，
2. 则先给当前client对象增加 CLIENT_PENDING_READ 标志
3. 把当前client对象添加到 `server.clients_pending_read`列表末尾并返回1

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/469da85f0baa4b1e9db1273de4cb1e95.png)

#### **handleClientsWithPendingReadsUsingThreads**

设置IO线程读任务列表

1. 如果IO线程未激活或者不允许IO线程读，则返回
2. 循环server.clients_pending_read列表
3. 按照RoundRobin算法给IO线程分配读任务
4. 设置读操作标志位并统计各个IO线程任务数
5. 如果还有未分配的client，则调用readQueryFromClient()
6. 等待所有的IO线程处理完了所有的client的读数据操作
7. 再次遍历clients_pending_read列表
8. 如果还有未处理的数据，则调用processInputBuffer()函数处理

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/f56e5b4058244407af756bb564cab82b.png)

#### handleClientsWithPendingWritesUsingThreads

设置IO线程写任务列表

1. 获得要处理的client数
2. 如果IO线程数为1或者不需要启动IO线程(stopThreadedIOIfNeeded)
   要处理的client数量小于设置的线程数的2倍，不启动IO线程
3. 则调用handleClientsWithPendingWrites()，将数据写入到客户端
4. 如果IO线程未激活，则调用startThreadIO()激活IO线程
5. 循环server.clients_pending_write列表
6. 按照RoundRobin算法给IO线程分配写任务
7. 设置写操作标志位并统计各个IO线程任务数
8. 如果还有未分配的client，则调用writeToClient()
9. 等待所有的IO线程处理完了所有的client的写数据操作
10. 再次遍历clients_pending_write列表
11. 如果还有未处理的数据，则注册sendReplyToClient函数处理

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/9332b8c66db34e019fa1472a17d7d3fa.png)

#### IOThreadMain

IO线程执行读写任务

1. 获取当前线程在IO_Threads的数组下标
2. IO线程会先自旋一会，循环
3. 如果在自旋期间主线程就给当前IO线程分配了任务的话，IO线程就不会去抢夺互斥锁
4. 如果自旋之后还没有任务分配，IO线程则会调用 `pthread_mutex_lock()`方法来抢夺对应的互斥锁。
5. 如果IO线程被分配到读写任务，就会进行具体的读写操作。
6. 每个IO线程都会遍历自己的 `io_threads_list[id]`任务队列，对队列中的client对象执行具体的读写操作。
7. 如果是写操作，则所有的线程调用writeToClient()
8. 如果是读操作，则所有的线程调用readQueryFromClient()
9. 清空自己的任务队列

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/1dbb4466ac4946caa30a130151bdcc01.png)

### serverCron

server.c

定时任务回调函数：

1. 发送SIGALRM信号触发看门狗处理
2. 更新服务器时间
3. 如果开启动态执行频率，则根据客户端数量动态调整执行频率
4. 每100ms更新一次统计量（run_with_period）
5. 缓存lru
6. 如果使用内存大于峰值内存，则更新峰值内存
7. 每100ms更新一次内存使用状态（run_with_period）
8. 如果收到了SIGTERM信号，尝试退出
9. 每5秒输出一次非空databases的信息到log当中（run_with_period）
10. 如果不是sentinel模式，则每5秒输出一个connected的client的信息到log
11. 调用clientsCron清理空闲的客户端或者释放query buffer中未被使用的空间
12. 调用databasesCron进行数据库处理
13. 如果开启了aof_rewrite的调度并且当前没有执行rdb/aof的操作，则进行aof重写操作
14. 如果有aof或rdb在进行，则等待对应的进程退出
15. 如果出错，则记录log
16. 如果是rdb任务退出，则调用backgroundSaveDoneHandler进行收尾工作
17. 如果是aof任务退出，调用backgroundRewriteDoneHandler进行收尾工作
18. 更新resize策略，不能进行resize
19. 如果没有aof或rdb在进行，则判断是否执行rdb和aof重写
20. 如果开启了aof_flush_postponed_start，则进行有条件刷盘
21. 每1秒检查如果刷盘错误则继续刷盘
22. 关闭要关闭的client
23. 每1秒执行一次replication
24. 每100ms执行一次clusterCron
25. 每100ms执行一次sentinel的定时器
26. 每1秒清理一次server.migrate_cached_sockets链表上的超时sockets
27. 更新serverCron执行次数
28. 返回下一次执行serverCron的间隔

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/272db761cef54fadb77c2469fa2f7dd2.png)

## 2.5、设计思想与优势

1. 利用aeEventLoop进行事件的封装

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/01ee98d03fe642dd91fe72326968ef27.png)
2. 当有事件产生时回调事件处理函数

   acceptTcpHandler

   readQueryFromClient

   sendReplyToClient
3. IOThread读写数据

   IO线程不是一开始就有的

   IO线程处理 读请求、写响应命令解析，不处理命令执行 （标识：client_pending_read）

   IO线程要么读要么写
4. 利用serverCron进行后台处理，提升性能

   ![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/de9bef0b1c684c499db26e277bc5210f.png)

# 三、aeProcessEvent

## 3.1、beforeSleep

server.c

main()--->aeMain()-->aeProcessEvents--->beforeSleep

处理事件之前：

1. 获得分配内存
2. 如果分配内存大于峰值内存，则峰值内存等于分配内存
3. 设置IO线程读任务
4. 如果集群有效，则调用clusterBeforeSleep处理集群
5. 如果开启定期删除并且是主机，则进行快速过期删除
6. 如果需要主从响应，则进行主从响应
7. 如果有等待的client，则解除所有等待wait命令而被阻塞的client
8. 如果有非阻塞的client，则处理非阻塞client的输入缓冲区内容
9. 有条件刷盘
10. 设置IO线程写任务
11. 释放GIL锁（全局解释器锁）

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/20ae3cd0e08b4a23affac42c681c5d6e.png)

## 3.2、aeProcessEvents

事件处理：

* 把文件事件处理嵌在时间事件之中，先找到要处理的时间事件，设置等待时间(超时时间)、
* 再处理文件事件，处理完后，再处理时间事件，效率更高。

  具体流程如下：

1. 如果既不是时间事件也不是文件事件，则返回0
2. 如果有文件事件或时间事件并且没有设置DONT_WAIT，则开始处理
3. 调用usUntilEarliestTimer()查找距离当前时间最近的时间事件
4. 如果时间事件存在，则获取当前时间，计算相差的时间
5. 如果相差的时间大于0，则设置等待时间的秒和毫秒，否则设置等待时间为0
6. 如果时间事件不存在，如果标志是DONT_WAIT，则设置等待时间为0，否则为null
7. 调用beforeSleep，处理前置任务
8. 调用IO多路复用API，阻塞等待就绪的文件事件
9. 如果有afterSleep，并且标志为AE_CALL_AFTER_SLEEP，则处理aftersleep
10. 遍历就绪的文件事件，依次处理
11. 如果事件标识为可读，则调用读事件处理回调函数rfileProc处理
12. 如果事件标识为可写，则调用写事件处理回调函数wfileProc处理
13. 如果是反转，则先处理写事件后处理读事件
14. 如果有时间事件则调用processTimeEvents处理时间事件
15. 返回处理的事件计数

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/0a9c00178ee44dfea1e3ac933a943260.png)

## 3.3、processTimeEvents

时间事件处理：

1. 如果当前时间小于最后一次访问时间，则把所有的时间事件再执行一遍
2. 设置最后一次访问时间为当前时间
3. 遍历时间事件链表
4. 如果id是标志为删除的id，则在链表上删除该节点
5. 如果有finalizerProc，则执行finalizerProc回调函数
6. 释放该节点
7. 如果id大于最大id，则继续循环
8. 获得当前时间(秒和毫秒)
9. 如果当前时间大于时间事件触发时间，则触发timeProc回调函数
10. 如果是执行多次，则计算下一次的执行时间
11. 否则将执行的时间事件id设置为DELETED标识
12. 链表指向下一个节点，返回执行次数

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/601083c5e0a8428380d34a8edaea9a96.png)

## 3.4、设计思想与优势

1. 在一个大循环里处理事件，简单实用
2. 把文件事件处理嵌在时间事件之中，处理性能高
3. 在文件事件处理前利用beforesleep，处理前置任务

![image.png](https://fynotefile.oss-cn-zhangjiakou.aliyuncs.com/fynote/fyfile/16834/1667179776002/cbe667d23caf4dfc85085f7a23d02b72.png)
