# IO

## IO零拷贝

### DMA读写和CPU读写

> **直接内存访问**（**D**irect **M**emory **A**ccess，**DMA**）是[计算机科学](https://zh.wikipedia.org/wiki/计算机科学)中的一种内存访问技术。它允许某些[电脑](https://zh.wikipedia.org/wiki/電腦)内部的硬件子系统（电脑外设），可以独立地直接读写系统[内存](https://zh.wikipedia.org/wiki/記憶體)，而不需[中央处理器](https://zh.wikipedia.org/wiki/中央處理器)（CPU）介入处理 。在同等程度的处理器负担下，DMA是一种快速的数据传送方式。很多硬件的系统会使用DMA，包含[硬盘](https://zh.wikipedia.org/wiki/硬碟)控制器、[绘图显卡](https://zh.wikipedia.org/wiki/繪圖顯示卡)、[网卡](https://zh.wikipedia.org/wiki/网络卡)和[声卡](https://zh.wikipedia.org/wiki/声卡)。

当进行其他设备与内存进行数据读写交互的时候，采用的是DMA拷贝，而平时内存与内存之间的读写交互，采用的是CPU拷贝的方式

### 传统 Linux 中的零拷贝技术

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

在 Linux 中系统调用 sendfile() 可以实现将数据从一个文件描述符传输到另一个文件描述符，从而实现了零拷贝技术。在 Java 中也使用了零拷贝技术，它就是 NIO FileChannel 类中的 transferTo() 方法，transferTo() 底层就依赖了操作系统零拷贝的机制，它可以将数据从 FileChannel 直接传输到另外一个 Channel。transferTo() 方法的定义如下：

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