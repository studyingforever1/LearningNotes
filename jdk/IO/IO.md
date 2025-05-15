# IO

## 网络IO

#### IO零拷贝

##### DMA读写和CPU读写

> **直接内存访问**（**D**irect **M**emory **A**ccess，**DMA**）是[计算机科学](https://zh.wikipedia.org/wiki/计算机科学)中的一种内存访问技术。它允许某些[电脑](https://zh.wikipedia.org/wiki/電腦)内部的硬件子系统（电脑外设），可以独立地直接读写系统[内存](https://zh.wikipedia.org/wiki/記憶體)，而不需[中央处理器](https://zh.wikipedia.org/wiki/中央處理器)（CPU）介入处理 。在同等程度的处理器负担下，DMA是一种快速的数据传送方式。很多硬件的系统会使用DMA，包含[硬盘](https://zh.wikipedia.org/wiki/硬碟)控制器、[绘图显卡](https://zh.wikipedia.org/wiki/繪圖顯示卡)、[网卡](https://zh.wikipedia.org/wiki/网络卡)和[声卡](https://zh.wikipedia.org/wiki/声卡)。

**当进行其他设备与内存进行数据读写交互的时候，采用的是DMA拷贝，而平时内存与内存之间的读写交互，采用的是CPU拷贝的方式**

###### 传统 Linux 中的零拷贝技术

所谓零拷贝，就是在数据操作时，不需要将数据从一个内存位置拷贝到另外一个内存位置，这样可以减少一次内存拷贝的损耗，从而节省了 CPU 时钟周期和内存带宽。

我们模拟一个场景，从文件中读取数据，然后将数据传输到网络上，那么传统的数据拷贝过程会分为哪几个阶段呢？具体如下图所示。（NIC是网卡）

<img src=".\images\零拷贝.png" style="zoom: 50%;" />

从上图中可以看出，从数据读取到发送一共经历了**四次数据拷贝**，具体流程如下：

1. 当用户进程发起 read() 调用后，上下文从用户态切换至内核态。DMA 引擎从文件中读取数据，并存储到内核态缓冲区，这里是**第一次数据拷贝**。
2. 请求的数据从内核态缓冲区拷贝到用户态缓冲区，然后返回给用户进程。第二次数据拷贝的过程同时，会导致上下文从内核态再次切换到用户态。
3. 用户进程调用 send() 方法期望将数据发送到网络中，此时会触发第三次线程切换，用户态会再次切换到内核态，请求的数据从用户态缓冲区被拷贝到 Socket 缓冲区。
4. 最终 send() 系统调用结束返回给用户进程，发生了第四次上下文切换。第四次拷贝会异步执行，从 Socket 缓冲区拷贝到协议引擎中。

传统的数据拷贝过程为什么不是将数据直接传输到用户缓冲区呢？其实引入内核缓冲区可以充当缓存的作用，这样就可以实现文件数据的预读，提升 I/O 的性能。但是当请求数据量大于内核缓冲区大小时，在完成一次数据的读取到发送可能要经历数倍次数的数据拷贝，这就造成严重的性能损耗。

接下来我们介绍下使用零拷贝技术之后数据传输的流程。重新回顾一遍传统数据拷贝的过程，可以发现第二次和第三次拷贝是可以去除的，DMA 引擎从文件读取数据后放入到内核缓冲区，然后可以直接从内核缓冲区传输到 Socket 缓冲区，从而减少内存拷贝的次数。

在 Linux 中系统调用 **sendfile**() 可以实现将数据从一个文件描述符传输到另一个文件描述符，从而实现了零拷贝技术。在 Java 中也使用了零拷贝技术，它就是 NIO FileChannel 类中的 transferTo() 方法，transferTo() 底层就依赖了操作系统零拷贝的机制，它可以将数据从 FileChannel 直接传输到另外一个 Channel。transferTo() 方法的定义如下：

```java
public abstract long transferTo(long position, long count, WritableByteChannel target) throws IOException;
```

FileChannel#transferTo() 的使用也非常简单，我们直接看如下的代码示例，通过 transferTo() 将 from.data 传输到 to.data()，等于实现了文件拷贝的功能。

```java
public void testTransferTo() throws IOException {

    RandomAccessFile fromFile = new RandomAccessFile("from.data", "rw");

    FileChannel fromChannel = fromFile.getChannel();

    RandomAccessFile toFile = new RandomAccessFile("to.data", "rw");

    FileChannel toChannel = toFile.getChannel();

    long position = 0;

    long count = fromChannel.size();

    fromChannel.transferTo(position, count, toChannel);

}
```

在使用了 FileChannel#transferTo() 传输数据之后，我们看下数据拷贝流程发生了哪些变化，如下图所示：

<img src=".\images\零拷贝01.png" style="zoom: 50%;" />

比较大的一个变化是，DMA 引擎从文件中读取数据拷贝到内核态缓冲区之后，由操作系统直接拷贝到 Socket 缓冲区，不再拷贝到用户态缓冲区，所以数据拷贝的次数从之前的 4 次减少到 3 次。

但是上述的优化离达到零拷贝的要求还是有差距的，能否继续减少内核中的数据拷贝次数呢？在 Linux 2.4 版本之后，开发者对 Socket Buffer 追加一些 Descriptor 信息来进一步减少内核数据的复制。如下图所示，DMA 引擎读取文件内容并拷贝到内核缓冲区，然后并没有再拷贝到 Socket 缓冲区，只是将数据的长度以及位置信息被追加到 Socket 缓冲区，然后 DMA 引擎根据这些描述信息，直接从内核缓冲区读取数据并传输到协议引擎中，从而消除最后一次 CPU 拷贝。

<img src=".\images\零拷贝02.png" style="zoom:50%;" />

通过上述 Linux 零拷贝技术的介绍，你也许还会存在疑问，最终使用零拷贝之后，不是还存在着数据拷贝操作吗？其实从 Linux 操作系统的角度来说，零拷贝就是为了避免用户态和内存态之间的数据拷贝。无论是传统的数据拷贝还是使用零拷贝技术，其中有 2 次 DMA 的数据拷贝必不可少，只是这 2 次 DMA 拷贝都是依赖硬件来完成，不需要 CPU 参与。所以，在这里我们讨论的零拷贝是个广义的概念，**只要能够减少不必要的 CPU 拷贝，都可以被称为零拷贝。**



### IO模型

数据到达（接收数据）：网卡会把接收到的数据写入内存中的socket接收缓存区中（DMA），网卡向CPU发出一个中断信号，CPU就知道数据到了，所以可以读取数据。

cpu在接到中断信号后，执行中断处理程序：

1. 将数据从socket的接收缓冲区中拷贝到用户应用的接收区中
2. 将进程放入工作队列中



#### 同步IO

##### BIO

![](.\images\BIO01.jpg)

```c
s=socket(ip,port)
    bind()
    listen()
int c=accept(s) //client连接
    data=recv(c)//接收client发送的数据 阻塞 只有recv这个方法才将数据从socket的读取缓冲区拷贝到用户空间来
```



##### select

select模式是I/O多路复用模式的一种早期实现。也是支持操作系统最多的模式(windows)。

<img src=".\images\select01.jpg" style="zoom: 67%;" />

<img src=".\images\select02.jpg" style="zoom:67%;" />

```c
#define MAXCLINE 5       // 连接队列中的个数
int fd[MAXCLINE];        // 连接的文件描述符队列

int main(void)
{
      sock_fd = socket(AF_INET,SOCK_STREAM,0)          // 建立主机间通信的 socket 结构体
      .....
      bind(sock_fd, (struct sockaddr *)&server_addr, sizeof(server_addr);         // 绑定socket到当前服务器
      listen(sock_fd, 5);  // 监听 5 个TCP连接

      fd_set fdsr;         // bitmap类型的文件描述符集合，01100 表示第1、2位有数据到达
      int max;

      for(i = 0; i < 5; i++)
      {
          .....
          fd[i] = accept(sock_fd, (struct sockaddr *)&client_addr, &sin_size);   // 跟 5 个客户端依次建立 TCP 连接，并将连接放入 fd 文件描述符队列
      }

      while(1)               // 循环监听连接上的数据是否到达
      {
        FD_ZERO(&fdsr);      // 对 fd_set 即 bitmap 类型进行复位，即全部重置为0

        for(i = 0; i < 5; i++)
        {
             FD_SET(fd[i], &fdsr);      // 将要监听的TCP连接对应的文件描述符所在的bitmap的位置置1，比如 0110010110 表示需要监听第 1、2、5、7、8个文件描述符对应的 TCP 连接
        }

        ret = select(max + 1, &fdsr, NULL, NULL, NULL);  // 调用select系统函数进入内核检查哪个连接的数据到达

        for(i=0;i<5;i++)
        {
            if(FD_ISSET(fd[i], &fdsr))      // fd_set中为1的位置表示的连接，意味着有数据到达，可以让用户进程读取
            {
                //recv这个方法才将数据从socket的读取缓冲区拷贝到用户空间来
                ret = recv(fd[i], buf,sizeof(buf), 0);
                ......
            }
        }
  }
```

##### poll

管理多个描述符也是进行轮询，根据描述符的状态进行处理，但 **poll 无最大文件描述符数量的限制**，**因其基于链表存储**。

select 和 poll 在内部机制方面并没有太大的差异。相比于 select 机制，poll 只是取消了最大监控文件描述符数限制，并没有从根本上解决 select 存在的问题。

##### epoll

<img src=".\images\epoll01.jpg" style="zoom:67%;" />

<img src=".\images\epoll02.jpg" alt="epoll02"  />

#### 异步IO

**异步I/O**是计算机操作系统对[输入输出](https://zh.wikipedia.org/wiki/输入输出)的一种处理方式：发起I/O请求的线程不等I/O操作完成，就继续执行随后的代码，I/O结果用其他方式通知发起I/O请求的程序。与异步I/O相对的是更为常见的“同步（阻塞）I/O”：发起I/O请求的线程不从正在调用的I/O操作函数返回（即被阻塞），直至I/O操作完成。

POSIX提供下述API函数：

|      |    阻塞     |           非阻塞            |
| :--: | :---------: | :-------------------------: |
| 同步 | write, read | write, read + poll / select |
| 异步 |      -      |     aio_write, aio_read     |

- aio
- io_uring (Linux 5.1以后支持)

JDK1.7开始支持的异步IOAPI **AsynchronousServerSocketChannel** 在linux5.1以前 底层依然使用epoll来实现 5.1后引入io_uring后暂时不清楚



## 文件IO

文件流在JVM中读写，当调用`flush`方法时，将JVM中的数据提交给操作系统，操作系统为了加速写入，并不会直接将数据写入磁盘中，而是写到操作系统的内存缓存中，当操作系统内存缓存满了等情况时才写入磁盘中。

Linux中提供`fsync`和`fdatasync`系统调用来支持文件同步写入到磁盘中

- **`fsync`**：适用于需要保证文件内容和元数据都被持久化存储的场景，例如数据库系统在写入重要数据时，要保证数据的完整性和一致性，就会使用 `fsync`。
- **`fdatasync`**：当只关注文件内容的持久化，且元数据的刷新对性能影响较大时，可使用 `fdatasync`。例如，日志文件的写入，通常更关心日志内容是否被正确保存，而对元数据的更新及时性要求不高。

在Java中则是`FileChannel`中的`force`方法

```java
public class Test {
    public static void main(String[] args) {
        try (FileOutputStream fos = new FileOutputStream("test.txt");
             FileChannel channel = fos.getChannel()) {
            String data = "Hello, World!";
            fos.write(data.getBytes());
            fos.flush();
            // 强制将数据和元数据写入磁盘
            channel.force(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```



网络IO中不存在`force`方法，即`flush`方法也只是将数据提交到操作系统内存缓存中，具体的写入网卡时机和发送时机受到多种情况（Nagel算法等）影响

### 顺序IO和随机IO

#### 顺序 I/O

顺序 I/O 是指按照数据在存储介质上的物理顺序依次进行读取或写入操作。就像磁带播放音乐一样，磁头沿着磁带的长度方向依次读取数据，数据的访问顺序与它们在存储介质上的排列顺序一致。

- **数据访问效率高**：当进行大规模连续数据的读写时，顺序 I/O 可以充分利用磁盘的预读机制和缓存技术，减少磁盘寻道时间，从而提高数据传输速度。例如，在对视频文件进行顺序读取时，磁盘可以一次性读取多个连续的磁盘块，大大提高了读取效率。
- **适合顺序存储结构**：对于一些顺序存储的数据结构，如日志文件、顺序排列的数据库表等，顺序 I/O 能够很好地适应其存储和访问方式，保证数据的完整性和一致性。

#### 随机 I/O

随机 I/O 则是指数据的访问顺序是随机的，不按照存储介质上的物理顺序进行读写。类似于在图书馆中随机查找不同书架上的书籍，存储设备需要频繁地移动磁头或进行其他定位操作来访问不同位置的数据（机械硬盘HDD中随机io的时间 = 寻道时间 + 磁盘旋转延迟）。

- **灵活性高**：可以根据需要随时访问存储介质上的任意位置的数据，不受数据存储顺序的限制。这对于需要随机访问数据的应用非常重要，如数据库查询、文件系统的随机访问等。
- **性能开销大**：由于每次随机访问都需要进行磁盘寻道和定位操作，相比顺序 I/O，随机 I/O 的性能开销较大，尤其是在访问大量离散数据时，会导致磁盘 I/O 性能下降。

一个文件会在Linux中被EXT文件系统划分成多个磁盘块(一个磁盘块代表n个磁道)，ext文件系统在操作磁盘时以磁盘块的基本单位进行操作，如果将相关联的数据存放在同一个磁盘块中或者相邻的磁盘块中，那么可以利用顺序IO加快磁盘读取速度，同时也可以利用文件系统缓存磁盘块，避免多次读取磁盘

