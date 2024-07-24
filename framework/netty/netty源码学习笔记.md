## Netty源码

### 1.Netty介绍

> Netty is an **asynchronous event-driven** network application framework for **rapid development of maintainable high performance protocol servers & clients.**
>
> Netty 是一个**异步事件驱动**的网络应用程序框架，用于快速开发**可维护的高性能协议服务器和客户端**。

![img](https://netty.io/images/components.png)

<img src=".\images\image-20240624111124224.png" alt="image-20240624111124224" style="zoom: 33%;" />

时延：服务器处理一个请求所需要的时间。

吞吐量：单位时间内（每秒/每分） 服务器对多个请求的响应数量。 例如来了100个请求，每秒响应了50个，那么这个服务器的吞吐量就是50.

QPS：设置吞吐量单位为秒的 就是QPS。例如来了100个请求，每秒响应了50个，那么这个服务器的QPS就是50.

### 2.IO

InputStream和OutputStream是java的流接口，其中的各个子类通过不同的native实现来调用不同的操作系统api（即系统调用）来实现不同功能

BufferedInputStream 是 FilterInputStream 的继承子类，不局限于文件IO，可以针对于不同流的缓冲读

如果不重写InputStream的read(byte b[])方法 ，那么依旧是循环调用系统调用的read读取，并没有减少系统调用的次数，只不过在你想读一个字节的时候，它帮你读了DEFAULT_BUFFER_SIZE大小的字节缓存在java中的byte[]数组中，下次可以直接从数组中获取。

但是被包在BufferedInputStream 的FilterInputStream 实际上重写了read(byte b[])方法 ，在底层c语言中使用了批量读取文件字节的系统调用，所以就大大减少了系统调用的次数，缓存在java中的byte[]数组中。



**文件IO**

FilterInputStream 是 InputStream的实现 其中调用read方法是，native中调用了操作系统api中对文件的读取api

```java
public class FileIO {
    public static void main(String[] args) throws IOException {
        FileInputStream fileInputStream = new FileInputStream("D:\\code\\nettystudycode\\src\\main\\resources\\fileio.txt");
        int read = fileInputStream.read();
        System.out.println(read);
    }
}
```

JVM和内核做交互的时候，在各自的内存区域开辟了一块缓存区来存储读写的数据，malloc是jvm源码中开辟JVM的内存缓存区的方法，IO_Read则负责将操作系统的数据缓存区的数据读取到JVM的缓存区中，最后通过SetByteArrayRegion来将JVM的缓存区的数据搬到堆内存的数组中去。下图是调用FileInputSteam.readBytes()方法时 navtive方法做的事情。

![](.\images\readBytes原理.png)





**BIO**







**NIO**

Buffer 、Channel、Selector

Buffer 是装载字节数据的，Channel是读写数据的操作通道，Selector则是选择器

只有继承了SelectableChannel的Channel才能具有选择的能力，文件IO不具备选择的能力





NIO是个模型 底层依然是inputstream那一套东西，只是把jvm和操作系统进行交互的过程抽象模型化成第二个图片的样子

<img src=".\images\image-20240624172408016.png" alt="image-20240624172408016" style="zoom:50%;" />

JVM和操作系统进行交互的写入和读取操作被抽象成了Channel，而在其中传输的数据则被抽象成了Buffer 

<img src=".\images\image-20240625084217786.png" alt="image-20240625084217786" style="zoom:50%;" />

```java
//NIO模型代码佐证

    	FileInputStream fileInputStream = new FileInputStream("D:\\code\\nettystudycode\\src\\main\\resources\\fileio.txt");
        FileChannel channel = fileInputStream.getChannel();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1);
		//利用channel来读取指定字节大小的文件数据
        channel.read(byteBuffer);

//channel.read的实际实现类
public class FileChannelImpl
    extends FileChannel
{
    
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!readable)
            throw new NonReadableChannelException();
        synchronized (positionLock) {
            int n = 0;
            int ti = -1;
            try {
                begin();
                ti = threads.add();
                if (!isOpen())
                    return 0;
                do {
                    //这里进行了实际的读取操作
                    n = IOUtil.read(fd, dst, -1, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                threads.remove(ti);
                end(n > 0);
                assert IOStatus.check(n);
            }
        }
    }
}

//继续深入IOUtil
public class IOUtil {
    
    static int read(FileDescriptor fd, ByteBuffer dst, long position,
                    NativeDispatcher nd)
        throws IOException
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        //可以看到 如果读取的buffer是DirectBuffer 那么直接从内核读取到DirectBuffer中去
        if (dst instanceof DirectBuffer)
            return readIntoNativeBuffer(fd, dst, position, nd);

        // Substitute a native buffer
        //否则是一个HeapBuffer 那么就会先创建一个临时的DirectBuffer来中转数据
        ByteBuffer bb = Util.getTemporaryDirectBuffer(dst.remaining());
        try {
            //先读取到DirectBuffer中去
            int n = readIntoNativeBuffer(fd, bb, position, nd);
            bb.flip();
            if (n > 0)
                //然后再放入HeapBuffer
                dst.put(bb);
            return n;
        } finally {
            Util.offerFirstTemporaryDirectBuffer(bb);
        }
    }
}
   
//由此我们佐证了一个结论 DirectBuffer在JVM的上下文层面的确完成了零拷贝的任务 减少了创建临时DirectBuffer来中转数据的消耗 
//而HeapBuffer也确实需要先从内核搬到JVM的临时DirectBuffer中 然后再搬到堆内的HeapBuffer中 
//并且NIO的底层也确实采用的和普通IO相同的模型进行读取数据
```



### 3.Buffer

Buffer是ByteBuffer的父类，继承关系如下

<img src=".\images\image-20240626133842813.png" alt="image-20240626133842813" style="zoom:33%;" />

HeapByteBuffer是在JVM堆空间中开辟的数组，而DirectByteBuffer则是在整个JVM进程中的堆外空间中开辟的缓存区，直接内存是采用Unsafe类通过进行开辟的，DirectByteBuffer和HeapByteBuffer都有四个指针，

```java
public abstract class Buffer {
    // Invariants: mark <= position <= limit <= capacity
    private int mark = -1;
    private int position = 0;
    private int limit;
    private int capacity;

    // Used only by direct buffers
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    long address;
}
```

Mark代表标志指针，用于用户随意放置标志下标索引的，Position则是read时当前指针的所指向位置，Limit是调用flip()方法时，Position归零，limit来指向Position位置的指针，Capacity则代表整个buffer的大小，不会变化。

![](.\images\NIO Buffer原理图.png)

```java
//接下来对DirectByteBuffer的部分关键代码进行分析

class DirectByteBuffer extends MappedByteBuffer implements DirectBuffer
{
    // Cached array base offset
    //缓存下来byte数组的第一个元素和数组首地址的偏移量
    private static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    // Cached unaligned-access capability
    // 缓存未是否未对齐？？？
    protected static final boolean UNALIGNED = Bits.unaligned();

    //继承了Runnable的用于在当前对象被回收后，配合回收JVM堆外空间
    private static class Deallocator
        implements Runnable
    {
		//堆外空间地址
        private long address;
        //堆外开辟的大小
        private long size;
        //buffer大小
        private int capacity;

        private Deallocator(long address, long size, int capacity) {
            assert (address != 0);
            this.address = address;
            this.size = size;
            this.capacity = capacity;
        }

        public void run() {
            if (address == 0) {
                // Paranoia
                return;
            }
            //利用Unsafe类释放堆外空间
            UNSAFE.freeMemory(address);
            address = 0;
            Bits.unreserveMemory(size, capacity);
        }

    }
	
    //用于对象被回收时，释放堆外空间的清洁器
    private final Cleaner cleaner;

    public Cleaner cleaner() { 
        return cleaner; 
    }


    // Primary constructor
    //
    DirectByteBuffer(int cap) {                   // package-private
		//通过调用父类的构造方法设置mark,pos,limit和capcity
        super(-1, 0, cap, cap);
        boolean pa = VM.isDirectMemoryPageAligned();
        int ps = Bits.pageSize();
        long size = Math.max(1L, (long)cap + (pa ? ps : 0));
        Bits.reserveMemory(size, cap);

        long base = 0;
        try {
            //通过unsafe类开辟堆外空间
            base = UNSAFE.allocateMemory(size);
        } catch (OutOfMemoryError x) {
            Bits.unreserveMemory(size, cap);
            throw x;
        }
        //对这片堆外空间的每个比特位全部置0
        UNSAFE.setMemory(base, size, (byte) 0);
        if (pa && (base % ps != 0)) {
            // Round up to page boundary
            address = base + ps - (base & (ps - 1));
        } else {
            address = base;
        }
        //初始化清洁器 把释放堆外空间的任务初始化好
        //把当前这个DirectByteBuffer对象和清洁器放入Cleaner中
        cleaner = Cleaner.create(this, new Deallocator(base, size, cap));
        att = null;
    }
}
    
//看一下Cleaner类    
//Cleaner继承了虚引用
//虚引用的特点之二就是 虚引用必须与ReferenceQueue一起使用，当GC准备回收一个对象，如果发现它还有虚引用，就会在回收之前，把这个虚引用加入到与之关联的ReferenceQueue中。
public class Cleaner extends PhantomReference<Object>
{
    // Dummy reference queue, needed because the PhantomReference constructor
    // insists that we pass a queue.  Nothing will ever be placed on this queue
    // since the reference handler invokes cleaners explicitly.
    //
    private static final ReferenceQueue<Object> dummyQueue = new ReferenceQueue<>();

    // Doubly-linked list of live cleaners, which prevents the cleaners
    // themselves from being GC'd before their referents
    //
    static private Cleaner first = null;

    private Cleaner(Object referent, Runnable thunk) {
        //加入PhantomReference中，引用和对应的ReferenceQueue队列中去
        super(referent, dummyQueue);
        this.thunk = thunk;
    }
    public static Cleaner create(Object ob, Runnable thunk) {
        if (thunk == null)
            return null;
          //把Cleaner对象加入到关联的ReferenceQueue中去
        return add(new Cleaner(ob, thunk));
    }
    
     private static synchronized Cleaner add(Cleaner cl) {
         //维护当前的Cleaner链表
        if (first != null) {
            cl.next = first;
            first.prev = cl;
        }
        first = cl;
        return cl;
    }

}

//在Reference类中 会对虚引用进行调用clean进行清理堆外空间
public abstract class Reference<T> {

   static boolean tryHandlePending(boolean waitForNotify) {
        Reference<Object> r;
        Cleaner c;
        try {
            synchronized (lock) {
                if (pending != null) {
                    r = pending;
                    // 'instanceof' might throw OutOfMemoryError sometimes
                    // so do this before un-linking 'r' from the 'pending' chain...
                    c = r instanceof Cleaner ? (Cleaner) r : null;
                    // unlink 'r' from 'pending' chain
                    pending = r.discovered;
                    r.discovered = null;
                } else {
                    // The waiting on the lock may cause an OutOfMemoryError
                    // because it may try to allocate exception objects.
                    if (waitForNotify) {
                        lock.wait();
                    }
                    // retry if waited
                    return waitForNotify;
                }
            }
        } catch (OutOfMemoryError x) {
            // Give other threads CPU time so they hopefully drop some live references
            // and GC reclaims some space.
            // Also prevent CPU intensive spinning in case 'r instanceof Cleaner' above
            // persistently throws OOME for some time...
            Thread.yield();
            // retry
            return true;
        } catch (InterruptedException x) {
            // retry
            return true;
        }

       //如果这个引用是Cleaner 那么调用它的clean方法 
       //堆外空间的清除也是在这里
        // Fast path for cleaners
        if (c != null) {
            c.clean();
            return true;
        }

        ReferenceQueue<? super Object> q = r.queue;
        if (q != ReferenceQueue.NULL) q.enqueue(r);
        return true;
    }
}
    
    

```



```java
//对HeapByteBuffer进行分析
class HeapByteBuffer extends ByteBuffer
{
    // For speed these fields are actually declared in X-Buffer;
    // these declarations are here as documentation
    /*

    protected final byte[] hb;
    protected final int offset;

    */

    //没什么特别的 只是利用了byte[cap]这个数组当作buffer
    HeapByteBuffer(int cap, int lim) {            // package-private

        super(-1, 0, lim, cap, new byte[cap], 0);
        /*
        hb = new byte[cap];
        offset = 0;
        */

    }
    //读写也只是通过下标索引读数组
   public byte get() {
        return hb[ix(nextGetIndex())];
    }
}


```

### 4.延申Unsafe类的一些知识

Unsafe类中 arrayBaseOffset()是用于获取指定数组类型的第一个元素距离数组首地址的偏移量，因为java的数组类型可能含有头部长度、类型等信息，所以实际的首地址距离第一个元素的地址有一定偏移。arrayIndexScale则是获取指定数组元素的每个元素占用的字节数。

```java
//有了第一个元素的地址偏移量和每个元素占用的字节大小，就可以通过地址偏移量操作任意一个数组中的元素
public static void main(String[] args) throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);

        int[] arr = new int[]{1, 2, 3, 4, 5};
		
    	//第一个元素的偏移量
        int arrayBaseOffset = unsafe.arrayBaseOffset(int[].class);
    	//每一个int的偏移量 这里是4个字节
        int arrayIndexScale = unsafe.arrayIndexScale(int[].class);
        //操作arr数组的第三个元素地址，写一个200的int数字 4个字节进去
        unsafe.putInt(arr, arrayBaseOffset + arrayIndexScale * 3L, 200);

}
```

Unsafe类中allocateMemory()方法用于分配指定大小的空间，返回当前空间的首地址，setMemory()用于将指定的byte放在指定地址中，放几个字节

```java
		//分配4个字节大小的空间 返回这个空间的首地址
        long allocateMemory = unsafe.allocateMemory(4);
		//通过putInt方法在指定地址位置放4个字节的数字进去
        unsafe.putInt(allocateMemory, 200);
		//在指定地址读4个字节当作数字出来
        int anInt = unsafe.getInt(allocateMemory);
        System.out.println("读出来的数字:" + anInt);//200
		//在指定地址位置 放一个68的二进制进去
        unsafe.setMemory(allocateMemory, 1, new Integer(68).byteValue());
        System.out.println("读出来的数字:" + unsafe.getInt(allocateMemory));//68
		//而3则是代表顺序写三个字节位 68这个数字的最后一个字节进去
		//1000100
		//10001000,10001000,10001000,1000100
		//就像这样 不足的位补0 
 		unsafe.setMemory(allocateMemory, 3, new Integer(68).byteValue());
		System.out.println("读出来的数字:" + unsafe.getInt(allocateMemory));//1145324612
		

		//如果放置的数字大于一个字节255的上限，那么就会截断，只取最后一个字节写入，
	    unsafe.setMemory(allocateMemory, 1, new Integer(377).byteValue());//读出来的数字:31097 写一个字节 读四个字节 后三个全是0 所以变大了

```

这里引申出一个大小端的问题，Java的读都是大端排列结果，屏蔽了底层机器的大小端差异，这里举一个例子，我的机器是小端

```java
        ByteOrder order = ByteOrder.nativeOrder();
        System.out.println(order);      	//LITTLE_ENDIAN 小端

		int x = 0x0104;
        System.out.println(Integer.toBinaryString(x));
		//通过Integer.reverseBytes方法可以反转字节 以字节为单位反转 而不是bit
        System.out.println(Integer.toBinaryString(Integer.reverseBytes(x)));
        //00000000,00000000,00000001,00000100 大端排列
        //00000100,00000001,00000000,00000000 小端排列

//由此可以看到 即使我的机器是小端 正常情况下并没有打印出如上的小端排列 这是因为java做了兼容 对小端机器进行了大端排序处理
```

又引出了一个有趣的问题，当写入字节到内存中的时候，到底是用Java的大端还是机器的小端呢？

```java
   		//申请一块4个字节的空间	
		long allocateMemory = unsafe.allocateMemory(4);
		//写入一个不超过4个字节的数字
		//即 1110111001101011001010000000000
        unsafe.putInt(allocateMemory, 2000000000);
		//再写入一个68数字的一个字节的数据 即 01000100
        unsafe.setMemory(allocateMemory, 1, new Integer(68).byteValue());
		//理想中 这个68的一个字节数据会写入到四个字节的第一个字节的位置 形成 01000100 01101011001010000000000 这样的拼接的东西
        System.out.println("读出来的数字:" + unsafe.getInt(allocateMemory));
		//可实质上 读出的数字是 2000000068 
		//对比来看一下
		//1110111001101011001010000000000 2000000000
		//1110111001101011001010001000100 2000000068
		//                       01000100  68
		//也就是说 68被直接写到了最后一个字节位置上 
		//那么证明这台机器其实是小端排列 最后一个字节在第一个位置 写入的时候覆盖了第一个字节 读取的时候Java做了大小端屏蔽 统一大端输出 所以才出现了这样的结果
```





### 5.事件循环组

实际上的事件循环组指的是EventLoopGroup 这个接口，但是为了介绍这个接口具有的能力 我们需要从它的所有继承父类来看，而事件循环组中的具体执行任务的应该指的是EventLoop这个接口，这就是我理解的在组和成员的定义中的两大顶层接口。

<img src=".\images\image-20240703163835885.png" alt="image-20240703163835885" style="zoom:50%;" />



#### 事件循环组流程图

![](.\images\事件循环组流程.png)







#### 公共继承

##### Executor

事件循环组的祖先就是以Executor的接口，提供了异步执行的能力

 ```java
 //这个类是java自带的接口 所有继承它的子类代表拥有了异步执行的能力
 package java.util.concurrent;
 
 public interface Executor {
 
     /**
      * Executes the given command at some time in the future.  The command
      * may execute in a new thread, in a pooled thread, or in the calling
      * thread, at the discretion of the {@code Executor} implementation.
      *
      * @param command the runnable task
      * @throws RejectedExecutionException if this task cannot be
      * accepted for execution
      * @throws NullPointerException if command is null
      */
     void execute(Runnable command);
 }
 ```

##### ExecutorService

作为Executor的继承接口，则对Executor接口的异步能力做了加强和扩展，支持了异步执行器的常见操作

```java
package java.util.concurrent;

//执行器服务
//这里只举例几个方法 具体代码需要自行查看 本质就是对执行器的常见操作的定义
public interface ExecutorService extends Executor {

    //关闭执行器
    void shutdown();
    
    List<Runnable> shutdownNow();

    boolean isShutdown();
 
    boolean isTerminated();
    //提交任务
    Future<?> submit(Runnable task);
   	//提交多个任务
    <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;
}
```

##### ScheduledExecutorService

ScheduledExecutorService则继承了ExecutorService，定义了一些周期性执行任务的能力

```java
package java.util.concurrent;

//周期性执行任务的能力接口定义
public interface ScheduledExecutorService extends ExecutorService {


    //指定时间后执行任务
    public ScheduledFuture<?> schedule(Runnable command,
                                       long delay, TimeUnit unit);


    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                           long delay, TimeUnit unit);

	//固定频率执行任务
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit);

	//周期性调度任务
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit);

}
```

##### EventExecutorGroup

EventExecutorGroup 是事件执行器组的定义接口 继承了ScheduledExecutorService ，拥有周期性执行任务的能力

```java
package io.netty.util.concurrent;

//从这个接口开始的组定义都是netty的

//负责EventExecutorGroup通过其next()方法提供EventExecutor要使用的。除此之外，它还负责处理它们的生命周期，并允许以全局方式关闭它们。
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

   //当且仅当由此EventExecutorGroup管理的所有 EventExecutors 都正常关闭或已关闭时，才返回true。
    boolean isShuttingDown();

   //优雅关闭执行器
    Future<?> shutdownGracefully();

   	//返回Future当 this EventExecutorGroup 管理的所有 EventExecutors 都已终止时通知的 which。
    Future<?> terminationFuture();

    //返回一个事件执行器组中管理的执行器 这里就体现了事件循环组的负载均衡能力
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}

```



#### EventLoopGroup组的相关类

EventLoopGroup是事件循环组的定义接口

##### EventLoopGroup

```java
/**
 * Special {@link EventExecutorGroup} which allows registering {@link Channel}s that get
 * processed for later selection during the event loop.
 
 * EventLoopGroup继承了EventExecutorGroup接口 具有了执行器的组管理能力
   这个接口实际上和事件执行器组的能力没有什么区别 都是管理执行器的 只不过多了一些channel的注册功能 可以只把它当成执行器组看待
 */
public interface EventLoopGroup extends EventExecutorGroup {
    /**
     * Return the next {@link EventLoop} to use
     */
    //返回一个EventLoop事件循环来使用 
    //这里根据继承的子类不同 Group的组继承类实现的时候会利用chooser来负载均衡一个组内管理的EventLoop返回
    //如果继承类是 成员的EventLoop派系的 那么只会返回它自己
    @Override
    EventLoop next();

    /**
     * Register a {@link Channel} with this {@link EventLoop}. The returned {@link ChannelFuture}
     * will get notified once the registration was complete.
     */
    ChannelFuture register(Channel channel);

    /**
     * Register a {@link Channel} with this {@link EventLoop} using a {@link ChannelFuture}. The passed
     * {@link ChannelFuture} will get notified once the registration was complete and also will get returned.
     */
    ChannelFuture register(ChannelPromise promise);

    /**
     * Register a {@link Channel} with this {@link EventLoop}. The passed {@link ChannelFuture}
     * will get notified once the registration was complete and also will get returned.
     *
     * @deprecated Use {@link #register(ChannelPromise)} instead.
     */
    @Deprecated
    ChannelFuture register(Channel channel, ChannelPromise promise);
}
```



##### AbstractEventExecutorGroup

AbstractEventExecutorGroup是EventExecutorGroup 抽象模板实现 只是一个模板 没有什么特殊实现

```java
package io.netty.util.concurrent;

/**
 * Abstract base class for {@link EventExecutorGroup} implementations.
 */
public abstract class AbstractEventExecutorGroup implements EventExecutorGroup {
    @Override
    public Future<?> submit(Runnable task) {
        return next().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return next().submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return next().submit(task);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return next().schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return next().schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return next().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return next().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public abstract void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return next().invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return next().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return next().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return next().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        next().execute(command);
    }
}
```



##### MultithreadEventExecutorGroup

MultithreadEventExecutorGroup是实现 EventExecutorGroup 的抽象基类，用于同时处理具有多个线程的任务的能力 到这里才算是事件执行器组的部分具体实现

```java
package io.netty.util.concurrent;

public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    //事件执行器组管理的事件执行器
    private final EventExecutor[] children;
    //只读的执行器集合
    private final Set<EventExecutor> readonlyChildren;
    private final AtomicInteger terminatedChildren = new AtomicInteger();
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
    //组内进行负载均衡选择事件执行器的选择器
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) {
            //executor是外部传入的用于创建线程的工厂 ？？？？
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }
		
        //创建管理执行器的数组
        children = new EventExecutor[nThreads];

        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                //创建各个执行器
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
		
        //初始化选择器
        chooser = chooserFactory.newChooser(children);

        //终止时的监听器
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                //当终止的执行器数量等于总执行器数量时
                if (terminatedChildren.incrementAndGet() == children.length) {
                    //终止promise设置完成
                    terminationFuture.setSuccess(null);
                }
            }
        };
		
        //将终止时的监听器加入到终止Promise中 用于终止时回调执行
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        //将执行器列表加入不可变集合中
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    //创建默认的线程创建工厂
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }

    //执行器组实现的next方法就会利用chooser进行负载均衡选择执行器
    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     */
    //抽象方法 需要子类来实现具体的创建执行器的方法
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

}
```



##### MultithreadEventLoopGroup



```java

//实现 EventLoopGroup 的抽象基类，用于同时处理具有多个线程的任务 实现了EventLoopGroup的register等相关方法
public abstract class MultithreadEventLoopGroup extends MultithreadEventExecutorGroup implements EventLoopGroup {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MultithreadEventLoopGroup.class);
	//默认管理的执行器数量
    private static final int DEFAULT_EVENT_LOOP_THREADS;

    static {
        //这里将默认的执行器数量设置为CPU核心数量*2 是为了能够充分利用CPU资源？？
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }

    /**
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
    }

    /**
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, ThreadFactory, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, threadFactory, args);
    }

    /**
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor,
     * EventExecutorChooserFactory, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                     Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, chooserFactory, args);
    }

    //线程创建的工厂
    @Override
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass(), Thread.MAX_PRIORITY);
    }
    
    
	//调用super.next();这里就是调用的MultithreadEventExecutorGroup的next()方法 利用chooser进行选择执行器
    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    //子类实现执行器的创建工作
    @Override
    protected abstract EventLoop newChild(Executor executor, Object... args) throws Exception;

    //通过next()拿到执行器绑定注册channel
    //模板方法
    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return next().register(promise);
    }

    @Deprecated
    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }

}
```



##### NioEventLoopGroup

```java
package io.netty.channel.nio;


//MultithreadEventLoopGroup用于基于 Channel的NIO Selector 的实现。
public class NioEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance using the default number of threads, the default {@link ThreadFactory} and
     * the {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance using the specified number of threads, {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
    }

    /**
     * Create a new instance using the default number of threads, the given {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(ThreadFactory threadFactory) {
        this(0, threadFactory, SelectorProvider.provider());
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * {@link SelectorProvider}.
     */
    public NioEventLoopGroup(
            int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        this(nThreads, threadFactory, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory,
        final SelectorProvider selectorProvider, final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, threadFactory, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(
            int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory,
                             final RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory, rejectedExecutionHandler);
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory,
                             final RejectedExecutionHandler rejectedExecutionHandler,
                             final EventLoopTaskQueueFactory taskQueueFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                rejectedExecutionHandler, taskQueueFactory);
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     
     设置子事件循环中 I/ O 所花费的所需时间的百分比。默认值为 50，这意味着事件循环将尝试在 I/ O 上花费与非 I/ O 任务相同的时间。
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).setIoRatio(ioRatio);
        }
    }

    /**
     * Replaces the current {@link Selector}s of the child event loops with newly created {@link Selector}s to work
     * around the  infamous epoll 100% CPU bug.
     */
    public void rebuildSelectors() {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).rebuildSelector();
        }
    }

    //实现MultithreadEventExecutorGroup的newChild方法
    //Executor是创建线程的工厂 args则是 SelectorProvider SelectStrategyFactory RejectedExecutionHandler
    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        EventLoopTaskQueueFactory queueFactory = args.length == 4 ? (EventLoopTaskQueueFactory) args[3] : null;
        return new NioEventLoop(this, executor, (SelectorProvider) args[0],
            ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2], queueFactory);
    }
}
```











#### EventLoop成员的相关类



##### EventLoop

EventLoop实际上和EventExecutor没有区别 所以可以把它当作组内的事件执行器看待

```java

//继承了EventLoopGroup 代表是EventLoopGroup的组内成员
//继承的OrderedEventExecutor才是重点 代表拥有了 事件执行器的能力

public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    //返回组对象
    @Override
    EventLoopGroup parent();
}
```



##### OrderedEventExecutor

```java
//OrderedEventExecutor只是简单做了个顺序的标记 
public interface OrderedEventExecutor extends EventExecutor {
}
```



##### EventExecutor

EventExecutor继承了EventExecutorGroup接口，EventExecutor是EventExecutorGroup事件执行器组的管理的事件执行器的定义接口 

```java


public interface EventExecutor extends EventExecutorGroup {

    /**
     * Returns a reference to itself.
     返回它自己 因为它只是一个事件执行器
     */
    @Override
    EventExecutor next();

    /**
     * Return the {@link EventExecutorGroup} which is the parent of this {@link EventExecutor},
     返回当前事件执行器所在的事件执行器组
     */
    EventExecutorGroup parent();

    /**
     * Calls {@link #inEventLoop(Thread)} with {@link Thread#currentThread()} as argument
    	
     */
    boolean inEventLoop();

    /**
     * Return {@code true} if the given {@link Thread} is executed in the event loop,
     * {@code false} otherwise.
     */
    boolean inEventLoop(Thread thread);

    /**
     * Return a new {@link Promise}.
     */
    <V> Promise<V> newPromise();

    /**
     * Create a new {@link ProgressivePromise}.
     */
    <V> ProgressivePromise<V> newProgressivePromise();

    /**
     * Create a new {@link Future} which is marked as succeeded already. So {@link Future#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    <V> Future<V> newSucceededFuture(V result);

    /**
     * Create a new {@link Future} which is marked as failed already. So {@link Future#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    <V> Future<V> newFailedFuture(Throwable cause);
}
```

##### NioEventLoop 

NioEventLoop 继承了 SingleThreadEventExecutor，具有了

<img src=".\images\image-20240703093045714.png" alt="image-20240703093045714" style="zoom:50%;" />



```java
package io.netty.channel.nio;

//NioEventLoopGroup中的成员 实际执行任务的对象
public final class NioEventLoop extends SingleThreadEventLoop {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioEventLoop.class);

    private static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    private static final boolean DISABLE_KEY_SET_OPTIMIZATION =
            SystemPropertyUtil.getBoolean("io.netty.noKeySetOptimization", false);

    private static final int MIN_PREMATURE_SELECTOR_RETURNS = 3;
    private static final int SELECTOR_AUTO_REBUILD_THRESHOLD;

    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return selectNow();
        }
    };
    
    private Selector selector;
    private Selector unwrappedSelector;
    private SelectedSelectionKeySet selectedKeys;

    private final SelectorProvider provider;

    private static final long AWAKE = -1L;
    private static final long NONE = Long.MAX_VALUE;

    // nextWakeupNanos is:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);

    private final SelectStrategy selectStrategy;

    private volatile int ioRatio = 50;
    private int cancelledKeys;
    private boolean needsToSelectAgain;
    
    
    
      NioEventLoop(NioEventLoopGroup parent, Executor executor, SelectorProvider selectorProvider,
                 SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler,
                 EventLoopTaskQueueFactory queueFactory) {
        super(parent, executor, false, newTaskQueue(queueFactory), newTaskQueue(queueFactory),
                rejectedExecutionHandler);
        this.provider = ObjectUtil.checkNotNull(selectorProvider, "selectorProvider");
        this.selectStrategy = ObjectUtil.checkNotNull(strategy, "selectStrategy");
        final SelectorTuple selectorTuple = openSelector();
        this.selector = selectorTuple.selector;
        this.unwrappedSelector = selectorTuple.unwrappedSelector;
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }
private SelectorTuple openSelector() {
        final Selector unwrappedSelector;
        try {
            unwrappedSelector = provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }

        if (DISABLE_KEY_SET_OPTIMIZATION) {
            return new SelectorTuple(unwrappedSelector);
        }

        Object maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    return Class.forName(
                            "sun.nio.ch.SelectorImpl",
                            false,
                            PlatformDependent.getSystemClassLoader());
                } catch (Throwable cause) {
                    return cause;
                }
            }
        });

        if (!(maybeSelectorImplClass instanceof Class) ||
            // ensure the current selector implementation is what we can instrument.
            !((Class<?>) maybeSelectorImplClass).isAssignableFrom(unwrappedSelector.getClass())) {
            if (maybeSelectorImplClass instanceof Throwable) {
                Throwable t = (Throwable) maybeSelectorImplClass;
                logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, t);
            }
            return new SelectorTuple(unwrappedSelector);
        }

        final Class<?> selectorImplClass = (Class<?>) maybeSelectorImplClass;
        final SelectedSelectionKeySet selectedKeySet = new SelectedSelectionKeySet();

        Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    Field selectedKeysField = selectorImplClass.getDeclaredField("selectedKeys");
                    Field publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys");

                    if (PlatformDependent.javaVersion() >= 9 && PlatformDependent.hasUnsafe()) {
                        // Let us try to use sun.misc.Unsafe to replace the SelectionKeySet.
                        // This allows us to also do this in Java9+ without any extra flags.
                        long selectedKeysFieldOffset = PlatformDependent.objectFieldOffset(selectedKeysField);
                        long publicSelectedKeysFieldOffset =
                                PlatformDependent.objectFieldOffset(publicSelectedKeysField);

                        if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                            PlatformDependent.putObject(
                                    unwrappedSelector, selectedKeysFieldOffset, selectedKeySet);
                            PlatformDependent.putObject(
                                    unwrappedSelector, publicSelectedKeysFieldOffset, selectedKeySet);
                            return null;
                        }
                        // We could not retrieve the offset, lets try reflection as last-resort.
                    }

                    Throwable cause = ReflectionUtil.trySetAccessible(selectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }
                    cause = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }

                    selectedKeysField.set(unwrappedSelector, selectedKeySet);
                    publicSelectedKeysField.set(unwrappedSelector, selectedKeySet);
                    return null;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                }
            }
        });

        if (maybeException instanceof Exception) {
            selectedKeys = null;
            Exception e = (Exception) maybeException;
            logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, e);
            return new SelectorTuple(unwrappedSelector);
        }
        selectedKeys = selectedKeySet;
        logger.trace("instrumented a special java.util.Set into: {}", unwrappedSelector);
        return new SelectorTuple(unwrappedSelector,
                                 new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet));
    }

    /**
     * Returns the {@link SelectorProvider} used by this {@link NioEventLoop} to obtain the {@link Selector}.
     */
    public SelectorProvider selectorProvider() {
        return provider;
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }
    
     public void register(final SelectableChannel ch, final int interestOps, final NioTask<?> task) {
        ObjectUtil.checkNotNull(ch, "ch");
        if (interestOps == 0) {
            throw new IllegalArgumentException("interestOps must be non-zero.");
        }
        if ((interestOps & ~ch.validOps()) != 0) {
            throw new IllegalArgumentException(
                    "invalid interestOps: " + interestOps + "(validOps: " + ch.validOps() + ')');
        }
        ObjectUtil.checkNotNull(task, "task");

        if (isShutdown()) {
            throw new IllegalStateException("event loop shut down");
        }

        if (inEventLoop()) {
            register0(ch, interestOps, task);
        } else {
            try {
                // Offload to the EventLoop as otherwise java.nio.channels.spi.AbstractSelectableChannel.register
                // may block for a long time while trying to obtain an internal lock that may be hold while selecting.
                submit(new Runnable() {
                    @Override
                    public void run() {
                        register0(ch, interestOps, task);
                    }
                }).sync();
            } catch (InterruptedException ignore) {
                // Even if interrupted we did schedule it so just mark the Thread as interrupted.
                Thread.currentThread().interrupt();
            }
        }
    }
    
    
     @Override
    protected void run() {
        int selectCnt = 0;
        for (;;) {
            try {
                int strategy;
                try {
                    strategy = selectStrategy.calculateStrategy(selectNowSupplier, hasTasks());
                    switch (strategy) {
                    case SelectStrategy.CONTINUE:
                        continue;

                    case SelectStrategy.BUSY_WAIT:
                        // fall-through to SELECT since the busy-wait is not supported with NIO

                    case SelectStrategy.SELECT:
                        long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
                        if (curDeadlineNanos == -1L) {
                            curDeadlineNanos = NONE; // nothing on the calendar
                        }
                        nextWakeupNanos.set(curDeadlineNanos);
                        try {
                            if (!hasTasks()) {
                                strategy = select(curDeadlineNanos);
                            }
                        } finally {
                            // This update is just to help block unnecessary selector wakeups
                            // so use of lazySet is ok (no race condition)
                            nextWakeupNanos.lazySet(AWAKE);
                        }
                        // fall through
                    default:
                    }
                } catch (IOException e) {
                    // If we receive an IOException here its because the Selector is messed up. Let's rebuild
                    // the selector and retry. https://github.com/netty/netty/issues/8566
                    rebuildSelector0();
                    selectCnt = 0;
                    handleLoopException(e);
                    continue;
                }

                selectCnt++;
                cancelledKeys = 0;
                needsToSelectAgain = false;
                final int ioRatio = this.ioRatio;
                boolean ranTasks;
                if (ioRatio == 100) {
                    try {
                        if (strategy > 0) {
                            processSelectedKeys();
                        }
                    } finally {
                        // Ensure we always run tasks.
                        ranTasks = runAllTasks();
                    }
                } else if (strategy > 0) {
                    final long ioStartTime = System.nanoTime();
                    try {
                        processSelectedKeys();
                    } finally {
                        // Ensure we always run tasks.
                        final long ioTime = System.nanoTime() - ioStartTime;
                        ranTasks = runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                    }
                } else {
                    ranTasks = runAllTasks(0); // This will run the minimum number of tasks
                }

                if (ranTasks || strategy > 0) {
                    if (selectCnt > MIN_PREMATURE_SELECTOR_RETURNS && logger.isDebugEnabled()) {
                        logger.debug("Selector.select() returned prematurely {} times in a row for Selector {}.",
                                selectCnt - 1, selector);
                    }
                    selectCnt = 0;
                } else if (unexpectedSelectorWakeup(selectCnt)) { // Unexpected wakeup (unusual case)
                    selectCnt = 0;
                }
            } catch (CancelledKeyException e) {
                // Harmless exception - log anyway
                if (logger.isDebugEnabled()) {
                    logger.debug(CancelledKeyException.class.getSimpleName() + " raised by a Selector {} - JDK bug?",
                            selector, e);
                }
            } catch (Error e) {
                throw (Error) e;
            } catch (Throwable t) {
                handleLoopException(t);
            } finally {
                // Always handle shutdown even if the loop processing threw an exception.
                try {
                    if (isShuttingDown()) {
                        closeAll();
                        if (confirmShutdown()) {
                            return;
                        }
                    }
                } catch (Error e) {
                    throw (Error) e;
                } catch (Throwable t) {
                    handleLoopException(t);
                }
            }
        }
    }
    
    
      // returns true if selectCnt should be reset
    private boolean unexpectedSelectorWakeup(int selectCnt) {
        if (Thread.interrupted()) {
            // Thread was interrupted so reset selected keys and break so we not run into a busy loop.
            // As this is most likely a bug in the handler of the user or it's client library we will
            // also log it.
            //
            // See https://github.com/netty/netty/issues/2426
            if (logger.isDebugEnabled()) {
                logger.debug("Selector.select() returned prematurely because " +
                        "Thread.currentThread().interrupt() was called. Use " +
                        "NioEventLoop.shutdownGracefully() to shutdown the NioEventLoop.");
            }
            return true;
        }
        if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&
                selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
            // The selector returned prematurely many times in a row.
            // Rebuild the selector to work around the problem.
            logger.warn("Selector.select() returned prematurely {} times in a row; rebuilding Selector {}.",
                    selectCnt, selector);
            rebuildSelector();
            return true;
        }
        return false;
    }
    
        private void processSelectedKeys() {
        if (selectedKeys != null) {
            processSelectedKeysOptimized();
        } else {
            processSelectedKeysPlain(selector.selectedKeys());
        }
    }
private void processSelectedKeysOptimized() {
        for (int i = 0; i < selectedKeys.size; ++i) {
            final SelectionKey k = selectedKeys.keys[i];
            // null out entry in the array to allow to have it GC'ed once the Channel close
            // See https://github.com/netty/netty/issues/2363
            selectedKeys.keys[i] = null;

            final Object a = k.attachment();

            if (a instanceof AbstractNioChannel) {
                processSelectedKey(k, (AbstractNioChannel) a);
            } else {
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                processSelectedKey(k, task);
            }

            if (needsToSelectAgain) {
                // null out entries in the array to allow to have it GC'ed once the Channel close
                // See https://github.com/netty/netty/issues/2363
                selectedKeys.reset(i + 1);

                selectAgain();
                i = -1;
            }
        }
    }

    private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
        final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
        if (!k.isValid()) {
            final EventLoop eventLoop;
            try {
                eventLoop = ch.eventLoop();
            } catch (Throwable ignored) {
                // If the channel implementation throws an exception because there is no event loop, we ignore this
                // because we are only trying to determine if ch is registered to this event loop and thus has authority
                // to close ch.
                return;
            }
            // Only close ch if ch is still registered to this EventLoop. ch could have deregistered from the event loop
            // and thus the SelectionKey could be cancelled as part of the deregistration process, but the channel is
            // still healthy and should not be closed.
            // See https://github.com/netty/netty/issues/5125
            if (eventLoop == this) {
                // close the channel if the key is not valid anymore
                unsafe.close(unsafe.voidPromise());
            }
            return;
        }

        try {
            int readyOps = k.readyOps();
            // We first need to call finishConnect() before try to trigger a read(...) or write(...) as otherwise
            // the NIO JDK channel implementation may throw a NotYetConnectedException.
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
                // See https://github.com/netty/netty/issues/924
                int ops = k.interestOps();
                ops &= ~SelectionKey.OP_CONNECT;
                k.interestOps(ops);

                unsafe.finishConnect();
            }

            // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                // Call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to write
                ch.unsafe().forceFlush();
            }

            // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
            // to a spin loop
            if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                unsafe.read();
            }
        } catch (CancelledKeyException ignored) {
            unsafe.close(unsafe.voidPromise());
        }
    }
    
    
	private int select(long deadlineNanos) throws IOException {
        if (deadlineNanos == NONE) {
            return selector.select();
        }
        // Timeout will only be 0 if deadline is within 5 microsecs
        long timeoutMillis = deadlineToDelayNanos(deadlineNanos + 995000L) / 1000000L;
        return timeoutMillis <= 0 ? selector.selectNow() : selector.select(timeoutMillis);
    }

```















#### Chooser相关类

##### EventExecutorChooserFactory 



```java
package io.netty.util.concurrent;

//chooser的创建工厂
@UnstableApi
public interface EventExecutorChooserFactory {

    /**
     * Returns a new {@link EventExecutorChooser}.
     */
    EventExecutorChooser newChooser(EventExecutor[] executors);

    /**
     * Chooses the next {@link EventExecutor} to use.
     chooser选择器
     */
    @UnstableApi
    interface EventExecutorChooser {

        /**
         * Returns the new {@link EventExecutor} to use.
         */
        EventExecutor next();
    }
}

```



##### DefaultEventExecutorChooserFactory

```java
package io.netty.util.concurrent;

@UnstableApi
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        //如果是2的倍数
        if (isPowerOfTwo(executors.length)) {
            //那么就使用2的倍数的选择器
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            //否则使用常规的选择器
            return new GenericEventExecutorChooser(executors);
        }
    }

    //判断是不是2的倍数
    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }
		//2的倍数的选择器 &2的倍数-1 相当于 %长度
        @Override
        public EventExecutor next() {
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        // Use a 'long' counter to avoid non-round-robin behaviour at the 32-bit overflow boundary.
        // The 64-bit long solves this by placing the overflow so far into the future, that no system
        // will encounter this in practice.
        private final AtomicLong idx = new AtomicLong();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }
		//常规选择器 直接取%
        @Override
        public EventExecutor next() {
            return executors[(int) Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}

```







#### Channel相关类

##### Channel

用于封装处理I/O操作

```java
package io.netty.channel;


//与网络套接字或组件的连接，能够执行 I/ O 操作，例如读取、写入、连接和绑定。
//频道为用户提供：
//通道的当前状态（例如，它是否打开？是否已连接？），
//通道的 配置参数 （例如接收缓冲区大小），
//通道支持的 I/ O 操作（例如读取、写入、连接和绑定），以及
//ChannelPipeline处理与通道关联的所有 I/ O 事件和请求。


//每个channel绑定了一个AttributeMap用于存储数据

public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    //返回全局唯一的channel标识符
    ChannelId id();

    //返回与这个channel绑定注册的执行器
    EventLoop eventLoop();

   	//返回这个channel的父级 如果没有返回null
    Channel parent();

    //返回这个channel的相关配置
    ChannelConfig config();

    //如果 已打开并且Channel以后可能处于活动状态，则返回true
    boolean isOpen();

    //当有执行器被注册到这个channel 返回true
    boolean isRegistered();

    //返回这个channel是否active并且可以连接
    boolean isActive();

    //描述channel的元数据
    ChannelMetadata metadata();

    //返回此通道绑定到的本地地址
    SocketAddress localAddress();

 	//返回此通道连接到的远程地址
    SocketAddress remoteAddress();

   	//返回当channel关闭时 回调的ChannelFuture
    ChannelFuture closeFuture();

   //当且仅当 I/ O 线程将立即执行请求的写入操作时返回 true 。当此方法返回 false 时发出的任何写入请求都将排队，直到 I/ O 线程准备好处理已排队的写入请求。
    boolean isWritable();

    //获取在返回false之前isWritable()可以写入多少字节。此数量将始终为非负数。如果isWritable()为 false 0，则为 0。
    long bytesBeforeUnwritable();

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     */
    long bytesBeforeWritable();

    //返回一个 仅供内部使用的 对象，该对象提供不安全的操作。
    Unsafe unsafe();

    //返回这个channel关联的pipeline
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     ？？？？？
     */
    ByteBufAllocator alloc();

    @Override
    Channel read();

    @Override
    Channel flush();

    /**
  	永远不应从用户代码中调用的不安全操作。这些方法仅用于实现实际传输，并且必须从 I/ O 线程调用，但以下方法除外：
		localAddress()
		remoteAddress()
		closeForcibly()
		register(EventLoop, ChannelPromise)
		deregister(ChannelPromise)
		voidPromise()
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         ？？？？？？？？？？
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

        //返回绑定到本地的， SocketAddress 或者 null 如果没有。
        SocketAddress localAddress();

        //返回绑定到远程的，或者null如果尚未绑定任何对象SocketAddress，则返回。
        SocketAddress remoteAddress();

        //注册的ChannelPromise，并在注册完成后通知ChannelFuture
        void register(EventLoop eventLoop, ChannelPromise promise);

        //绑定到 SocketAddress Channel 的 ChannelPromise ，并在完成后通知它。
        void bind(SocketAddress localAddress, ChannelPromise promise);

        //Channel将给定ChannelFuture的与给定的远程SocketAddress连接。如果应该使用特定的本地SocketAddress，则需要将其作为参数给出。否则，只需传递给null它。一旦连接操作完成，将ChannelPromise收到通知。
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        //断开连接ChannelChannelFuture，并在操作完成后通知。ChannelPromise
        void disconnect(ChannelPromise promise);

        //Channel ChannelPromise关闭 并在操作完成后通知 ChannelPromise 
        void close(ChannelPromise promise);

        //立即关闭而不 Channel 触发任何事件。可能仅在注册尝试失败时才有用。
        void closeForcibly();

        //取消注册 Channel上的EventLoop 并在操作完成后通知 ChannelPromise 。
        void deregister(ChannelPromise promise);

        //计划一个读取操作，该操作填充 中第一个ChannelInboundHandler ChannelPipeline的入站缓冲区。如果已存在挂起的读取操作，则此方法不执行任何操作。
        void beginRead();

        //计划写入操作
        void write(Object msg, ChannelPromise promise);

        //刷新通过 write(Object, ChannelPromise)安排的所有写入操作。
        void flush();

        /**
         * Return a special ChannelPromise which can be reused and passed to the operations in {@link Unsafe}.
         * It will never be notified of a success or error and so is only a placeholder for operations
         * that take a {@link ChannelPromise} as argument but for which you not want to get notified.
         */
        ChannelPromise voidPromise();

        /**
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        //？？？？？？？？？？？？？？
        ChannelOutboundBuffer outboundBuffer();
    }
}

```









##### AbstractChannel

Channel的骨架实现

//？？？？ 这里越过了部分内存buffer操作的代码 以事件循环的代码为主 后续再补

```java
package io.netty.channel;


/**
 * 一个Channel的骨架实现 继承了DefaultAttributeMap的属性存储能力
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);
	
    //父级Channel
    private final Channel parent;
    //全局唯一的ChannelId
    private final ChannelId id;
    //内部的Unsafe类 操作IO
    private final Unsafe unsafe;
    //默认与channel绑定的ChannelPipeline
    private final DefaultChannelPipeline pipeline;
    
    private final VoidChannelPromise unsafeVoidPromise = new VoidChannelPromise(this, false);
    //关闭Channel时回调的Promise
    private final CloseFuture closeFuture = new CloseFuture(this);

    //绑定的本地地址
    private volatile SocketAddress localAddress;
    //绑定的远端地址
    private volatile SocketAddress remoteAddress;
    //注册在这个channel上的执行器
    private volatile EventLoop eventLoop;
    //是否注册标志
    private volatile boolean registered;
    private boolean closeInitiated;
    private Throwable initialCloseCause;

    //此通道的字符串表示形式的缓存
    private boolean strValActive;
    private String strVal;
    
     protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }
    
    //返回一个默认的ChannelId实例
     protected ChannelId newId() {
        return DefaultChannelId.newInstance();
    }

    //返回一个默认的ChannelPipeline实例
    protected DefaultChannelPipeline newChannelPipeline() {
        //与当前channel进行绑定
        return new DefaultChannelPipeline(this);
    }
    
     protected abstract class AbstractUnsafe implements Unsafe {

        private volatile ChannelOutboundBuffer outboundBuffer = new ChannelOutboundBuffer(AbstractChannel.this);
        private RecvByteBufAllocator.Handle recvHandle;
        private boolean inFlush0;
        /** true if the channel has never been registered, false otherwise */
        private boolean neverRegistered = true;

         @Override
         //注册执行器到Channel上面 注册完成后回调Promise
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            ObjectUtil.checkNotNull(eventLoop, "eventLoop");
            //根据属性registered判断是否注册过
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }
            //判断执行器的类型是否和当前Channel的类型兼容 比如NioServerSocketChannel必须和NioEventLoop进行注册
            if (!isCompatible(eventLoop)) {
                promise.setFailure(
                        new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
                return;
            }

            //设置当前执行器
            AbstractChannel.this.eventLoop = eventLoop;

            //如果当前线程在执行器组中
            if (eventLoop.inEventLoop()) {
                //直接进行注册工作
                register0(promise);
            } else {
                try {
                    //否则扔进去等注册
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    logger.warn(
                            "Force-closing a channel whose registration task was not accepted by an event loop: {}",
                            AbstractChannel.this, t);
                    closeForcibly();
                    closeFuture.setClosed();
                    safeSetFailure(promise, t);
                }
            }
        }

         //注册的实际方法
        private void register0(ChannelPromise promise) {
            try {
                // check if the channel is still open as it could be closed in the mean time when the register
                // call was outside of the eventLoop
                //检查promise有没有取消
                //检查当前Channel是不是open的
                if (!promise.setUncancellable() || !ensureOpen(promise)) {
                    return;
                }
                //是不是第一次注册
                boolean firstRegistration = neverRegistered;
                //注册 由子类实现该方法
                doRegister();
                //设置已经注册过的属性
                neverRegistered = false;
                registered = true;

                // Ensure we call handlerAdded(...) before we actually notify the promise. This is needed as the
                // user may already fire events through the pipeline in the ChannelFutureListener.
                //调用执行所有handler被添加到pipeline的handlerAdded()方法
                pipeline.invokeHandlerAddedIfNeeded();

                //通知注册事件完成 
                safeSetSuccess(promise);
                
                //调用channelpipeline的fireChannelRegistered()来触发通道注册事件 从而调用channelHandler链的方法
                pipeline.fireChannelRegistered();
                
                
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                
                //判断当前channel是不是active并且已连接的
                if (isActive()) {
                    if (firstRegistration) {
                        pipeline.fireChannelActive();
                    } else if (config().isAutoRead()) {
                        // This channel was registered before and autoRead() is set. This means we need to begin read
                        // again so that we process inbound data.
                        //
                        // See https://github.com/netty/netty/issues/4805
                        beginRead();
                    }
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            assertEventLoop();

            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            // See: https://github.com/netty/netty/issues/576
            if (Boolean.TRUE.equals(config().getOption(ChannelOption.SO_BROADCAST)) &&
                localAddress instanceof InetSocketAddress &&
                !((InetSocketAddress) localAddress).getAddress().isAnyLocalAddress() &&
                !PlatformDependent.isWindows() && !PlatformDependent.maybeSuperUser()) {
                // Warn a user about the fact that a non-root user can't receive a
                // broadcast packet on *nix if the socket is bound on non-wildcard address.
                logger.warn(
                        "A non-root user can't receive a broadcast packet if the socket " +
                        "is not bound to a wildcard address; binding to a non-wildcard " +
                        "address (" + localAddress + ") anyway as requested.");
            }

            boolean wasActive = isActive();
            try {
                doBind(localAddress);
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
                return;
            }

            if (!wasActive && isActive()) {
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.fireChannelActive();
                    }
                });
            }

            safeSetSuccess(promise);
        }
         
         
                 /**
         * Marks the specified {@code promise} as success.  If the {@code promise} is done already, log a message.
         */
        protected final void safeSetSuccess(ChannelPromise promise) {
            if (!(promise instanceof VoidChannelPromise) && !promise.trySuccess()) {
                logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
            }
        }

        /**
         * Marks the specified {@code promise} as failure.  If the {@code promise} is done already, log a message.
         */
        protected final void safeSetFailure(ChannelPromise promise, Throwable cause) {
            if (!(promise instanceof VoidChannelPromise) && !promise.tryFailure(cause)) {
                logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
            }
        }
         
         
     }

}
```







##### AbstractNioChannel

##### NioServerSocketChannel





#### ChannelPipeline相关类

处理或拦截 Channel 的入站事件和出站操作的 ChannelHandler 列表。ChannelPipeline 实现了拦截过滤器模式的高级形式，让用户可以完全控制事件的处理方式以及管道中的 ChannelHandler 之间的交互方式。每个Channel都有自己的ChannelPipeline ，并在创建新Channel时自动创建。

**事件如何在管道中流动?**
下图描述了 ChannelPipeline 中的 ChannelHandler 通常如何处理 I/O 事件。I/O 事件由 ChannelInboundHandler 或 ChannelOutboundHandler 处理，并通过调用 ChannelHandlerContext 中定义的事件传播方法（如 ChannelHandlerContext.fireChannelRead(Object) 和 ChannelHandlerContext.write(Object)）转发到最近的ChannelHandler 。

<img src=".\images\image-20240705095824032.png" alt="image-20240705095824032" style="zoom:50%;" />

入站事件由入站处理程序自下而上的方向处理，如关系图左侧所示。入站处理程序通常处理由关系图底部的 I/ O 线程生成的入站数据。入站数据通常是通过实际的输入操作从远程对等体读取的，例如 SocketChannel. read(ByteBuffer)。如果入站事件超出了顶部入站处理程序的范围，则会以静默方式丢弃该事件，或者在需要您注意时将其记录下来。
出站事件由出站处理程序以自上而下的方向进行处理，如关系图右侧所示。出站处理程序通常会生成或转换出站流量，例如写入请求。如果出站事件超出了底部出站处理程序的范围，则由与 Channel关联的 I/ O 线程处理。I/ O 线程通常执行实际的输出操作，例如 SocketChannel. write(ByteBuffer)。
例如，假设我们创建了以下管道：

```java
  ChannelPipeline p = ...;
  p. addLast("1", new InboundHandlerA());
  p. addLast("2", new InboundHandlerB());
  p. addLast("3", new OutboundHandlerA());
  p. addLast("4", new OutboundHandlerB());
  p. addLast("5", new InboundOutboundHandlerX());
```

在上面的示例中，名称以开头的 Inbound 类表示它是一个入站处理程序。名称开头的 Outbound 类表示它是出站处理程序。
在给定的示例配置中，当事件入站时，处理程序评估顺序为 1、2、3、4、5。当事件出站时，顺序为 5、4、3、2、1。在此原则之上， ChannelPipeline 跳过对某些处理程序的评估以缩短堆栈深度：
3 和 4 不实现 ChannelInboundHandler，因此入站事件的实际评估顺序为：1、2 和 5。
1 和 2 不实现 ChannelOutboundHandler，因此出站事件的实际评估顺序为：5、4 和 3。
如果 5 同时 ChannelInboundHandler 实现 and ChannelOutboundHandler，则入站和出站事件的评估顺序可能分别为 125 和 543。

**将事件转发到下一个处理程序**

正如您可能在图中注意到的那样，处理程序必须调用事件传播方法 ChannelHandlerContext 才能将事件转发到其下一个处理程序。这些方法包括：

```java
//入站事件传播方法：
ChannelHandlerContext. fireChannelRegistered()
ChannelHandlerContext. fireChannelActive()
ChannelHandlerContext. fireChannelRead(Object)
ChannelHandlerContext. fireChannelReadComplete()
ChannelHandlerContext. fireExceptionCaught(Throwable)
ChannelHandlerContext. fireUserEventTriggered(Object)
ChannelHandlerContext. fireChannelWritabilityChanged()
ChannelHandlerContext. fireChannelInactive()
ChannelHandlerContext. fireChannelUnregistered()
    
//出站事件传播方法：
ChannelHandlerContext. bind(SocketAddress, ChannelPromise)
ChannelHandlerContext. connect(SocketAddress, SocketAddress, ChannelPromise)
ChannelHandlerContext. write(Object, ChannelPromise)
ChannelHandlerContext. flush()
ChannelHandlerContext. read()
ChannelHandlerContext. disconnect(ChannelPromise)
ChannelHandlerContext. close(ChannelPromise)
ChannelHandlerContext. deregister(ChannelPromise)
    
//示例

  public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
      {@code @Override}
      public void channelActive({@link ChannelHandlerContext} ctx) {
          System.out.println("Connected!");
          //转发给下一个ChannelHandlerContext
          ctx.fireChannelActive();
      }
  }


  public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
      {@code @Override}
      public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
          System.out.println("Closing ..");
          //转发给下一个ChannelHandlerContext
          ctx.close(promise);
      }
  }
```



##### ChannelPipeline

```java
package io.netty.channel;

//继承了ChannelInboundInvoker和ChannelOutboundInvoker的管道 用于链式处理ChannelHandler 
//ChannelInboundInvoker和ChannelOutboundInvoker分别定义了入站出站的传播方法
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {
 
    //指定一个处理程序的名称 添加到第一个
    ChannelPipeline addFirst(String name, ChannelHandler handler);
    
  //指定一个处理程序的名称 添加到最后一个
    ChannelPipeline addLast(String name, ChannelHandler handler);
    
	//添加到baseName的处理程序之前
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);
    
    ////添加到baseName的处理程序之后
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);
    
    //这里不再举例 详细看源码 接口比较简单 基本就是对链式的增删改查操作
    .........
}
```



##### DefaultChannelPipeline

```java
package io.netty.channel;

//默认 ChannelPipeline 实现。它通常是由实现 Channel 在创建时 Channel 创建的。
public class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);

    private static final String HEAD_NAME = generateName0(HeadContext.class);
    private static final String TAIL_NAME = generateName0(TailContext.class);

    private static final FastThreadLocal<Map<Class<?>, String>> nameCaches =
            new FastThreadLocal<Map<Class<?>, String>>() {
        @Override
        protected Map<Class<?>, String> initialValue() {
            return new WeakHashMap<Class<?>, String>();
        }
    };

    private static final AtomicReferenceFieldUpdater<DefaultChannelPipeline, MessageSizeEstimator.Handle> ESTIMATOR =
            AtomicReferenceFieldUpdater.newUpdater(
                    DefaultChannelPipeline.class, MessageSizeEstimator.Handle.class, "estimatorHandle");
    //处理器上下文链表的头和尾
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;

    //当前绑定的channel
    private final Channel channel;
    
    private final ChannelFuture succeededFuture;
    private final VoidChannelPromise voidPromise;
    private final boolean touch = ResourceLeakDetector.isEnabled();

    private Map<EventExecutorGroup, EventExecutor> childExecutors;
    private volatile MessageSizeEstimator.Handle estimatorHandle;
    //第一次注册标志位
    private boolean firstRegistration = true;

  	//被挂起的channelHandler的链表头部
    //这里要么是PendingHandlerAddedTask 要么是 PendingHandlerRemovedTask 
    //用于处理channelHandler添加移除channelPipeline的事件
    private PendingHandlerCallback pendingHandlerCallbackHead;

    //设置为 true 一旦 AbstractChannel 注册。一旦设置为 true 该值，将永远不会更改。
    private boolean registered;
    
  	protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise =  new VoidChannelPromise(channel, true);

        tail = new TailContext(this);
        head = new HeadContext(this);

        head.next = tail;
        tail.prev = head;
    }
    
    //调用所有的handler刚被添加到pipeline的事件handlerAdded()的方法 这里是处理刚被挂起的handler的handlerAdded()
   final void invokeHandlerAddedIfNeeded() {
        assert channel.eventLoop().inEventLoop();
       //只调用一次
        if (firstRegistration) {
            firstRegistration = false;
            // We are now registered to the EventLoop. It's time to call the callbacks for the ChannelHandlers,
            // that were added before the registration was done.
            callHandlerAddedForAllHandlers();
        }
    }
    
   private void callHandlerAddedForAllHandlers() {
        final PendingHandlerCallback pendingHandlerCallbackHead;
        synchronized (this) {
            assert !registered;
			
            //channel已经注册 那么把channelpipeline的registered标志位设置为true
            // This Channel itself was registered.
            registered = true;
			
            //拿到挂起的channelHandler
            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            // Null out so it can be GC'ed.
            this.pendingHandlerCallbackHead = null;
        }

        // This must happen outside of the synchronized(...) block as otherwise handlerAdded(...) may be called while
        // holding the lock and so produce a deadlock if handlerAdded(...) will try to add another handler from outside
        // the EventLoop.
        PendingHandlerCallback task = pendingHandlerCallbackHead;
       //链式执行所有的挂起channelHandler
        while (task != null) {
            task.execute();
            task = task.next;
        }
    }
    
    //添加channelHandler到channelPipeline中
    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }
    
    @Override
    public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        ObjectUtil.checkNotNull(handlers, "handlers");
		//循环加入所有的channelHandler
        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, null, h);
        }

        return this;
    }
    
    
    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            //检查这个handler是不是@Share注解修饰的 能被加入到多个channelpipeline中
            checkMultiplicity(handler);

            //新建一个channelHandlerContext对象来封装handler
            newCtx = newContext(group, filterName(name, handler), handler);

            ////加入到当前维护的ChannelHandlerContext链的尾部
            addLast0(newCtx);

            // If the registered is false it means that the channel was not registered on an eventLoop yet.
            // In this case we add the context to the pipeline and add a task that will call
            // ChannelHandler.handlerAdded(...) once the channel is registered.
            //如果还没注册完成
            if (!registered) {
                //设置这个ChannelHandlerContext的handlerAdded方法在稍后执行
                newCtx.setAddPending();
                //挂起这个ChannelHandlerContext到pendingHandlerCallbackHead
                callHandlerCallbackLater(newCtx, true);
                return this;
            }
		   //如果注册完了 直接异步执行 调用所有的handler的handlerAdded方法
            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }
    
    private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
        //创建一个默认的channelHandlerContext 把handler放进去进行封装
        return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
    }
    
    //加入到当前维护的ChannelHandlerContext链的尾部
    private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
    }
    
    
    
    @Override
    public final ChannelPipeline fireChannelRegistered() {
       	//从ChannelHandlerContext链的头部开始触发ChannelRegistered事件
        AbstractChannelHandlerContext.invokeChannelRegistered(head);
        return this;
    }
    
    

}
```









#### ChannelHandler相关类

处理 I/ O 事件或拦截 I/ O 操作，并将其转发到其 ChannelPipeline中的下一个处理程序，每个ChannelHandler 提供有一个 ChannelHandlerContext 对象。一个ChannelHandler 应该通过上下文对象与它所属的对象 ChannelPipeline 进行交互。使用 context 对象，可以 ChannelHandler 向上游或下游传递事件，动态修改管道，或使用AttributeKey存储特定于处理程序的信息

##### ChannelHandler

```java
package io.netty.channel;

//ChannelHandler 本身没有提供很多方法，但你通常必须实现它的一个子类型：
//ChannelInboundHandler 处理入站 I/ O 事件，以及
//ChannelOutboundHandler 处理出站 I/ O 操作。
//或者，为方便起见，提供了以下适配器类：
//ChannelInboundHandlerAdapter 要处理入站 I/ O 事件，
//ChannelOutboundHandlerAdapter 处理出站 I/ O 操作，以及
//ChannelDuplexHandler 处理入站和出站事件
public interface ChannelHandler {

 	//当ChannelHandler被添加到ChannelPipeline中并准备开始处理事件时，此方法将被调用。开发人员通常在此方法中执行一些初始化设置，比如注册监听器、分配资源等，为ChannelHandler处理即将到达的事件做准备。
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    //当ChannelHandler从ChannelPipeline中被移除并且不再处理任何事件时，此方法将被调用。这是执行清理工作的理想时机，例如取消定时任务、关闭资源、解除引用以避免内存泄漏等，确保ChannelHandler优雅地退出服务。
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     *
     * @deprecated if you want to handle this event you should implement {@link ChannelInboundHandler} and
     * implement the method there.
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    //带有该注解的ChannelHandler可以被添加到多个ChannelPipeline中去，否则每次必须用一个新的实例
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
```

##### ChannelInboundHandler

channel入站事件的处理器接口

```java
package io.netty.channel;

/**
 * {@link ChannelHandler} which adds callbacks for state changes. This allows the user
 * to hook in to state changes easily.
 */
public interface ChannelInboundHandler extends ChannelHandler {

    //ChannelHandlerContext的channel已经被注册了EventLoop
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;

    //ChannelHandlerContext的channel已经被解除注册了EventLoop
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    //ChannelHandlerContext的channel已经active了
    void channelActive(ChannelHandlerContext ctx) throws Exception;

    // Channel处于非活动状态，并已达到其生命周期的终点。
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

  	//channel可读时调用
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * Invoked when the last message read by the current read operation has been consumed by
     * {@link #channelRead(ChannelHandlerContext, Object)}.  If {@link ChannelOption#AUTO_READ} is off, no further
     * attempt to read an inbound data from the current {@link Channel} will be made until
     * {@link ChannelHandlerContext#read()} is called.
     */
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;

    //如果触发了用户事件，则被调用。
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    //一旦 的 Channel 可写状态发生变化，就会被调用。您可以使用 检查状态 Channel.isWritable()。
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;

   	//如果抛出Throwable ，则被调用。
    @Override
    @SuppressWarnings("deprecation")
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}

```

##### ChannelOutboundHandler

```java
package io.netty.channel;

import java.net.SocketAddress;

//ChannelHandler 将收到 IO 出站操作的通知。
public interface ChannelOutboundHandler extends ChannelHandler {
   	//在执行绑定操作时调用。
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception;

    //在执行连接操作时调用。
    void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception;

    //在执行断开连接操作时调用。
    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    //在执行关闭操作时调用。
    void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    //一旦从当前注册 EventLoop的 执行取消注册操作，就会调用。
    void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    //拦截 ChannelHandlerContext.read()。
    void read(ChannelHandlerContext ctx) throws Exception;

  	//在执行写入操作时调用。写入操作将通过 ChannelPipeline写入消息
    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception;

    //在执行刷新操作时调用。刷新操作将尝试刷新所有先前挂起的已写入消息。
    void flush(ChannelHandlerContext ctx) throws Exception;
}

```

##### ChannelInitializer

```java
package io.netty.channel;

//一个特殊的 ChannelInboundHandler，它提供了一种在将 Channel 注册到其 EventLoop 后初始化 Channel 的简单方法。
//@Sharable注解的ChannelHandler可以被多个pipeline使用
@Sharable
public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter {

   

    
    //当ChannelHandler被添加到ChannelPipeline中并准备开始处理事件时，此方法将被调用
    //继承了ChannelHandler的handlerAdded()
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        //判断这个channel是否注册
        if (ctx.channel().isRegistered()) {
            // This should always be true with our current DefaultChannelPipeline implementation.
            // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering
            // surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers
            // will be added in the expected order.
            //调用initChannel方法来完成一些初始化操作
            if (initChannel(ctx)) {

                //完成初始化了 这个ChannelHandlerContext就没用了 需要移除
                // We are done with init the Channel, removing the initializer now.
                removeState(ctx);
            }
        }
    }
    
  	@SuppressWarnings("unchecked")
    private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
        //在initMap里加入这个ChannelHandlerContext
        //这是为了避免重复调用initChannel
        if (initMap.add(ctx)) { // Guard against re-entrance.
            try {
                //调用子类实现的channel初始化方法
                initChannel((C) ctx.channel());
            } catch (Throwable cause) {
                // Explicitly call exceptionCaught(...) as we removed the handler before calling initChannel(...).
                // We do so to prevent multiple calls to initChannel(...).
                exceptionCaught(ctx, cause);
            } finally {
                ChannelPipeline pipeline = ctx.pipeline();
                if (pipeline.context(this) != null) {
                    pipeline.remove(this);
                }
            }
            return true;
        }
        return false;
    }
    
    //注册后 Channel 将调用此方法。方法返回后，此ChannelHandler实例将从ChannelPipeline中删除。
    protected abstract void initChannel(C ch) throws Exception;

    
     private void removeState(final ChannelHandlerContext ctx) {
        // The removal may happen in an async fashion if the EventExecutor we use does something funky.
        
         //如果这个ChannelHandlerContext已经被移除了 那么在initMap里也移除
         if (ctx.isRemoved()) {
            initMap.remove(ctx);
             
             //否则异步移除
        } else {
            // The context is not removed yet which is most likely the case because a custom EventExecutor is used.
            // Let's schedule it on the EventExecutor to give it some more time to be completed in case it is offloaded.
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    initMap.remove(ctx);
                }
            });
        }
    }
}
```







#### ChannelHandlerContext相关类

封装channelHandler的处理器上下文对象 可以传播事件到下一个handlerContext

##### ChannelInboundInvoker

```java
package io.netty.channel;

public interface ChannelInboundInvoker {

 	//触发channelPipeline的下一个ChannelInboundHandler.channelRegistered()事件
    ChannelInboundInvoker fireChannelRegistered();
    
    //下面的以此类推

    ChannelInboundInvoker fireChannelUnregistered();


    ChannelInboundInvoker fireChannelActive();

 
    ChannelInboundInvoker fireChannelInactive();

 
    ChannelInboundInvoker fireExceptionCaught(Throwable cause);

  
    ChannelInboundInvoker fireUserEventTriggered(Object event);

  
    ChannelInboundInvoker fireChannelRead(Object msg);

   
    ChannelInboundInvoker fireChannelReadComplete();

    ChannelInboundInvoker fireChannelWritabilityChanged();
}

```



##### ChannelOutboundInvoker

```java
package io.netty.channel;

public interface ChannelOutboundInvoker {

	//触发ChannelPipeline中的下一个ChannelOutboundHandler.bind()方法 该操作完成后回调ChannelFuture通知
    ChannelFuture bind(SocketAddress localAddress);

  
    ChannelFuture connect(SocketAddress remoteAddress);

  
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);


    ChannelFuture disconnect();

   
    ChannelFuture close();

  
    ChannelFuture deregister();

   
    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    
    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

   
    ChannelFuture disconnect(ChannelPromise promise);

    
    ChannelFuture close(ChannelPromise promise);

   
    ChannelFuture deregister(ChannelPromise promise);

   
    ChannelOutboundInvoker read();

   
    ChannelFuture write(Object msg);

    
    ChannelFuture write(Object msg, ChannelPromise promise);

    
    ChannelOutboundInvoker flush();

    
    ChannelFuture writeAndFlush(Object msg, ChannelPromise promise);

    
    ChannelFuture writeAndFlush(Object msg);

    
    ChannelPromise newPromise();

    ChannelProgressivePromise newProgressivePromise();

    ChannelFuture newSucceededFuture();


    ChannelFuture newFailedFuture(Throwable cause);

 
    ChannelPromise voidPromise();
}

```

##### ChannelHandlerContext

```java
package io.netty.channel;

//封装了ChannelHandler的处理器上下文 拥有传播事件的能力
//attr(AttributeKey) 允许您存储和访问与 ChannelHandler / Channel 及其上下文相关的有状态信息
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

   	//返回绑定在当前channelHandlerContext的channel
    Channel channel();

    //返回执行器
    EventExecutor executor();

  	//的唯一名称ChannelHandlerContext。当ChannelHandler被添加到 ChannelPipeline时，将使用该名称。此名称还可用于从ChannelPipeline中访问已注册ChannelHandler
    String name();

    //返回当前ChannelHandlerContext绑定的ChannelHandler
    ChannelHandler handler();

    //返回是不是已经从ChannelPipeline删除了当前ChannelHandler
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * Return the assigned {@link ChannelPipeline}
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     //建议使用Channel的attr(AttributeKey)方法
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
```

##### AbstractChannelHandlerContext

```java
package io.netty.channel;


//ChannelHandlerContext的模板实现
abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannelHandlerContext.class);
    //链式结构 上一个handlerContext
    volatile AbstractChannelHandlerContext next;
    //下一个handlerContext
    volatile AbstractChannelHandlerContext prev;

    //CAS更新hanlerState的处理状态
    private static final AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> HANDLER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractChannelHandlerContext.class, "handlerState");

    /**
     * ChannelHandler.handlerAdded(ChannelHandlerContext) 即将被调用 也就是加入到ChannelPipeline的pendingHandlerCallbackHead里面挂起了
     */
    private static final int ADD_PENDING = 1;
    /**
     * ChannelHandler.handlerAdded(ChannelHandlerContext)被调用完成了
     */
    private static final int ADD_COMPLETE = 2;
    /**
     * ChannelHandler.handlerRemoved(ChannelHandlerContext)被调用完成了
     */
    private static final int REMOVE_COMPLETE = 3;
    
   	//既没有 ChannelHandler.handlerAdded(ChannelHandlerContext) 
    //也没有 ChannelHandler.handlerRemoved(ChannelHandlerContext) 被调用。
    private static final int INIT = 0;

    //绑定的pipeline
    private final DefaultChannelPipeline pipeline;
    //上下文的名字
    private final String name;
    //执行的顺序
    private final boolean ordered;
    private final int executionMask;

    // Will be set to null if no child executor should be used, otherwise it will be set to the
    // child executor.
    final EventExecutor executor;
    private ChannelFuture succeededFuture;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances then needed.
    private Tasks invokeTasks;

    private volatile int handlerState = INIT;
    
    AbstractChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor,
                                  String name, Class<? extends ChannelHandler> handlerClass) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.pipeline = pipeline;
        this.executor = executor;
        this.executionMask = mask(handlerClass);
        // Its ordered if its driven by the EventLoop or the given Executor is an instanceof OrderedEventExecutor.
        //如果它由 EventLoop 驱动，或者给定的 Executor 是 OrderedEventExecutor 的实例，则它是有序的。
        ordered = executor == null || executor instanceof OrderedEventExecutor;
    }
    
    //举一个传播事件的例子
    
    //上游调用fireChannelRegistered
     @Override
    public ChannelHandlerContext fireChannelRegistered() {
        invokeChannelRegistered(findContextInbound(MASK_CHANNEL_REGISTERED));
        return this;
    }
    
    //遍历ChannelHandlerContext链表 找到符合条件的ChannelHandlerContext 通过mask的位运算来计算出当前handler是否处理该事件
     private AbstractChannelHandlerContext findContextInbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        EventExecutor currentExecutor = executor();
        do {
            ctx = ctx.next;
        } while (skipContext(ctx, currentExecutor, mask, MASK_ONLY_INBOUND));
        return ctx;
    }
    //跳过条件的计算 通过mask的位运算来计算出当前handler是否处理该事件 mask代表不同事件
    private static boolean skipContext(
            AbstractChannelHandlerContext ctx, EventExecutor currentExecutor, int mask, int onlyMask) {
        // Ensure we correctly handle MASK_EXCEPTION_CAUGHT which is not included in the MASK_EXCEPTION_CAUGHT
        return (ctx.executionMask & (onlyMask | mask)) == 0 ||
                // We can only skip if the EventExecutor is the same as otherwise we need to ensure we offload
                // everything to preserve ordering.
                //
                // See https://github.com/netty/netty/issues/10067
                (ctx.executor() == currentExecutor && (ctx.executionMask & mask) == 0);
    }

    //调用下一个ChannelHandlerContext的fireChannelRegistered方法
    static void invokeChannelRegistered(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        //如果当前线程在执行器组中 那么直接执行
        if (executor.inEventLoop()) {
            next.invokeChannelRegistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelRegistered();
                }
            });
        }
    }

    //调用下一个channelHandlerContext的fireChannelRegistered()方法
    private void invokeChannelRegistered() {
        if (invokeHandler()) {
            try {
                //把自己这个上下文传进去
                ((ChannelInboundHandler) handler()).channelRegistered(this);
            } catch (Throwable t) {
                invokeExceptionCaught(t);
            }
        } else {
            fireChannelRegistered();
        }
    }
    
    //调用handler的handlerAdded方法
   final void callHandlerAdded() throws Exception {
        // We must call setAddComplete before calling handlerAdded. Otherwise if the handlerAdded method generates
        // any pipeline events ctx.handler() will miss them because the state will not allow it.
       //设置handlerAdded方法已经调用过的状态
        if (setAddComplete()) {
            handler().handlerAdded(this);
        }
    }
    
    
    

```



##### DefaultChannelHandlerContext

```java
package io.netty.channel;

final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    //绑定ChannelHandler
    private final ChannelHandler handler;

    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, handler.getClass());
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
}
```







##### PendingHandlerCallback

位于DefaultChannelPipeline的内部类

```java
 private abstract static class PendingHandlerCallback implements Runnable {
        final AbstractChannelHandlerContext ctx;
        PendingHandlerCallback next;

        PendingHandlerCallback(AbstractChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        abstract void execute();
    }

```



##### PendingHandlerAddedTask

位于DefaultChannelPipeline的内部类

```java
    private final class PendingHandlerAddedTask extends PendingHandlerCallback {

        PendingHandlerAddedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerAdded0(ctx);
        }

        //当调用execute方法时
        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            //如果当前线程属于执行器组 那么直接执行
            if (executor.inEventLoop()) {
                //调用ChannelHandler的handlerAdded方法
                callHandlerAdded0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke handlerAdded() as the EventExecutor {} rejected it, removing handler {}.",
                                executor, ctx.name(), e);
                    }
                    atomicRemoveFromHandlerList(ctx);
                    ctx.setRemoved();
                }
            }
        }
    }

//该方法位于DefaultChannelPipeline中
 private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) {
        try {
            //调用AbstractChannelHandlerContext的封装的callHandlerAdded方法
            ctx.callHandlerAdded();
        } catch (Throwable t) {
            boolean removed = false;
            try {
                atomicRemoveFromHandlerList(ctx);
                ctx.callHandlerRemoved();
                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            }

            if (removed) {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }


```



##### PendingHandlerRemovedTask

位于DefaultChannelPipeline的内部类

```java
 private final class PendingHandlerRemovedTask extends PendingHandlerCallback {

        PendingHandlerRemovedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerRemoved0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                callHandlerRemoved0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke handlerRemoved() as the EventExecutor {} rejected it," +
                                        " removing handler {}.", executor, ctx.name(), e);
                    }
                    // remove0(...) was call before so just call AbstractChannelHandlerContext.setRemoved().
                    ctx.setRemoved();
                }
            }
        }
    }

//该方法位于DefaultChannelPipeline中
    private void callHandlerRemoved0(final AbstractChannelHandlerContext ctx) {
        // Notify the complete removal.
        try {
            ctx.callHandlerRemoved();
        } catch (Throwable t) {
            fireExceptionCaught(new ChannelPipelineException(
                    ctx.handler().getClass().getName() + ".handlerRemoved() has thrown an exception.", t));
        }
    }
```







#### Promise相关类

Promise 代表了对于异步执行的结果

<img src=".\images\image-20240704115657629.png" alt="image-20240704115657629" style="zoom:50%;" />

##### Future

这个是jdk的接口Future 提供了对于异步执行结果的操作

```java
package java.util.concurrent;

// Future 表示异步计算的结果。提供了检查计算是否完成、等待计算完成以及检索计算结果的方法。只有在计算完成后，才能使用 method get 检索结果，必要时阻塞直到计算准备好。取消是通过该 cancel 方法执行的。提供了其他方法来确定任务是正常完成还是已取消。计算一旦完成，就无法取消计算。
public interface Future<V> {

   
    boolean cancel(boolean mayInterruptIfRunning);

    
    boolean isCancelled();

   
    boolean isDone();

    V get() throws InterruptedException, ExecutionException;

   
    V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
```



##### Future

这个是netty继承了jdk的Future接口后，对原接口的扩展，提供了监听器的能力

```java
package io.netty.util.concurrent
    
//异步操作的结果
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {

    boolean isSuccess();

    boolean isCancellable();

    //如果 I/ O 操作失败，则返回 I/ O 操作失败的原因。
    //返回：失败的原因。 null 如果成功了，或者这个未来还没有完成。
    Throwable cause();

  	//添加一个监听器到Future中 当结果完成时调用监听器
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

  
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

   	//移除监听器
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);


    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    //等待这个异步操作，直到它完成，如果这个异步操作失败了，则抛出失败的原因。
    Future<V> sync() throws InterruptedException;

  
    Future<V> syncUninterruptibly();

   	//等待这个异步操作完成。
    Future<V> await() throws InterruptedException;

  
    Future<V> awaitUninterruptibly();

 
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;


    boolean await(long timeoutMillis) throws InterruptedException;


    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

  
    boolean awaitUninterruptibly(long timeoutMillis);

  
    V getNow();

  
    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}

```







##### Promise

Promise才是netty中使用的Promise的顶级接口，拥有回调监听器和获取任务结果的能力，并且拥有主动设置异步操作结果的能力

```java
package io.netty.util.concurrent;

//特殊 Future ，这是可写的。
public interface Promise<V> extends Future<V> {
   	//把这个异步操作标记为成功 并且唤醒所有监听器
	//如果已经成功或失败，它将抛出一个 IllegalStateException.
    Promise<V> setSuccess(V result);

   	//把这个异步操作标记为成功 唤醒所有监听器
	//true 当且仅当成功地将这个未来标记为成功。否则 false ，因为这个未来已经被标记为成功或失败。
    boolean trySuccess(V result);

   //标记失败 通知所有监听器
    Promise<V> setFailure(Throwable cause);

	//标记失败 通知所有监听器
    boolean tryFailure(Throwable cause);

 	//标记这个异步操作无法取消
    boolean setUncancellable();

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptibly();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptibly();
}

```



##### ChannelFuture

<img src=".\images\image-20240704115903935.png" alt="image-20240704115903935" style="zoom:50%;" />

```java
package io.netty.channel;


//异步 Channel I/ O 操作的结果。
//Netty 中的所有 I/ O 操作都是异步的。这意味着任何 I/ O 调用都将立即返回，而不能保证请求的 I/ O 操作在调用结束时已完成。相反，您将返回一个 ChannelFuture 实例，该实例为您提供有关 I/ O 操作的结果或状态的信息。
//A ChannelFuture 要么是 未完成 ，要么是 已完成。当 I/ O 操作开始时，将创建一个新的 future 对象。新的 future 最初是未完成的 - 它既没有成功、失败，也没有被取消，因为 I/ O 操作尚未完成。如果 I/ O 操作成功完成、失败或取消完成，则未来将标记为已完成，并提供更具体的信息，例如失败的原因。请注意，即使失败和取消也属于已完成状态。
public interface ChannelFuture extends Future<Void> {

    //返回绑定到这个Future的channel
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * 如果这是一个 void的ChannelFuture，不允许调用以下方法
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     */
    boolean isVoid();
}
```







##### ChannelPromise

ChannelPromise 继承了ChannelFuture和Promise，具有了Promise的设置执行结果的能力和Future调度监视器的能力

```java
package io.netty.channel;


//特殊 ChannelFuture ，这是可写的。
public interface ChannelPromise extends ChannelFuture, Promise<Void> {

    @Override
    Channel channel();

    @Override
    ChannelPromise setSuccess(Void result);

    ChannelPromise setSuccess();

    boolean trySuccess();

    @Override
    ChannelPromise setFailure(Throwable cause);

    @Override
    ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelPromise removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelPromise sync() throws InterruptedException;

    @Override
    ChannelPromise syncUninterruptibly();

    @Override
    ChannelPromise await() throws InterruptedException;

    @Override
    ChannelPromise awaitUninterruptibly();

    /**
     * Returns a new {@link ChannelPromise} if {@link #isVoid()} returns {@code true} otherwise itself.
     */
    ChannelPromise unvoid();
}
```



##### DefaultPromise

对于Future和Promise的默认实现

```java
package io.netty.util.concurrent;

public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {
    
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultPromise.class);
    private static final InternalLogger rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultPromise.class.getName() + ".rejectedExecution");
    
    private static final int MAX_LISTENER_STACK_DEPTH = Math.min(8,
            SystemPropertyUtil.getInt("io.netty.defaultPromise.maxListenerStackDepth", 8));
    
    //用于CAS更新当前对象result字段的更新器
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");
    
    private static final Object SUCCESS = new Object();
    
    private static final Object UNCANCELLABLE = new Object();
    
    private static final CauseHolder CANCELLATION_CAUSE_HOLDER = new CauseHolder(
            StacklessCancellationException.newInstance(DefaultPromise.class, "cancel(...)"));
    
    private static final StackTraceElement[] CANCELLATION_STACK = CANCELLATION_CAUSE_HOLDER.cause.getStackTrace();

    private volatile Object result;
    //用于执行唤醒监听器的执行器
    private final EventExecutor executor;
    /**
    	一个或者多个监听器 可以是GenericFutureListener或者DefaultFutureListeners
     */
    private Object listeners;
    /**
     //在当前对象上wait()的线程数量
     */
    private short waiters;

    /**
     标志是否正在通知监听器
     */
    private boolean notifyingListeners;
    
    //创建新实例。最好使用它 EventExecutor. newPromise() 来创建新的Promise
    public DefaultPromise(EventExecutor executor) {
        this.executor = checkNotNull(executor, "executor");
    }
   
	//设置执行结果为成功 设置result
    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }
    
	//设置执行结果为成功 设置result
    @Override
    public boolean trySuccess(V result) {
        return setSuccess0(result);
    }

    //设置执行结果为失败
    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }
    
 	//设置执行结果为失败
    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
    }

    //添加监听器
    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");
		
        //添加监听器
        synchronized (this) {
            addListener0(listener);
        }
		//如果任务已经完成了 那么通知所有监听器
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

   
	//移除监听器
    @Override
    public Promise<V> removeListener(final GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");
		
        synchronized (this) {
            removeListener0(listener);
        }

        return this;
    }

    
	//在这个Promise上进行等待 等价于调用wait()
    @Override
    public Promise<V> await() throws InterruptedException {
        //如果任务已经完成了 那么直接返回
        if (isDone()) {
            return this;
        }

        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
		
        //检查死锁
        checkDeadLock();

        synchronized (this) {
            //循环等待 直到任务完成
            while (!isDone()) {
                //增加等待的线程数量
                incWaiters();
                try {
                    //Object的wait()
                    wait();
                } finally {
                    //减少等待的线程数量
                    decWaiters();
                }
            }
        }
        return this;
    }

 
	//等待任务执行完成
    @Override
    public Promise<V> sync() throws InterruptedException {
        //wait()等待任务完成
        await();
        //如果失败抛出异常
        rethrowIfFailed();
        return this;
    }



    protected EventExecutor executor() {
        return executor;
    }
	
    //通知所有监听器
    private void notifyListeners() {
        //获取执行器
        EventExecutor executor = executor();
        //当前线程在不在这个执行器组中
        if (executor.inEventLoop()) {
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
            final int stackDepth = threadLocals.futureListenerStackDepth();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    //通知所有监听器
                    notifyListenersNow();
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }
		//不在的话扔到执行器中执行
        safeExecute(executor, new Runnable() {
            @Override
            public void run() {
                //通知所有监听器
                notifyListenersNow();
            }
        });
    }

    

    private void notifyListenersNow() {
        Object listeners;
        synchronized (this) {
            // Only proceed if there are listeners to notify and we are not already notifying listeners.
            if (notifyingListeners || this.listeners == null) {
                return;
            }
            //设置正在通知监听器
            notifyingListeners = true;
            //拿到监听器集合
            listeners = this.listeners;
            this.listeners = null;
        }
        for (;;) {
            if (listeners instanceof DefaultFutureListeners) {
                notifyListeners0((DefaultFutureListeners) listeners);
            } else {
                //通知监听器
                notifyListener0(this, (GenericFutureListener<?>) listeners);
            }
            synchronized (this) {
                if (this.listeners == null) {
                    // Nothing can throw from within this method, so setting notifyingListeners back to false does not
                    // need to be in a finally block.
                    //最后设置标志位为false 
                    notifyingListeners = false;
                    //跳出循环
                    return;
                }
                listeners = this.listeners;
                //通知完成监听器清空
                this.listeners = null;
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            //调用监听器的回调方法
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
            }
        }
    }

    //添加监听器
    private void addListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners == null) {
            listeners = listener;
        } else if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).add(listener);
        } else {
            listeners = new DefaultFutureListeners((GenericFutureListener<?>) listeners, listener);
        }
    }

    //移除监听器
    private void removeListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).remove(listener);
        } else if (listeners == listener) {
            listeners = null;
        }
    }

    //设置执行结果为成功
    private boolean setSuccess0(V result) {
        return setValue0(result == null ? SUCCESS : result);
    }

    //设置执行结果为失败
    private boolean setFailure0(Throwable cause) {
        return setValue0(new CauseHolder(checkNotNull(cause, "cause")));
    }

    //设置执行结果 通知监听器
    private boolean setValue0(Object objResult) {
        //CAS更新result结果
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
            RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
    }

	//增加等待的线程数量
    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        ++waiters;
    }

    //减少等待的线程数量
    private void decWaiters() {
        --waiters;
    }

   	//利用执行器进行执行监视器回调
    private static void safeExecute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            rejectedExecutionLogger.error("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }


}
```





##### DefaultChannelPromise

整合了DefaultPromise和Channel的实现类

```java
package io.netty.channel;

//默认 ChannelPromise 实现。建议使用 Channel. newPromise() 创建新 ChannelPromise 构造函数，而不是显式调用构造函数。

public class DefaultChannelPromise extends DefaultPromise<Void> implements ChannelPromise, FlushCheckpoint {

    private final Channel channel;
    private long checkpoint;
    
        /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     */
    public DefaultChannelPromise(Channel channel) {
        this.channel = checkNotNull(channel, "channel");
    }

    /**
     		绑定channel和执行器
     */
    public DefaultChannelPromise(Channel channel, EventExecutor executor) {
        super(executor);
        this.channel = checkNotNull(channel, "channel");
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (e == null) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

}
```



#### EventListener相关类

事件监听器

##### EventListener

```java
package java.util;

/**
 * A tagging interface that all event listener interfaces must extend.
 所有事件侦听器接口都必须扩展的标记接口。
 * @since JDK1.1
 */
public interface EventListener {
}
```

##### GenericFutureListener

```java
package io.netty.util.concurrent;

import java.util.EventListener;

/**
 * Listens to the result of a {@link Future}.  The result of the asynchronous operation is notified once this listener
 * is added by calling {@link Future#addListener(GenericFutureListener)}.
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * Invoked when the operation associated with the {@link Future} has been completed.
     *	在与关联的 Future 操作完成时调用。 Promise完成时回调的方法
     */
    void operationComplete(F future) throws Exception;
}

```

##### DefaultFutureListeners

```java
package io.netty.util.concurrent;

import java.util.Arrays;

//默认的异步结果监听器
final class DefaultFutureListeners {

    //监听器集合
    private GenericFutureListener<? extends Future<?>>[] listeners;
    private int size;
    private int progressiveSize; // the number of progressive listeners

    @SuppressWarnings("unchecked")
    DefaultFutureListeners(
            GenericFutureListener<? extends Future<?>> first, GenericFutureListener<? extends Future<?>> second) {
        listeners = new GenericFutureListener[2];
        listeners[0] = first;
        listeners[1] = second;
        size = 2;
        if (first instanceof GenericProgressiveFutureListener) {
            progressiveSize ++;
        }
        if (second instanceof GenericProgressiveFutureListener) {
            progressiveSize ++;
        }
    }

    public void add(GenericFutureListener<? extends Future<?>> l) {
        GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        final int size = this.size;
        //如果当前大小到达最大
        if (size == listeners.length) {
            //扩容两倍
            this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
        }
        //加入监听器
        listeners[size] = l;
        this.size = size + 1;

        if (l instanceof GenericProgressiveFutureListener) {
            progressiveSize ++;
        }
    }

    public void remove(GenericFutureListener<? extends Future<?>> l) {
        final GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            if (listeners[i] == l) {
                int listenersToMove = size - i - 1;
                if (listenersToMove > 0) {
             	    //移除当前监听器 重建数组
                    System.arraycopy(listeners, i + 1, listeners, i, listenersToMove);
                }
                listeners[-- size] = null;
                this.size = size;

                if (l instanceof GenericProgressiveFutureListener) {
                    progressiveSize --;
                }
                return;
            }
        }
    }

    public GenericFutureListener<? extends Future<?>>[] listeners() {
        return listeners;
    }

    public int size() {
        return size;
    }

    public int progressiveSize() {
        return progressiveSize;
    }
}

```







#### ServerBootstrap的启动流程

debug的代码

```java
package com.zcq.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

public class HelloNetty {
    public static void main(String[] args) {
        new NettyServer(8888).serverStart();
    }
}

class NettyServer {


    int port = 8888;

    public NettyServer(int port) {
        this.port = port;
    }

    public void serverStart() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();

        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new BossHandler())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new WorkerHandler());
                    }
                });

        try {
            Channel f = b.bind(port).sync().channel();
            f.closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }


    }
}

class BossHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //super.channelRead(ctx, msg);
        System.out.println("boss: channel read");
        ByteBuf buf = (ByteBuf)msg;

        System.out.println(buf.toString(CharsetUtil.UTF_8));

        ctx.writeAndFlush(msg);

        ctx.close();

        //buf.release();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        System.out.println("我自己的BossHandler handleradded");
    }
}

class WorkerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //super.channelRead(ctx, msg);
        System.out.println("Worker: channel read");
        ByteBuf buf = (ByteBuf)msg;
        System.out.println(buf.toString());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        System.out.println("我自己的WorkerHandler handleradded");
    }
}
```



















#### 其他拓展类

##### AtomicReferenceFieldUpdater

```java
//CAS原子更新某个对象的属性值
private static final AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> PROPERTIES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    SingleThreadEventExecutor.class, ThreadProperties.class, "threadProperties");
```



### 6.内存管理

本章参考资料：  [Netty 高性能内存管理设计](https://learn.lianglianglee.com/%e4%b8%93%e6%a0%8f/Netty%20%e6%a0%b8%e5%bf%83%e5%8e%9f%e7%90%86%e5%89%96%e6%9e%90%e4%b8%8e%20RPC%20%e5%ae%9e%e8%b7%b5-%e5%ae%8c/12%20%20%e4%bb%96%e5%b1%b1%e4%b9%8b%e7%9f%b3%ef%bc%9a%e9%ab%98%e6%80%a7%e8%83%bd%e5%86%85%e5%ad%98%e5%88%86%e9%85%8d%e5%99%a8%20jemalloc%20%e5%9f%ba%e6%9c%ac%e5%8e%9f%e7%90%86.md)



#### 内外碎片

##### 内碎片

内存是按 Page 进行分配的，即便我们只需要很小的内存，操作系统至少也会分配 4K 大小的 Page，单个 Page 内只有一部分字节都被使用，剩余的字节形成了内部碎片，如下图所示。

<img src=".\images\内碎片.png" style="zoom: 50%;" />

##### 外碎片

外部碎片与内部碎片相反，是在分配较大内存块时产生的。我们试想一下，当需要分配大内存块的时候，操作系统只能通过分配连续的 Page 才能满足要求，在程序不断运行的过程中，这些 Page 被频繁的回收并重新分配，Page 之间就会出现小的空闲内存块，这样就形成了外部碎片，如下图所示。

<img src=".\images\外碎片.png" style="zoom: 33%;" />

#### 常用内存算法

##### 动态内存分配

动态内存分配（Dynamic memory allocation）又称为堆内存分配，后面简称 DMA，操作系统根据程序运行过程中的需求即时分配内存，且分配的内存大小就是程序需求的大小。在大部分场景下，只有在程序运行的时候才知道所需要分配的内存大小，如果提前分配可能会分配的大小无法把控，分配太大会浪费空间，分配太小会无法使用。

DMA 是从一整块内存中按需分配，对于分配出的内存会记录元数据，同时还会使用空闲分区链维护空闲内存，便于在内存分配时查找可用的空闲分区，常用的有三种查找策略：

**第一种是首次适应算法（first fit）**，空闲分区链以地址递增的顺序将空闲分区以双向链表的形式连接在一起，从空闲分区链中找到第一个满足分配条件的空闲分区，然后从空闲分区中划分出一块可用内存给请求进程，剩余的空闲分区仍然保留在空闲分区链中。如下图所示，P1 和 P2 的请求可以在内存块 A 中完成分配。该算法每次都从低地址开始查找，造成低地址部分会不断被分配，同时也会产生很多小的空闲分区。

<img src=".\images\首次适应算法.png" style="zoom:50%;" />



**第二种是循环首次适应算法（next fit）**，该算法是由首次适应算法的变种，循环首次适应算法不再是每次从链表的开始进行查找，而是从上次找到的空闲分区的下⼀个空闲分区开始查找。如下图所示，P1 请求在内存块 A 完成分配，然后再为 P2 分配内存时，是直接继续向下寻找可用分区，最终在 B 内存块中完成分配。该算法相比⾸次适应算法空闲分区的分布更加均匀，而且查找的效率有所提升，但是正因为如此会造成空闲分区链中大的空闲分区会越来越少。

<img src=".\images\循环首次适应算法.png" style="zoom:50%;" />



**第三种是最佳适应算法（best fit）**，空闲分区链以空闲分区大小递增的顺序将空闲分区以双向链表的形式连接在一起，每次从空闲分区链的开头进行查找，这样第一个满足分配条件的空间分区就是最优解。如下图所示，在 A 内存块分配完 P1 请求后，空闲分区链重新按分区大小进行排序，再为 P2 请求查找满足条件的空闲分区。该算法的空间利用率更高，但同样也会留下很多较难利用的小空闲分区，由于每次分配完需要重新排序，所以会有造成性能损耗。

<img src=".\images\最佳适应算法.png" style="zoom:50%;" />



##### 伙伴算法

伙伴算法是一种非常经典的内存分配算法，它采用了分离适配的设计思想，将物理内存按照 2 的次幂进行划分，内存分配时也是按照 2 的次幂大小进行按需分配，例如 4KB、 8KB、16KB 等。假设我们请求分配的内存大小为 10KB，那么会按照 16KB 分配。

伙伴算法相对比较复杂，我们结合下面这张图来讲解它的分配原理。

<img src=".\images\伙伴算法.png" style="zoom: 33%;" />

伙伴算法把内存划分为 11 组不同的 2 次幂大小的内存块集合，每组内存块集合都用双向链表连接。链表中每个节点的内存块大小分别为 1、2、4、8、16、32、64、128、256、512 和 1024 个连续的 Page，例如第一组链表的节点为 2^0 个连续 Page，第二组链表的节点为 2^1 个连续 Page，以此类推。

假设我们需要分配 10K 大小的内存块，看下伙伴算法的具体分配过程：

1. 首先需要找到存储 2^4 连续 Page 所对应的链表，即数组下标为 4；
2. 查找 2^4 链表中是否有空闲的内存块，如果有则分配成功；
3. 如果 2^4 链表不存在空闲的内存块，则继续沿数组向上查找，即定位到数组下标为 5 的链表，链表中每个节点存储 2^5 的连续 Page；
4. 如果 2^5 链表中存在空闲的内存块，则取出该内存块并将它分割为 2 个 2^4 大小的内存块，其中一块分配给进程使用，剩余的一块链接到 2^4 链表中。

以上是伙伴算法的分配过程，那么释放内存时候伙伴算法又会发生什么行为呢？当进程使用完内存归还时，需要检查其伙伴块的内存是否释放，所谓伙伴块是不仅大小相同，而且两个块的地址是连续的，其中低地址的内存块起始地址必须为 2 的整数次幂。如果伙伴块是空闲的，那么就会将两个内存块合并成更大的块，然后重复执行上述伙伴块的检查机制。直至伙伴块是非空闲状态，那么就会将该内存块按照实际大小归还到对应的链表中。频繁的合并会造成 CPU 浪费，所以并不是每次释放都会触发合并操作，当链表中的内存块个数小于某个阈值时，并不会触发合并操作。

由此可见，伙伴算法有效地减少了外部碎片，但是有可能会造成非常严重的内部碎片，最严重的情况会带来 50% 的内存碎片。

> 二叉树的第𝑖![{\displaystyle i}](https://wikimedia.org/api/rest_v1/media/math/render/svg/add78d8608ad86e54951b8c8bd6c8d8416533d20)层至多拥有2𝑖−1![{\displaystyle 2^{i-1}}](https://wikimedia.org/api/rest_v1/media/math/render/svg/de838b503259acc792dd682654445984ea6e4c6d)个节点；深度为𝑘![{\displaystyle k}](https://wikimedia.org/api/rest_v1/media/math/render/svg/c3c9a2c7b599b37105512c5d570edc034056dd40)的二叉树至多总共有2𝑘+1−1![{\displaystyle 2^{\begin{aligned}k+1\end{aligned}}-1}](https://wikimedia.org/api/rest_v1/media/math/render/svg/f24729d4eae59094b7ed114e09dcbf142f32cde8)个节点（定义根节点所在深度 𝑘0=0![{\displaystyle k_{0}=0}](https://wikimedia.org/api/rest_v1/media/math/render/svg/93e97ea5847aa5aa81c2ecaca22b04c612a72c05))



##### Slab 算法

因为伙伴算法都是以 Page 为最小管理单位，在小内存的分配场景，伙伴算法并不适用，如果每次都分配一个 Page 岂不是非常浪费内存，因此 Slab 算法应运而生了。Slab 算法在伙伴算法的基础上，对小内存的场景专门做了优化，采用了内存池的方案，解决内部碎片问题。

Linux 内核使用的就是 Slab 算法，因为内核需要频繁地分配小内存，所以 Slab 算法提供了一种高速缓存机制，使用缓存存储内核对象，当内核需要分配内存时，基本上可以通过缓存中获取。此外 Slab 算法还可以支持通用对象的初始化操作，避免对象重复初始化的开销。下图是 Slab 算法的结构图，Slab 算法实现起来非常复杂，本文只做一个简单的了解。

<img src=".\images\Slab算法.png" style="zoom: 33%;" />

在 Slab 算法中维护着大小不同的 Slab 集合，在最顶层是 cache_chain，cache_chain 中维护着一组 kmem_cache 引用，kmem_cache 负责管理一块固定大小的对象池。通常会提前分配一块内存，然后将这块内存划分为大小相同的 slot，不会对内存块再进行合并，同时使用位图 bitmap 记录每个 slot 的使用情况。

kmem_cache 中包含三个 Slab 链表：**完全分配使用 slab_full**、**部分分配使用 slab_partial**和**完全空闲 slabs_empty**，这三个链表负责内存的分配和释放。每个链表中维护的 Slab 都是一个或多个连续 Page，每个 Slab 被分配多个对象进行存储。Slab 算法是基于对象进行内存管理的，它把相同类型的对象分为一类。当分配内存时，从 Slab 链表中划分相应的内存单元；当释放内存时，Slab 算法并不会丢弃已经分配的对象，而是将它保存在缓存中，当下次再为对象分配内存时，直接会使用最近释放的内存块。

单个 Slab 可以在不同的链表之间移动，例如当一个 Slab 被分配完，就会从 slab_partial 移动到 slabs_full，当一个 Slab 中有对象被释放后，就会从 slab_full 再次回到 slab_partial，所有对象都被释放完的话，就会从 slab_partial 移动到 slab_empty。

#### 

#### netty内存管理设计

##### 内存规格介绍

Netty 保留了内存规格分类的设计理念，不同大小的内存块采用的分配策略是不同的，具体内存规格的分类情况如下图所示。

<img src=".\images\netty内存规格.png" style="zoom:50%;" />

上图中 Tiny 代表 0 ~ 512B 之间的内存块，Samll 代表 512B ~ 8K 之间的内存块，Normal 代表 8K ~ 16M 的内存块，Huge 代表大于 16M 的内存块。在 Netty 中定义了一个 SizeClass 类型的枚举，用于描述上图中的内存规格类型，分别为 Tiny、Small 和 Normal。但是图中 Huge 并未在代码中定义，当分配大于 16M 时，可以归类为 Huge 场景，Netty 会直接使用非池化的方式进行内存分配。

Netty 在每个区域内又定义了更细粒度的内存分配单位，分别为 Chunk、Page、Subpage，我们将逐一对其进行介绍。

Chunk 是 Netty 向操作系统申请内存的单位，所有的内存分配操作也是基于 Chunk 完成的，Chunk 可以理解为 Page 的集合，每个 Chunk 默认大小为 16M。

Page 是 Chunk 用于管理内存的单位，Netty 中的 Page 的大小为 8K，不要与 Linux 中的内存页 Page 相混淆了。假如我们需要分配 64K 的内存，需要在 Chunk 中选取 8 个 Page 进行分配。

Subpage 负责 Page 内的内存分配，假如我们分配的内存大小远小于 Page，直接分配一个 Page 会造成严重的内存浪费，所以需要将 Page 划分为多个相同的子块进行分配，这里的子块就相当于 Subpage。按照 Tiny 和 Small 两种内存规格，SubPage 的大小也会分为两种情况。在 Tiny 场景下，最小的划分单位为 16B，按 16B 依次递增，16B、32B、48B …… 496B；在 Small 场景下，总共可以划分为 512B、1024B、2048B、4096B 四种情况。Subpage 没有固定的大小，需要根据用户分配的缓冲区大小决定，例如分配 1K 的内存时，Netty 会把一个 Page 等分为 8 个 1K 的 Subpage。

> 关于为什么是最小的内存规格为什么是16byte？
>
> 因为设计之初是为了对象存储 Java中一个最小的对象是16Byte(8 MarkWord + 4 指针 + 4对齐字节)
>
> 在 Hotspot VM 中，对象在内存中的存储布局分为 3 块区域：
>
> - 对象头（Header）
> - 实例数据（Instance Data）
> - 对齐填充（Padding）
>
> 对象头又包括三部分：MarkWord、元数据指针、数组长度。
>
> - MarkWord：用于存储对象运行时的数据，好比 HashCode、锁状态标志、GC分代年龄等。**这部分在 64 位操作系统下占 8 字节，32 位操作系统下占 4 字节。**
> - 指针：对象指向它的类元数据的指针，虚拟机通过这个指针来确定这个对象是哪一个类的实例。
>   这部分就涉及到指针压缩的概念，**在开启指针压缩的状况下占 4 字节，未开启状况下占 8 字节。**
> - 数组长度：这部分只有是数组对象才有，**若是是非数组对象就没这部分。这部分占 4 字节。**
>
> 实例数据就不用说了，用于存储对象中的各类类型的字段信息（包括从父类继承来的）。
>
> 关于对齐填充，Java 对象的大小默认是按照 8 字节对齐，也就是说 Java 对象的大小必须是 8 字节的倍数。若是算到最后不够 8 字节的话，那么就会进行对齐填充。



了解了 Netty 不同粒度的内存的分配单位后，我们接下来看看 Netty 中的 jemalloc 是如何实现的。

##### Netty内存池架构设计

Netty 中的内存池可以看作一个 Java 版本的 jemalloc 实现，并结合 JVM 的诸多特性做了部分优化。如下图所示，我们首先从全局视角看下 Netty 内存池的整体布局，对它有一个宏观的认识。

<img src=".\images\Netty内存池架构设计.png" style="zoom:50%;" />

#### netty内存管理相关类

内存分配分为pooled内存池缓存的 和 unpooled 直接分配内存 不做缓存的两种





###### AbstractByteBufAllocator



```java
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {

    //申请内存时的统一方法
    @Override
    public ByteBuf buffer(int initialCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }
    

    //堆内空间分配的方法
    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        //调用子类重写的方法 PooledByteBufAllocator
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

     /**
     * Create a heap {@link ByteBuf} with the given initialCapacity and maxCapacity.
     */
    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);

    /**
     * Create a direct {@link ByteBuf} with the given initialCapacity and maxCapacity.
     */
    protected abstract ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity);

}
```















##### 非缓存的内存分配

###### Unpooled

不做缓存直接分配的通常使用此类进行快速分配

```java
package io.netty.buffer;

public final class Unpooled {

    //核心分配类就是UnpooledByteBufAllocator 用于内存的分配工作
    private static final ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

    /**
     * Big endian byte order. 大端序
     */
    public static final ByteOrder BIG_ENDIAN = ByteOrder.BIG_ENDIAN;

    /**
     * Little endian byte order. 小端序
     */
    public static final ByteOrder LITTLE_ENDIAN = ByteOrder.LITTLE_ENDIAN;

    /**
     * A buffer whose capacity is {@code 0}. 空的ByteBuf
     */
    public static final ByteBuf EMPTY_BUFFER = ALLOC.buffer(0, 0);
    
    
        /**
     * Creates a new big-endian Java heap buffer with reasonably small initial capacity, which
     * expands its capacity boundlessly on demand.
     创建一个新的 big-endian Java 堆缓冲区，该缓冲区具有相当小的初始容量，可根据需要无限扩展其容量。
     */
    public static ByteBuf buffer() {
        return ALLOC.heapBuffer();
    }

    /**
     * Creates a new big-endian direct buffer with reasonably small initial capacity, which
     * expands its capacity boundlessly on demand.
     创建一个具有相当小的初始容量的新的大端序直接缓冲区，该缓冲区可根据需要无限扩展其容量。
     */
    public static ByteBuf directBuffer() {
        return ALLOC.directBuffer();
    }

}
```



###### UnpooledByteBufAllocator



```java
package io.netty.buffer;

/**
 * Simplistic {@link ByteBufAllocator} implementation that does not pool anything.
   不做缓存池的内存分配器
 */
public final class UnpooledByteBufAllocator extends AbstractByteBufAllocator {

        /**
     * Default instance which uses leak-detection for direct buffers.
     默认实例，对直接缓冲区使用泄漏检测。
     */
    public static final UnpooledByteBufAllocator DEFAULT =
            new UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred());
    
    
    
    //分配堆内的空间
	@Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        //通过判断是否能获取到sun.misc.Unsafe类来返回hasUnsafe
        return PlatformDependent.hasUnsafe() ? new UnpooledUnsafeHeapByteBuf(this, initialCapacity, maxCapacity)
                : new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }

    //分配堆外空间
    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
         //通过判断是否能获取到sun.misc.Unsafe类来返回hasUnsafe
        ByteBuf buf = PlatformDependent.hasUnsafe() ?
                UnsafeByteBufUtil.newUnsafeDirectByteBuf(this, initialCapacity, maxCapacity) :
                new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);

        return disableLeakDetector ? buf : toLeakAwareBuffer(buf);
    }
    

}
```



###### UnsafeByteBufUtil

```java
package io.netty.buffer;

final class UnsafeByteBufUtil {
    private static final boolean UNALIGNED = PlatformDependent.isUnaligned();
    private static final byte ZERO = 0;

    static UnpooledUnsafeDirectByteBuf newUnsafeDirectByteBuf(
            ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        
        //如果没有cleaner 那么就自己释放堆外空间
        if (PlatformDependent.useDirectBufferNoCleaner()) {
            return new UnpooledUnsafeNoCleanerDirectByteBuf(alloc, initialCapacity, maxCapacity);
        }
        //否则用自带的cleaner自动释放
        return new UnpooledUnsafeDirectByteBuf(alloc, initialCapacity, maxCapacity);
    }
}
```















##### 缓存的内存分配

netty维护了一个大的内存池进行内存分配和回收管理



###### PooledByteBufAllocator

带缓存的内存分配器 

```java
package io.netty.buffer;


public class PooledByteBufAllocator extends AbstractByteBufAllocator {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PooledByteBufAllocator.class);
    //默认的heapArena的大小
    private static final int DEFAULT_NUM_HEAP_ARENA;
    //默认的directArena的大小
    private static final int DEFAULT_NUM_DIRECT_ARENA;
	//默认的页大小
    private static final int DEFAULT_PAGE_SIZE;
    //默认的二叉树的最大层级 0 - 11 高度为12的二叉树
    private static final int DEFAULT_MAX_ORDER; // 8192 << 11 = 16 MiB per chunk
    
    //PoolThreadCache相关属性
    //分别代表tiny、small、normal规格下相同的内存块的数量限制
    private static final int DEFAULT_TINY_CACHE_SIZE;
    private static final int DEFAULT_SMALL_CACHE_SIZE;
    private static final int DEFAULT_NORMAL_CACHE_SIZE;
    //代表normal规格下缓存区的最大容量
    private static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY;
    //代表当超过此数字的空闲内存 进行一次缓存释放
    private static final int DEFAULT_CACHE_TRIM_INTERVAL;

    //最小的页大小
    private static final int MIN_PAGE_SIZE = 4096;
    //chunkSize的最大大小
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    static {
        //获取io.netty.allocator.pageSize的值 没有就设置为8192
        int defaultPageSize = SystemPropertyUtil.getInt("io.netty.allocator.pageSize", 8192);
        Throwable pageSizeFallbackCause = null;
        try {
            validateAndCalculatePageShifts(defaultPageSize);
        } catch (Throwable t) {
            pageSizeFallbackCause = t;
            defaultPageSize = 8192;
        }
        DEFAULT_PAGE_SIZE = defaultPageSize;

        //获取io.netty.allocator.maxOrder的值 没有就设置为11
        int defaultMaxOrder = SystemPropertyUtil.getInt("io.netty.allocator.maxOrder", 11);
        Throwable maxOrderFallbackCause = null;
        try {
            validateAndCalculateChunkSize(DEFAULT_PAGE_SIZE, defaultMaxOrder);
        } catch (Throwable t) {
            maxOrderFallbackCause = t;
            defaultMaxOrder = 11;
        }
        DEFAULT_MAX_ORDER = defaultMaxOrder;

        // Determine reasonable default for nHeapArena and nDirectArena.
        // Assuming each arena has 3 chunks, the pool should not consume more than 50% of max memory.
        //确定 nHeapArena 和 nDirectArena 的合理默认值。假设每个竞技场有 3 个块，则池消耗的最大内存不应超过 50%。
        final Runtime runtime = Runtime.getRuntime();

        // Use 2 * cores by default to reduce condition as we use 2 * cores for the number of EventLoops
        // in NIO and EPOLL as well. If we choose a smaller number we will run into hotspots as allocation and
        // deallocation needs to be synchronized on the PoolArena.
        // See https://github.com/netty/netty/issues/3888
        //默认设置为核心数*2 和EventLoopGroup的设置相同 因为要保证一个EventLoop可以分配到一个Arena 多线程下效率更高
        final int defaultMinNumArena = runtime.availableProcessors() * 2;
        //默认的chunkSize = 2^11 * 8192 
        final int defaultChunkSize = DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER;
        
        //确定 nHeapArena 和 nDirectArena 的合理默认值。假设每个竞技场有 3 个块，则池消耗的最大内存不应超过 50%。
        DEFAULT_NUM_HEAP_ARENA = Math.max(0,
                SystemPropertyUtil.getInt(
                        "io.netty.allocator.numHeapArenas",
                        (int) Math.min(
                                defaultMinNumArena,
                                runtime.maxMemory() / defaultChunkSize / 2 / 3)));
        DEFAULT_NUM_DIRECT_ARENA = Math.max(0,
                SystemPropertyUtil.getInt(
                        "io.netty.allocator.numDirectArenas",
                        (int) Math.min(
                                defaultMinNumArena,
                                PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3)));

        // cache sizes
        DEFAULT_TINY_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty.allocator.tinyCacheSize", 512);
        DEFAULT_SMALL_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty.allocator.smallCacheSize", 256);
        DEFAULT_NORMAL_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty.allocator.normalCacheSize", 64);

        // 32 kb is the default maximum capacity of the cached buffer. Similar to what is explained in
        // 'Scalable memory allocation using jemalloc'
        DEFAULT_MAX_CACHED_BUFFER_CAPACITY = SystemPropertyUtil.getInt(
                "io.netty.allocator.maxCachedBufferCapacity", 32 * 1024);

        // the number of threshold of allocations when cached entries will be freed up if not frequently used
        DEFAULT_CACHE_TRIM_INTERVAL = SystemPropertyUtil.getInt(
                "io.netty.allocator.cacheTrimInterval", 8192);

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.allocator.numHeapArenas: {}", DEFAULT_NUM_HEAP_ARENA);
            logger.debug("-Dio.netty.allocator.numDirectArenas: {}", DEFAULT_NUM_DIRECT_ARENA);
            if (pageSizeFallbackCause == null) {
                logger.debug("-Dio.netty.allocator.pageSize: {}", DEFAULT_PAGE_SIZE);
            } else {
                logger.debug("-Dio.netty.allocator.pageSize: {}", DEFAULT_PAGE_SIZE, pageSizeFallbackCause);
            }
            if (maxOrderFallbackCause == null) {
                logger.debug("-Dio.netty.allocator.maxOrder: {}", DEFAULT_MAX_ORDER);
            } else {
                logger.debug("-Dio.netty.allocator.maxOrder: {}", DEFAULT_MAX_ORDER, maxOrderFallbackCause);
            }
            logger.debug("-Dio.netty.allocator.chunkSize: {}", DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER);
            logger.debug("-Dio.netty.allocator.tinyCacheSize: {}", DEFAULT_TINY_CACHE_SIZE);
            logger.debug("-Dio.netty.allocator.smallCacheSize: {}", DEFAULT_SMALL_CACHE_SIZE);
            logger.debug("-Dio.netty.allocator.normalCacheSize: {}", DEFAULT_NORMAL_CACHE_SIZE);
            logger.debug("-Dio.netty.allocator.maxCachedBufferCapacity: {}", DEFAULT_MAX_CACHED_BUFFER_CAPACITY);
            logger.debug("-Dio.netty.allocator.cacheTrimInterval: {}", DEFAULT_CACHE_TRIM_INTERVAL);
        }
    }

    public static final PooledByteBufAllocator DEFAULT =
            new PooledByteBufAllocator(PlatformDependent.directBufferPreferred());

    //heapArea的数组
    private final PoolArena<byte[]>[] heapArenas;
    //directArena的数组
    private final PoolArena<ByteBuffer>[] directArenas;
    private final int tinyCacheSize;
    private final int smallCacheSize;
    private final int normalCacheSize;
    //heapArea的监视器
    private final List<PoolArenaMetric> heapArenaMetrics;
    //directArena的监视器
    private final List<PoolArenaMetric> directArenaMetrics;
    //线程本地缓存的对象
    private final PoolThreadLocalCache threadCache;

    public PooledByteBufAllocator() {
        this(false);
    }

    public PooledByteBufAllocator(boolean preferDirect) {
        this(preferDirect, DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }

    public PooledByteBufAllocator(boolean preferDirect, int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        this(preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder,
                DEFAULT_TINY_CACHE_SIZE, DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE);
    }

    public PooledByteBufAllocator(boolean preferDirect, int nHeapArena, int nDirectArena, int pageSize, int maxOrder,
                                  int tinyCacheSize, int smallCacheSize, int normalCacheSize) {
        //设置是否首选堆外内存
        super(preferDirect);
        //实例化PoolThreadLocalCache对象 只是简单的创建了对象 内部什么都没初始化
        threadCache = new PoolThreadLocalCache();
        //tiny、small、normal内存规格同一个大小的缓存数量
        this.tinyCacheSize = tinyCacheSize;
        this.smallCacheSize = smallCacheSize;
        this.normalCacheSize = normalCacheSize;
        
        //计算chunkSize大小 2^11 * 8192 = 16M
        final int chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);

        if (nHeapArena < 0) {
            throw new IllegalArgumentException("nHeapArena: " + nHeapArena + " (expected: >= 0)");
        }
        if (nDirectArena < 0) {
            throw new IllegalArgumentException("nDirectArea: " + nDirectArena + " (expected: >= 0)");
        }
		//计算页面偏移 log2(8192) = 13 
        int pageShifts = validateAndCalculatePageShifts(pageSize);

        //接下来就是对heapArena的创建工作
        if (nHeapArena > 0) {
            //创建一个nHeapArena大小的数组
            heapArenas = newArenaArray(nHeapArena);
            List<PoolArenaMetric> metrics = new ArrayList<PoolArenaMetric>(heapArenas.length);
            //对每个HeapArena创建对象 加入到监视器中
            for (int i = 0; i < heapArenas.length; i ++) {
                PoolArena.HeapArena arena = new PoolArena.HeapArena(this, pageSize, maxOrder, pageShifts, chunkSize);
                heapArenas[i] = arena;
                metrics.add(arena);
            }
            heapArenaMetrics = Collections.unmodifiableList(metrics);
        } else {
            heapArenas = null;
            heapArenaMetrics = Collections.emptyList();
        }

        if (nDirectArena > 0) {
            directArenas = newArenaArray(nDirectArena);
            List<PoolArenaMetric> metrics = new ArrayList<PoolArenaMetric>(directArenas.length);
            for (int i = 0; i < directArenas.length; i ++) {
                PoolArena.DirectArena arena = new PoolArena.DirectArena(
                        this, pageSize, maxOrder, pageShifts, chunkSize);
                directArenas[i] = arena;
                metrics.add(arena);
            }
            directArenaMetrics = Collections.unmodifiableList(metrics);
        } else {
            directArenas = null;
            directArenaMetrics = Collections.emptyList();
        }
    }
    
    
    //分配堆内空间
    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        //通过PoolThreadLocalCache来获取当前线程的PoolThreadCache
        PoolThreadCache cache = threadCache.get();
        //获取heapArena进行分配
        PoolArena<byte[]> heapArena = cache.heapArena;

        ByteBuf buf;
        if (heapArena != null) {
            //分配内存
            buf = heapArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            buf = new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
        }

        return toLeakAwareBuffer(buf);
    }
    
    
    
    
    
}
```











###### PoolArena

![](.\images\PoolArena.png)

```java
package io.netty.buffer;

abstract class PoolArena<T> implements PoolArenaMetric {
    static final boolean HAS_UNSAFE = PlatformDependent.hasUnsafe();

    //内存规格的枚举
    enum SizeClass {
        //在 Tiny 场景下，最小的划分单位为 16B，按 16B 依次递增，16B、32B、48B …… 496B； 共31种情况
        Tiny,
        //在 Small 场景下，总共可以划分为 512B、1024B、2048B、4096B 4种情况。
        Small,
        //normal则代表8k以上直到chunkSize大小
        Normal
    }

    //计算tinySubpage的缓存子页数组大小 32 但实际存储是从下标1开始到31
    static final int numTinySubpagePools = 512 >>> 4;

    //分配器
    final PooledByteBufAllocator parent;

    //最大的层级 11
    private final int maxOrder;
    //页面大小 8k
    final int pageSize;
    //页面偏移 13
    final int pageShifts;
    //chunk的大小 16M
    final int chunkSize;
    //快速计算是否满足分配tiny和small规格的掩码
    final int subpageOverflowMask;
    //small规格的subpage缓存子页数组的大小 4种情况
    final int numSmallSubpagePools;
    //tiny规格的子页缓存数组
    private final PoolSubpage<T>[] tinySubpagePools;
    //small规格的子页缓存数组
    private final PoolSubpage<T>[] smallSubpagePools;

    //管理使用率为50-100的Chunk列表
    private final PoolChunkList<T> q050;
    //管理使用率为25-75的Chunk列表
    private final PoolChunkList<T> q025;
    //管理使用率为1-50的Chunk列表
    private final PoolChunkList<T> q000;
    //管理使用率为0-25的Chunk列表
    private final PoolChunkList<T> qInit;
    //管理使用率为75-100的Chunk列表
    private final PoolChunkList<T> q075;
    //管理使用率为100的Chunk列表
    private final PoolChunkList<T> q100;

    //chunk的监视器列表
    private final List<PoolChunkListMetric> chunkListMetrics;

    // Metrics for allocations and deallocations
    //分配normal的计数
    private long allocationsNormal;
    // We need to use the LongCounter here as this is not guarded via synchronized block.
    //使用LongCounter进行不同内存规格的分配计数
    private final LongCounter allocationsTiny = PlatformDependent.newLongCounter();
    private final LongCounter allocationsSmall = PlatformDependent.newLongCounter();
    private final LongCounter allocationsHuge = PlatformDependent.newLongCounter();
    private final LongCounter activeBytesHuge = PlatformDependent.newLongCounter();

    //不同内存规格释放的计数
    private long deallocationsTiny;
    private long deallocationsSmall;
    private long deallocationsNormal;

    // We need to use the LongCounter here as this is not guarded via synchronized block.
    private final LongCounter deallocationsHuge = PlatformDependent.newLongCounter();

    // Number of thread caches backed by this arena.
    //当前arena支持的线程缓存数量
    final AtomicInteger numThreadCaches = new AtomicInteger();

    protected PoolArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        this.parent = parent;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        //这里取出来是-8192 前面为1 后面为0 便于后面进行快速位运算判断
        subpageOverflowMask = ~(pageSize - 1);
        //基于numTinySubpagePools = 32来创建tinySubpagePools数组
        tinySubpagePools = newSubpagePoolArray(numTinySubpagePools);
        for (int i = 0; i < tinySubpagePools.length; i ++) {
            //每个subpage只创建一个head
            tinySubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        //13-9 = 4 对应small的四种情况
        numSmallSubpagePools = pageShifts - 9;
        smallSubpagePools = newSubpagePoolArray(numSmallSubpagePools);
        for (int i = 0; i < smallSubpagePools.length; i ++) {
            //每个subpage只创建一个head
            smallSubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        //对不同使用率的chunkList进行初始化 链式绑定起来 
        q100 = new PoolChunkList<T>(null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList<T>(q100, 75, 100, chunkSize);
        q050 = new PoolChunkList<T>(q075, 50, 100, chunkSize);
        q025 = new PoolChunkList<T>(q050, 25, 75, chunkSize);
        q000 = new PoolChunkList<T>(q025, 1, 50, chunkSize);
        //init的存在是为了即使chunk不再被使用 也不会立即回收 放在init里面可以避免重复创建对象
        qInit = new PoolChunkList<T>(q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);

        //把chunkList加入到指标监视器里面
        List<PoolChunkListMetric> metrics = new ArrayList<PoolChunkListMetric>(6);
        metrics.add(qInit);
        metrics.add(q000);
        metrics.add(q025);
        metrics.add(q050);
        metrics.add(q075);
        metrics.add(q100);
        chunkListMetrics = Collections.unmodifiableList(metrics);
    }
    
    
    //分配内存
    PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        //新建一个ByteBuf对象 当作分配的这块内存的容器
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        allocate(cache, buf, reqCapacity);
        return buf;
    }
    
    private void allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {
        //将所需的内存大小进行规格化 
        final int normCapacity = normalizeCapacity(reqCapacity);
        
        //判断所需内存大小是否属于tiny或者small规格
        if (isTinyOrSmall(normCapacity)) { // capacity < pageSize
            int tableIdx;
            PoolSubpage<T>[] table;
            //判断是否是tiny规格
            boolean tiny = isTiny(normCapacity);
            if (tiny) { // < 512
                //用线程本地的cache分配一下试试看
                if (cache.allocateTiny(this, buf, reqCapacity, normCapacity)) {
                    // was able to allocate out of the cache so move on
                    //缓存分配成功直接返回
                    return;
                }
                //计算normCapacity在tinySubpagePools的下标位置 16在1下标
                tableIdx = tinyIdx(normCapacity);
                table = tinySubpagePools;
            } else {
                if (cache.allocateSmall(this, buf, reqCapacity, normCapacity)) {
                    // was able to allocate out of the cache so move on
                    return;
                }
                tableIdx = smallIdx(normCapacity);
                table = smallSubpagePools;
            }
		   
            //取到poolArena的tinySubpage对应下标的head节点
            final PoolSubpage<T> head = table[tableIdx];

            //加锁取poolArena中的子页池
            /**
             * Synchronize on the head. This is needed as {@link PoolChunk#allocateSubpage(int)} and
             * {@link PoolChunk#free(long)} may modify the doubly linked list as well.
             */
            synchronized (head) {
                final PoolSubpage<T> s = head.next;
                //如果只有head一个元素 也就是没有对应的子页
                if (s != head) {
                    assert s.doNotDestroy && s.elemSize == normCapacity;
                    long handle = s.allocate();
                    assert handle >= 0;
                    s.chunk.initBufWithSubpage(buf, handle, reqCapacity);

                    if (tiny) {
                        allocationsTiny.increment();
                    } else {
                        allocationsSmall.increment();
                    }
                    return;
                }
            }
            //那么就需要从正常的8k normal规格的页面中分割出多个子页面来
            allocateNormal(buf, reqCapacity, normCapacity);
            return;
        }
        //如果normCapacity属于normal规格
        if (normCapacity <= chunkSize) {
            //尝试在线程本地缓存中分配normal
            if (cache.allocateNormal(this, buf, reqCapacity, normCapacity)) {
                // was able to allocate out of the cache so move on
                return;
            }
            allocateNormal(buf, reqCapacity, normCapacity);
        } else {
            // Huge allocations are never served via the cache so just call allocateHuge
            //分配超过16M的内存 不缓存直接分配
            allocateHuge(buf, reqCapacity);
        }
    }
    
    
    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        //新建一个不缓存的chunk
        PoolChunk<T> chunk = newUnpooledChunk(reqCapacity);
        //加入统计
        activeBytesHuge.add(chunk.chunkSize());
        //初始化buf
        buf.initUnpooled(chunk, reqCapacity);
       	//分配huge的数量+1
        allocationsHuge.increment();
    }
    
    
   	private synchronized void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        //尝试从不同使用率的chunkList中分配
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
            q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
            q075.allocate(buf, reqCapacity, normCapacity)) {
            //分配normal的次数+1
            ++allocationsNormal;
            return;
        }
        //一个chunk都没有就需要新建chunk

        // Add a new chunk.
        PoolChunk<T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
        //利用chunk来分配内存 返回地址
        long handle = c.allocate(normCapacity);
        //分配normal页数量+1
        ++allocationsNormal;
        assert handle > 0;
        //初始化buf
        c.initBuf(buf, handle, reqCapacity);
        //加入qInit队列
        qInit.add(c);
    }
    
    

    
    
    //通过内存规格 找到对应的subpage的head
    PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        int tableIdx;
        PoolSubpage<T>[] table;
        //如果是tiny类型
        if (isTiny(elemSize)) { // < 512
            // 16B -> 对应下标1
            tableIdx = elemSize >>> 4;
            table = tinySubpagePools;
        } else {
            // 512B ——> 0
            // 1024B -> 1
            // 2048B -> 2
            // 4096 -> 3
            tableIdx = 0;
            elemSize >>>= 10;
            while (elemSize != 0) {
                elemSize >>>= 1;
                tableIdx ++;
            }
            table = smallSubpagePools;
        }
	    //返回对应规格的head
        return table[tableIdx];
    }
    
    
	static final class HeapArena extends PoolArena<byte[]> {

        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize);
        }

        @Override
        boolean isDirect() {
            return false;
        }
		
        //新建一个chunk
        @Override
        protected PoolChunk<byte[]> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new PoolChunk<byte[]>(this, new byte[chunkSize], pageSize, maxOrder, pageShifts, chunkSize);
        }
	}
    
    
    
	//确定当前申请容量是否是Tiny或者Small规格的
  	// capacity < pageSize
    boolean isTinyOrSmall(int normCapacity) {
        //原理是subpageOverflowMask是2的次幂-1取反 低位都是0 高位是1 
        //如果normCapacity小于8192 那么&结果就是全0 否则高位存在1不等于0
        return (normCapacity & subpageOverflowMask) == 0;
    }
	//确定当前申请容量是否是Tiny规格的
  	// normCapacity < 512
    static boolean isTiny(int normCapacity) {
        return (normCapacity & 0xFFFFFE00) == 0;
    }



//PoolArena中的normalizeCapacity方法 具有和 HashMap初始化容器大小的相同最接近2次幂的计算

 	int normalizeCapacity(int reqCapacity) {
        if (reqCapacity < 0) {
            throw new IllegalArgumentException("capacity: " + reqCapacity + " (expected: 0+)");
        }
     	//如果需要的容量大于chunkSize 16M 直接返回
        if (reqCapacity >= chunkSize) {
            return reqCapacity;
        }

     	//如果reqCapacity > tiny 处于small和normal的大小范围 
     	//这里就是和hashMap相同逻辑计算的部分 
        if (!isTiny(reqCapacity)) { // >= 512
            // Doubled
		   //核心逻辑是将当前数字的最高位后面的位全部置为1 然后再+1 就可以获得最接近的那个2次幂的数字	
            int normalizedCapacity = reqCapacity;
            normalizedCapacity --;
            normalizedCapacity |= normalizedCapacity >>>  1;
            normalizedCapacity |= normalizedCapacity >>>  2;
            normalizedCapacity |= normalizedCapacity >>>  4;
            normalizedCapacity |= normalizedCapacity >>>  8;
            normalizedCapacity |= normalizedCapacity >>> 16;
            normalizedCapacity ++;

            if (normalizedCapacity < 0) {
                normalizedCapacity >>>= 1;
            }

            return normalizedCapacity;
        }
     
     	//接下来处理reqCapacity处于tiny的范围

     	//如果reqCapacity是16的倍数
        // Quantum-spaced
        if ((reqCapacity & 15) == 0) {
            return reqCapacity;
        }
		//找到最接近的16的倍数
        return (reqCapacity & ~15) + 16;
    }
}
```





###### PoolSubpage

<img src=".\images\PoolSubpage.png" style="zoom: 67%;" />

<img src=".\images\PoolSubpage02.png" alt="PoolSubpage02" style="zoom: 33%;" />

```java

package io.netty.buffer;

//chunk设置为T泛型 因为其内部可能是byte[] 也可能是ByteBuffer
final class PoolSubpage<T> implements PoolSubpageMetric {

    //所属的chunk
    final PoolChunk<T> chunk;
    //所属chunk的内存下标
    private final int memoryMapIdx;
    //当前页面在chunk.memory中的偏移量
    private final int runOffset;
    //页面大小
    private final int pageSize;
    //子页的使用的位图
    private final long[] bitmap;

   
    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    boolean doNotDestroy;
    //子页面的大小
    int elemSize;
    //子页面的数量
    private int maxNumElems;
    //位图的实际使用长度
    private int bitmapLength;
    //下一个可用的位图索引
    private int nextAvail;
    //整个可用的位图索引数量
    private int numAvail;
    
    
    
    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        //按照最小规格16B的位图 一个bit占16B 记录当前子页面的哪些子页面被使用了 这里是直接设置的最大长度 实际长度需要看bitmapLength
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
        
        init(head, elemSize);
    }

    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;
        //划分的子页面大小
        this.elemSize = elemSize;
        if (elemSize != 0) {
            //计算一个page能被切分成多少个elemSize大小的子页面
            maxNumElems = numAvail = pageSize / elemSize;
            //下一个可用的位图索引设置为0
            nextAvail = 0;
            //实际的bitmap的长度 占几个long 
            bitmapLength = maxNumElems >>> 6;
            if ((maxNumElems & 63) != 0) {
                bitmapLength ++;
            }

            //bitmap全初始化为0
            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }
        //加入到arena的tinySubpagePool或者smallSubpagePool
        addToPool(head);
    }
    
    //加入到arena的tinySubpagePool或者smallSubpagePool
    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }
    
    
     long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

         //获取下一个可用的位图索引 nextAvail
        final int bitmapIdx = getNextAvail();
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) == 0;
         //设置位图下标为q的为已用
        bitmap[q] |= 1L << r;

         //可用的位图数量-1
        if (-- numAvail == 0) {
            //如果为0了 那么就从arena的subpagePool中移除 代表整个page都被分配完了 没有子页面了
            removeFromPool();
        }
		
         //计算出地址
        return toHandle(bitmapIdx);
    }
    
    //高32位是bitmapId 低32位是memoryId
    //0x4000000000000000L 是为了避免bitmapIdx为0 造成不知道到底有没有bitmapIdx
    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }
}
```













###### PoolChunk

<img src=".\images\PoolChunk.png" style="zoom:50%;" />

```java
package io.netty.buffer;

final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;
	
    //所属的arena
    final PoolArena<T> arena;
    //分配的内存
    final T memory;
    //是否缓存
    final boolean unpooled;

    //标记二叉树上 以当前节点为根节点的子树 最近的可以分配的内存块位于哪个层级
    private final byte[] memoryMap;
    //记录二叉树每个节点的深度
    private final byte[] depthMap;
    
    //所有叶子节点的数组 2048个  记录哪个叶子结点被拆分成子页面了
    private final PoolSubpage<T>[] subpages;
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    //用于判断大小是否超过normal的掩码
    private final int subpageOverflowMask;
    //页面大小 8k
    private final int pageSize;
    // 13 
    private final int pageShifts;
    // 0 - 11 层级
    private final int maxOrder;
    //chunk的大小
    private final int chunkSize;
    //取对数 log2(16M) = 24
    private final int log2ChunkSize;
    private final int maxSubpageAllocs;
    /** Used to mark memory as unusable */
    
    //标记当前二叉树结点 不可用的标记 12 
    private final byte unusable;

    //空闲内存大小
    private int freeBytes;

    //所在的chunkList
    PoolChunkList<T> parent;
    //前一个结点
    PoolChunk<T> prev;
    //后一个
    PoolChunk<T> next;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        //标记是缓存的
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        //标记当前二叉树结点 不可用的标记 12 
        unusable = (byte) (maxOrder + 1);
        log2ChunkSize = log2(chunkSize);
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        // 2^11 = 2048 全部叶子节点的数量
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        // 2048 * 2 = 4096 整个二叉树一共4096个节点
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        
        //给memoryMap进行初始化赋值 以当前节点为根节点的子树 可以分配的内存块位于哪个层级
        //当 当前节点的memoryMap[memoryMapIndex] = 当前的层级时 代表以这个节点为根的子树的内存都是空闲的
        //当 当前节点的memoryMap[memoryMapIndex] > 当前的层级时 代表当前节点不可被分配 最近可以被分配的子节点在memoryMap[memoryMapIndex]层
        //当 当前节点的memoryMap[memoryMapIndex] = unusable时 代表以当前节点为根的子树已经全部分配完了 不可分配
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }

        //所有叶子结点的数组 2048个   记录哪个叶子结点被拆分成子页面了
        subpages = newSubpageArray(maxSubpageAllocs);
    }
    
    
    
    //分配normCapacity大小的内存 返回地址
    long allocate(int normCapacity) {
        //如果normCapacity属于normal规格
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize
            //分配>8k的整页
            return allocateRun(normCapacity);
        } else {
            //否则切分tiny/small规格的子页面
            return allocateSubpage(normCapacity);
        }
    }
    
    //分配>8k的整页
    private long allocateRun(int normCapacity) {
        //计算出需要的内存大小在二叉树的哪一层 等同于用log2(normCapacity) - log(8k) = 算出normCapacity是8k的几个2倍
        //正好二叉树一层就是2倍 maxOrder 减 就是normCapacity所在的层数
        int d = maxOrder - (log2(normCapacity) - pageShifts);
        //在二叉树上分配d层的节点
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }
        //减少可用空间
        freeBytes -= runLength(id);
        return id;
    }
    
    //指定id的节点占用内存的大小
    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);
    }
    
    
   private long allocateSubpage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        //因为要切分tiny/small规格的子页面了 所以肯定把多余的子页面加入到arena的subpage池中 
        //获取对应规格的subpagePoolHead
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);
        synchronized (head) {
            //d代表层级 子页面只能从叶子节点的页面切分出来 所以 d = 11 
            int d = maxOrder; // subpages are only be allocated from pages i.e., leaves
            //取d = 11 层级的叶子页面 返回值是当前层级上的空闲页面id 也就是memoryMap的id
            int id = allocateNode(d);
            if (id < 0) {
                return id;
            }
			
            //获取叶子结点的页面集合 记录哪个叶子结点被拆分成子页面了
            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            //更新空闲空间的大小
            freeBytes -= pageSize;
			
            //把memoryMapId转换成叶子结点集合的下标
            int subpageIdx = subpageIdx(id);
            //通过下标取出来操作的是哪个叶子结点
            PoolSubpage<T> subpage = subpages[subpageIdx];
            if (subpage == null) {
                //新建subpage 将arena的tinyHead或者smallHead放进去 内部会挂上head
                // runOffset(id) 当前page在chunk.memory的偏移量
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                //记录哪个叶子结点被拆分成子页面了
                subpages[subpageIdx] = subpage;
            } else {
                subpage.init(head, normCapacity);
            }
            //subpage的对象初始化完了 接下来该分配内存了
            return subpage.allocate();
        }
    }
    
    
    private int allocateNode(int d) {
        //取id = 1的二叉树结点 也就是整个二叉树的根
        int id = 1;
        int initial = - (1 << d); // has last d bits = 0 and rest all = 1
        //取id下标的memoryMap的值 
        byte val = value(id);
        //如果根节点的值>想要查找的层级 那么证明 整棵树都没有d层级那么大的整内存块了 返回-1
        if (val > d) { // unusable
            return -1;
        }
        
        //否则进入循环
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            //通过 * 2 不断向下移动层级
            id <<= 1;
            //获取以当前节点为根的 可以分配内存的子树的层级
            val = value(id);
            //如果当前节点存储的层级比查找层级要大 证明这个节点的子树分配不了d层的内存大小了
            if (val > d) {
                //找它的兄弟节点
                id ^= 1;
                val = value(id);
            }
        }
        //最终当val = d 的时候跳出 代表当前节点就是d层的 可以满足d层的内存分配需求
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);
        //标记这个节点为unusable 被使用了
        setValue(id, unusable); // mark as unusable
        //递归更新父节点的val值 
        updateParentsAlloc(id);
        return id;
    }
    
    
    
    private void updateParentsAlloc(int id) {
        //更新到id为1的根节点跳出
        while (id > 1) {
            //获取父节点的id
            int parentId = id >>> 1;
            //当前节点的val1
            byte val1 = value(id);
            //兄弟节点的val2
            byte val2 = value(id ^ 1);
            byte val = val1 < val2 ? val1 : val2;
            //设置给父节点两个子节点中更小的
            setValue(parentId, val);
            //父节点设置为当前节点
            id = parentId;
        }
    }

  
    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        //取低32位的memoryMapId
        int memoryMapIdx = memoryMapIdx(handle);
        //取高32位的bitmapId
        int bitmapIdx = bitmapIdx(handle);
        if (bitmapIdx == 0) {
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            buf.init(this, handle, runOffset(memoryMapIdx), reqCapacity, runLength(memoryMapIdx),
                     arena.parent.threadCache());
        } else {
            //以子页面的形式初始化buf
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }
    
    
    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;
	    //取低32位的memoryMapId
        int memoryMapIdx = memoryMapIdx(handle);
	    //以memoryMapId转换成叶子节点的集合下标 
        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        //计算子页面在chunk.memory中的偏移量
        // runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize
        // 指定memoryMapIdx的page在chunk.memory的下标起始地址 + bitmap中已经被使用的子页面数量 * 子页面大小 = 当前子页面在chunk.memory的起始地址
        buf.init(
            this, handle,
            runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, reqCapacity, subpage.elemSize,
            arena.parent.threadCache());
    }
    

	//如果两个比特相同（都是0或都是1），那么异或的结果为0。
    //如果两个比特不同（一个是0，另一个是1），那么异或的结果为1。
	//通过memoryMapIdx的下标计算subpage的下标位置 
	//因为maxSubpageAllocs = 2048 
	//异或运算在这里被用作一种技巧来清除 memoryMapIdx 最高位的设置位。当 maxSubpageAllocs 是一个2的幂次方时，它的二进制表示只有一个位是1，其余都是0。进行异或运算时，如果 memoryMapIdx 的最高位也是1，则通过异或会将这一位清零，而不会影响到其他位。返回值是处理后的 memoryMapIdx，此时最高位的1已经被清除，剩下的部分可以看作是在叶子页面集合内的索引。
	private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }
}
```





###### PoolChunkList

<img src=".\images\PoolChunkList.png" style="zoom:50%;" />

<img src=".\images\PoolChunkList02.png" style="zoom:50%;" />

```java

package io.netty.buffer;


final class PoolChunkList<T> implements PoolChunkListMetric {
    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();
    
    //下一个PoolChunkList
    private final PoolChunkList<T> nextList;
    //最小使用率
    private final int minUsage;
    //最大使用率
    private final int maxUsage;
    //列表最大容量
    private final int maxCapacity;

    //chunk的head
    private PoolChunk<T> head;

    //前一个PoolChunkList
    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunkList(PoolChunkList<T> nextList, int minUsage, int maxUsage, int chunkSize) {
        assert minUsage <= maxUsage;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
        maxCapacity = calculateMaxCapacity(minUsage, chunkSize);
    }
    
    void add(PoolChunk<T> chunk) {
        //chunk的使用率超过当前chunkList的最大值 移动到下一个chunkList
        if (chunk.usage() >= maxUsage) {
            nextList.add(chunk);
            return;
        }
        add0(chunk);
    }
    
    //从PoolChunkList中分配
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (head == null || normCapacity > maxCapacity) {
            // Either this PoolChunkList is empty or the requested capacity is larger then the capacity which can
            // be handled by the PoolChunks that are contained in this PoolChunkList.
            return false;
        }

        //取head的chunk
        for (PoolChunk<T> cur = head;;) {
            //利用chunk进行分配内存
            long handle = cur.allocate(normCapacity);
            //分配不成功 用下一个PoolChunk分配
            if (handle < 0) {
                cur = cur.next;
                if (cur == null) {
                    return false;
                }
            } else {
                //否则初始化buf
                cur.initBuf(buf, handle, reqCapacity);
                //重新计算使用率
                if (cur.usage() >= maxUsage) {
                    //超过最大使用率 移动到下一个链表
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
    }
}
```







###### PoolThreadLocalCache



```java
//PooledByteBufAllocator的内部类 
//PooledByteBufAllocator中封装了一个PoolThreadLocalCache属性threadCache
//本身就是一个threadLocal 线程本地变量
//只不过在第一次获取时会进行initialValue() 调用子类方法进行对线程本地变量内部填充
//释放threadLocal时也会调用子类onRemoval() 来释放响应的内存

final class PoolThreadLocalCache extends FastThreadLocal<PoolThreadCache> {

    	//调用threadCache.get()方法时会调用此方法进行初始化
        @Override
        protected synchronized PoolThreadCache initialValue() {
            //获取最少使用的heapArena 
            final PoolArena<byte[]> heapArena = leastUsedArena(heapArenas);
            //获取最少使用的directArena
            final PoolArena<ByteBuffer> directArena = leastUsedArena(directArenas);

            //创建PoolThreadCache对象 真正的线程本地的内存缓存
            return new PoolThreadCache(
                    heapArena, directArena, tinyCacheSize, smallCacheSize, normalCacheSize,
                    DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);
        }

        @Override
        protected void onRemoval(PoolThreadCache threadCache) {
            threadCache.free();
        }

    	//获取最少使用的arena
        private <T> PoolArena<T> leastUsedArena(PoolArena<T>[] arenas) {
            if (arenas == null || arenas.length == 0) {
                return null;
            }
			
            //根据numThreadCaches来获取最少被线程绑定的arena
            PoolArena<T> minArena = arenas[0];
            for (int i = 1; i < arenas.length; i++) {
                PoolArena<T> arena = arenas[i];
                if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                    minArena = arena;
                }
            }

            return minArena;
        }
    }
}
```













###### PoolThreadCache

<img src=".\images\MemoryRegionCache.png" style="zoom: 33%;" />

<img src=".\images\MemoryRegionCache02.png" alt="MemoryRegionCache02" style="zoom: 50%;" />

```java
package io.netty.buffer;

//线程本地的内存缓存
final class PoolThreadCache {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PoolThreadCache.class);

    //缓存的heapArena
    final PoolArena<byte[]> heapArena;
    //缓存的directArena
    final PoolArena<ByteBuffer> directArena;

    //堆内/堆外空间的tiny、small、normal规格的子页缓存
    // Hold the caches for the different size classes, which are tiny, small and normal.
    private final MemoryRegionCache<byte[]>[] tinySubPageHeapCaches;
    private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;
    private final MemoryRegionCache<ByteBuffer>[] tinySubPageDirectCaches;
    private final MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;
    private final MemoryRegionCache<byte[]>[] normalHeapCaches;
    private final MemoryRegionCache<ByteBuffer>[] normalDirectCaches;

    // Used for bitshifting when calculate the index of normal caches later
    private final int numShiftsNormalDirect;
    private final int numShiftsNormalHeap;
    //表示当缓存中的分配次数超过此阈值时，会触发一次缓存清理操作，释放不再需要的缓冲区，以便回收内存。
    private final int freeSweepAllocationThreshold;

    private int allocations;

    //当前PoolThreadCache绑定的线程
    private final Thread thread = Thread.currentThread();
    
    private final Runnable freeTask = new Runnable() {
        @Override
        public void run() {
            free0();
        }
    };

    PoolThreadCache(PoolArena<byte[]> heapArena, PoolArena<ByteBuffer> directArena,
                    int tinyCacheSize, int smallCacheSize, int normalCacheSize,
                    int maxCachedBufferCapacity, int freeSweepAllocationThreshold) {
        if (maxCachedBufferCapacity < 0) {
            throw new IllegalArgumentException("maxCachedBufferCapacity: "
                    + maxCachedBufferCapacity + " (expected: >= 0)");
        }
        if (freeSweepAllocationThreshold < 1) {
            throw new IllegalArgumentException("freeSweepAllocationThreshold: "
                    + freeSweepAllocationThreshold + " (expected: > 0)");
        }
        
        
        
        //表示当缓存中的分配次数超过此阈值时，会触发一次缓存清理操作，释放不再需要的缓冲区，以便回收内存。
        this.freeSweepAllocationThreshold =freeSweepAllocationThreshold;
        this.heapArena = heapArena;
        this.directArena = directArena;
        
        if (directArena != null) {
            tinySubPageDirectCaches = createSubPageCaches(
                    tinyCacheSize, PoolArena.numTinySubpagePools, SizeClass.Tiny);
            smallSubPageDirectCaches = createSubPageCaches(
                    smallCacheSize, directArena.numSmallSubpagePools, SizeClass.Small);

            numShiftsNormalDirect = log2(directArena.pageSize);
            normalDirectCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, directArena);

            directArena.numThreadCaches.getAndIncrement();
        } else {
            // No directArea is configured so just null out all caches
            tinySubPageDirectCaches = null;
            smallSubPageDirectCaches = null;
            normalDirectCaches = null;
            numShiftsNormalDirect = -1;
        }
       	
        //direct和heap的分配一样 只分析heap
        if (heapArena != null) {
            // Create the caches for the heap allocations
            //创建tiny规格的subpage缓存数组
            tinySubPageHeapCaches = createSubPageCaches(
                    tinyCacheSize, PoolArena.numTinySubpagePools, SizeClass.Tiny);
            //创建small规格的subpage缓存数组
            smallSubPageHeapCaches = createSubPageCaches(
                    smallCacheSize, heapArena.numSmallSubpagePools, SizeClass.Small);
			
            //13
            numShiftsNormalHeap = log2(heapArena.pageSize);
            
            //创建normal的缓存  设置cacheSize大小 设置最大缓存区容量
            normalHeapCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, heapArena);

            //当前heapArena的线程使用数量+1
            heapArena.numThreadCaches.getAndIncrement();
        } else {
            // No heapArea is configured so just null out all caches
            tinySubPageHeapCaches = null;
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
            numShiftsNormalHeap = -1;
        }

        // The thread-local cache will keep a list of pooled buffers which must be returned to
        // the pool when the thread is not alive anymore.
        ThreadDeathWatcher.watch(thread, freeTask);
    }

    //创建tiny和small类型内存规格的缓存
    private static <T> MemoryRegionCache<T>[] createSubPageCaches(
            int cacheSize, int numCaches, SizeClass sizeClass) {
        if (cacheSize > 0) {
            @SuppressWarnings("unchecked")
            //numCaches使用32、4 代表tiny和small规格的情况
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                // TODO: maybe use cacheSize / cache.length
                //每个情况下分别能存放512、个相同规格的内存
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize, sizeClass);
            }
            return cache;
        } else {
            return null;
        }
    }

    private static <T> MemoryRegionCache<T>[] createNormalCaches(
            int cacheSize, int maxCachedBufferCapacity, PoolArena<T> area) {
        if (cacheSize > 0) {
            //取chunkSize和maxCachedBufferCapacity更小值
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);
            //算出数组的长度  通过log2计算出
            // 0 ---> 1个页面 8k
            // 1 ---> 2个页面 16k
            // 2 ---> 3个页面 24k
            //........
            int arraySize = Math.max(1, log2(max / area.pageSize) + 1);
		   
            @SuppressWarnings("unchecked")
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[arraySize];
            for (int i = 0; i < cache.length; i++) {
                //对每个NormalMemoryRegionCache设置64的cacheSize大小
                cache[i] = new NormalMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    
    //利用cache分配tiny大小的内存
    boolean allocateTiny(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForTiny(area, normCapacity), buf, reqCapacity);
    }
    
    //利用cache分配normal大小的内存
    boolean allocateNormal(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForNormal(area, normCapacity), buf, reqCapacity);
    }
    
    //根据规格大小计算所属的MemoryRegionCache
    private MemoryRegionCache<?> cacheForTiny(PoolArena<?> area, int normCapacity) {
        //通过normCapacity计算当前规格所在的MemoryRegionCache
        //比如 16B的规格应该在第1位
        int idx = PoolArena.tinyIdx(normCapacity);
        if (area.isDirect()) {
            return cache(tinySubPageDirectCaches, idx);
        }
        //通过tinySubPageHeapCaches和下标索引找到对应的缓存
        return cache(tinySubPageHeapCaches, idx);
    }
    
    //计算所属的MemoryRegionCache
    private MemoryRegionCache<?> cacheForNormal(PoolArena<?> area, int normCapacity) {
        if (area.isDirect()) {
            int idx = log2(normCapacity >> numShiftsNormalDirect);
            return cache(normalDirectCaches, idx);
        }
        //计算MemoryRegionCache的下标
        //算出数组的长度  通过log2计算出
        // 0 ---> 1个页面 8k
        // 1 ---> 2个页面 16k
        // 2 ---> 3个页面 24k
        //........
        int idx = log2(normCapacity >> numShiftsNormalHeap);
        //取下标位idx的MemoryRegionCache
        return cache(normalHeapCaches, idx);
    }
    
    private static <T> MemoryRegionCache<T> cache(MemoryRegionCache<T>[] cache, int idx) {
        if (cache == null || idx > cache.length - 1) {
            return null;
        }
        //直接返回对应的下标缓存
        return cache[idx];
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean allocate(MemoryRegionCache<?> cache, PooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        //利用cache来分配缓存
        boolean allocated = cache.allocate(buf, reqCapacity);
        //如果分配次数超过了阈值 那么进行一次清理
        if (++ allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        return allocated;
    }
    
	private abstract static class MemoryRegionCache<T> {
        //相同内存大小的子页的数量上限
        private final int size;
        //相同内存大小的子页的队列
        private final Queue<Entry<T>> queue;
        //内存规格
        private final SizeClass sizeClass;
        //分配次数
        private int allocations;

        MemoryRegionCache(int size, SizeClass sizeClass) {
            this.size = MathUtil.findNextPositivePowerOfTwo(size);
            queue = PlatformDependent.newFixedMpscQueue(this.size);
            this.sizeClass = sizeClass;
        }
    
        public final boolean allocate(PooledByteBuf<T> buf, int reqCapacity) {
            //从队列中拿出一个子页
            Entry<T> entry = queue.poll();
            //如果没有直接返回空
            if (entry == null) {
                return false;
            }
            //否则进行分配 初始化buf
            initBuf(entry.chunk, entry.handle, buf, reqCapacity);
            //回收这个子页
            entry.recycle();
		   //分配次数+1
            // allocations is not thread-safe which is fine as this is only called from the same thread all time.
            ++ allocations;
            return true;
        }
    }
    


}

```

















##### ByteBuf相关类





###### ByteBuf



```java
package io.netty.buffer;

public abstract class ByteBuf implements ReferenceCounted, Comparable<ByteBuf> {



}
```



###### AbstractByteBuf











###### UnpooledHeapByteBuf

```java
package io.netty.buffer;

//Big endian Java 堆缓冲区实现
public class UnpooledHeapByteBuf extends AbstractReferenceCountedByteBuf {

    //分配器
    private final ByteBufAllocator alloc;
    //堆内开辟的空间
    byte[] array;
    private ByteBuffer tmpNioBuf;
    
    //通过new byte[] 的方式开辟堆内空间
     protected UnpooledHeapByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        this(alloc, new byte[initialCapacity], 0, 0, maxCapacity);
    }

    //操作数组时更多采用System的工具类来操作
    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }
}    
```













###### UnpooledUnsafeHeapByteBuf



```java
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

//使用unsafe类来操作堆内存数组的类
final class UnpooledUnsafeHeapByteBuf extends UnpooledHeapByteBuf {

    UnpooledUnsafeHeapByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    //和UnpooledHeapByteBuf的区别就是操作数组用的是unsafe类
    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return UnsafeByteBufUtil.getByte(array, index);
    }

}
```



###### UnpooledDirectByteBuf



```java
package io.netty.buffer;


public class UnpooledDirectByteBuf extends AbstractReferenceCountedByteBuf {

    private final ByteBufAllocator alloc;

    //包装了Nio的ByteBuffer 这里就是DirectByteBuffer
    private ByteBuffer buffer;
    private ByteBuffer tmpNioBuf;
    private int capacity;
    private boolean doNotFree;
    
    
    protected UnpooledDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity);
        }
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity);
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = alloc;
        //实际的内存分配采用的是ByteBuffer.allocateDirect的内存分配方式
        setByteBuffer(ByteBuffer.allocateDirect(initialCapacity));
    }
}   
```



###### UnpooledUnsafeDirectByteBuf



```java
public class UnpooledUnsafeDirectByteBuf extends AbstractReferenceCountedByteBuf {

    private final ByteBufAllocator alloc;

    //和UnpooledDirectByteBuf不同的是记录了分配堆外空间内存的地址
    private long memoryAddress;
    private ByteBuffer tmpNioBuf;
    private int capacity;
    private boolean doNotFree;
    ByteBuffer buffer;
	
    protected UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity);
        }
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity);
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = alloc;
        //依然是通过ByteBuffer.allocateDirect来分配堆外空间
        setByteBuffer(allocateDirect(initialCapacity), false);
    }
    
    protected ByteBuffer allocateDirect(int initialCapacity) {
        return ByteBuffer.allocateDirect(initialCapacity);
    }
}
```



###### UnpooledUnsafeNoCleanerDirectByteBuf



```java
package io.netty.buffer;

final class UnpooledUnsafeNoCleanerDirectByteBuf extends UnpooledUnsafeDirectByteBuf {

    UnpooledUnsafeNoCleanerDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
    }

    //分配内存的时候使用unsafe分配
    @Override
    protected ByteBuffer allocateDirect(int initialCapacity) {
        return PlatformDependent.allocateDirectNoCleaner(initialCapacity);
    }

    //释放内存的时候手动释放 不依靠cleaner
    @Override
    protected void freeDirect(ByteBuffer buffer) {
        PlatformDependent.freeDirectNoCleaner(buffer);
    }
}
```

















#### 其他拓展知识

##### 缓存行对齐

CPU在读取缓存的时候，因为缓存具有空间局部性，所以会将目标地址及其周围的数据都加载到缓存中，当多个线程修改同一个缓存行内的数据时，根据缓存一致性原则，会通知其他CPU的缓存行失效，所以造成了性能下降，这就是缓存行对齐的必要性。

<img src=".\images\f3e479fee2e60c7d4412d9fe30030569.png" alt="img" style="zoom:50%;" />

### 7.线程

#### 线程创建相关类

##### ThreadPerTaskExecutor

封装了线程工厂创造线程的执行器

```java
package io.netty.util.concurrent;

//继承了Executor 这样就能把所有执行的操作都抽象到Executor层
//调用Executor.execute(Runnable command)方法的时候 子类可能是执行器组 可能是执行器 也可能是 线程工厂执行器
public final class ThreadPerTaskExecutor implements Executor {
    
    //线程工厂
    private final ThreadFactory threadFactory;

    public ThreadPerTaskExecutor(ThreadFactory threadFactory) {
        this.threadFactory = ObjectUtil.checkNotNull(threadFactory, "threadFactory");
    }

    @Override
    public void execute(Runnable command) {
        //线程工厂新建一个线程来执行command 启动线程
        threadFactory.newThread(command).start();
    }
}
```

##### DefaultThreadFactory

```java
package io.netty.util.concurrent;


//具有 ThreadFactory 简单命名规则的实现。
public class DefaultThreadFactory implements ThreadFactory {

    private static final AtomicInteger poolId = new AtomicInteger();

    //自增id 用于生成线程名称
    private final AtomicInteger nextId = new AtomicInteger();
    //线程名称前缀
    private final String prefix;
    //是否守护进程
    private final boolean daemon;
    //线程优先级 默认5
    private final int priority;
    //线程组
    protected final ThreadGroup threadGroup;
    
    
    //生成poolType首字母小写的线程名称
    public static String toPoolName(Class<?> poolType) {
        ObjectUtil.checkNotNull(poolType, "poolType");

        //获取类名
        String poolName = StringUtil.simpleClassName(poolType);
        switch (poolName.length()) {
            case 0:
                return "unknown";
            case 1:
                return poolName.toLowerCase(Locale.US);
            default:
                if (Character.isUpperCase(poolName.charAt(0)) && Character.isLowerCase(poolName.charAt(1))) {
                    return Character.toLowerCase(poolName.charAt(0)) + poolName.substring(1);
                } else {
                    return poolName;
                }
        }
    }

    public DefaultThreadFactory(String poolName, boolean daemon, int priority, ThreadGroup threadGroup) {
        ObjectUtil.checkNotNull(poolName, "poolName");

        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "priority: " + priority + " (expected: Thread.MIN_PRIORITY <= priority <= Thread.MAX_PRIORITY)");
        }
		
        //线程名称前缀 nioEventLoop-1-
        prefix = poolName + '-' + poolId.incrementAndGet() + '-';
        this.daemon = daemon;
        this.priority = priority;
        this.threadGroup = threadGroup;
    }

    public DefaultThreadFactory(String poolName, boolean daemon, int priority) {
        this(poolName, daemon, priority, System.getSecurityManager() == null ?
                Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup());
    }

    //创建线程的方法
    @Override
    public Thread newThread(Runnable r) {
        Thread t = newThread(FastThreadLocalRunnable.wrap(r), prefix + nextId.incrementAndGet());
        try {
            //设置守护线程
            if (t.isDaemon() != daemon) {
                t.setDaemon(daemon);
            }
			//设置优先级
            if (t.getPriority() != priority) {
                t.setPriority(priority);
            }
        } catch (Exception ignored) {
            // Doesn't matter even if failed to set.
        }
        //返回该线程 此时还没启动
        return t;
    }
	
    //创建一个FastThreadLocalThread线程
    protected Thread newThread(Runnable r, String name) {
        return new FastThreadLocalThread(threadGroup, r, name);
    }
    
    
}
```











#### 线程及线程本地缓存

##### Thread



```java
package java.lang;

public class Thread implements Runnable {

    /* ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class. */
    //线程本地管理的ThreadLocalMap
    ThreadLocal.ThreadLocalMap threadLocals = null;




}
```





##### ThreadLocal

<img src="D:\doc\my\studymd\LearningNotes\framework\netty\images\ThreadLocal.png" style="zoom:33%;" />

当调用 ThreadLocal.set() 添加 Entry 对象时，是如何解决 Hash 冲突的呢？

这就需要我们了解线性探测法的实现原理。每个 ThreadLocal 在初始化时都会有一个 Hash 值为 threadLocalHashCode，每增加一个 ThreadLocal， Hash 值就会固定增加一个魔术 HASH_INCREMENT = 0x61c88647。为什么取 0x61c88647 这个魔数呢？实验证明，通过 0x61c88647 累加生成的 threadLocalHashCode 与 2 的幂取模，得到的结果可以较为均匀地分布在长度为 2 的幂大小的数组中。

为了便于理解，我们采用一组简单的数据模拟 ThreadLocal.set() 的过程是如何解决 Hash 冲突的。

1. threadLocalHashCode = 4，threadLocalHashCode & 15 = 4；此时数据应该放在数组下标为 4 的位置。下标 4 的位置正好没有数据，可以存放。
2. threadLocalHashCode = 19，threadLocalHashCode & 15 = 4；但是下标 4 的位置已经有数据了，如果当前需要添加的 Entry 与下标 4 位置已存在的 Entry 两者的 key 相同，那么该位置 Entry 的 value 将被覆盖为新的值。我们假设 key 都是不相同的，所以此时需要向后移动一位，下标 5 的位置没有冲突，可以存放。
3. threadLocalHashCode = 33，threadLocalHashCode & 15 = 3；下标 3 的位置已经有数据，向后移一位，下标 4 位置还是有数据，继续向后查找，发现下标 6 没有数据，可以存放。

ThreadLocal.get() 的过程也是类似的，也是根据 threadLocalHashCode 的值定位到数组下标，然后判断当前位置 Entry 对象与待查询 Entry 对象的 key 是否相同，如果不同，继续向下查找。由此可见，ThreadLocal.set()/get() 方法在数据密集时很容易出现 Hash 冲突，需要 O(n) 时间复杂度解决冲突问题，效率较低。

下面我们再聊聊 ThreadLocalMap 中 Entry 的设计原理。Entry 继承自弱引用类 WeakReference，Entry 的 key 是弱引用，value 是强引用。在 JVM 垃圾回收时，只要发现了弱引用的对象，不管内存是否充足，都会被回收。那么为什么 Entry 的 key 要设计成弱引用呢？我们试想下，如果 key 都是强引用，当 ThreadLocal 不再使用时，然而 ThreadLocalMap 中还是存在对 ThreadLocal 的强引用，那么 GC 是无法回收的，从而造成内存泄漏。

虽然 Entry 的 key 设计成了弱引用，但是当 ThreadLocal 不再使用被 GC 回收后，ThreadLocalMap 中可能出现 Entry 的 key 为 NULL，那么 Entry 的 value 一直会强引用数据而得不到释放，只能等待线程销毁。那么应该如何避免 ThreadLocalMap 内存泄漏呢？ThreadLocal 已经帮助我们做了一定的保护措施，在执行 ThreadLocal.set()/get() 方法时，ThreadLocal 会清除 ThreadLocalMap 中 key 为 NULL 的 Entry 对象，让它还能够被 GC 回收。除此之外，当线程中某个 ThreadLocal 对象不再使用时，立即调用 remove() 方法删除 Entry 对象。如果是在异常的场景中，记得在 finally 代码块中进行清理，保持良好的编码意识。

```java
package java.lang;


public class ThreadLocal<T> {

    //计算ThreadLocal的hashCode
    private final int threadLocalHashCode = nextHashCode();

 
    private static AtomicInteger nextHashCode =
        new AtomicInteger();

    private static final int HASH_INCREMENT = 0x61c88647;

    //每增加一个ThreadLocal就加一个HASH_INCREMENT来初始化hashCode值
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    //给子类用于继承初始化value
    protected T initialValue() {
        return null;
    }

    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }
    
    //存放value
    public void set(T value) {
        //从当前线程中取出ThreadLocalMap
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            //存放threadLocal和value
            map.set(this, value);
        } else {
            createMap(t, value);
        }
    }
    

    //获取当前ThreadLocal的value
    public T get() {
        //获取当前线程
        Thread t = Thread.currentThread();
        //从当前线程中取出ThreadLocalMap
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        //没找到 调用初始化方法
        return setInitialValue();
    }
    
    
    //获取当前线程的ThreadLocalMap
   	ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }
    
    
    
    
    static class ThreadLocalMap {

        //继承WeakReference 弱引用 一旦用户的强引用ThreadLocal不再使用 就会回收ThreadLocal
        //但value必须手动remove()方法释放
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        //初始化容量
        private static final int INITIAL_CAPACITY = 16;

        //放置Entry的数组
        private Entry[] table;

        //数组里存放的threadLocal的数量
        private int size = 0;

        //扩容阈值 当size达到这个值 进行扩容
        private int threshold; // Default to 0
        
        
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            //获取当前table
            Entry[] tab = table;
            int len = tab.length;
            //计算新增Entry的下标 hashCode对len取余
            int i = key.threadLocalHashCode & (len-1);

            //使用线性探测法 当hashCode产生冲突时 顺序+1尝试存放 直到数组尽头或者e==null有空闲位置
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get();

                if (k == key) {
                    e.value = value;
                    return;
                }

                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            //将Entry放在这个位置
            tab[i] = new Entry(key, value);
            int sz = ++size;
            //尝试清理掉一些threadLocal已经释放 但是没有释放value的Entry
            //size>threshold 扩容
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }
        
        //i++
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }
        
        //获取对应的Entry
        private Entry getEntry(ThreadLocal<?> key) {
            //根据threadLocal计算hash值
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
            //如果这个位置上的Entry就是存的ThreadLocal的Entry
            if (e != null && e.get() == key)
                return e;
            else
                //否则有hash冲突 继续顺序向下找
                return getEntryAfterMiss(key, i, e);
        }
        
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            //顺序从i不停找
            while (e != null) {
                ThreadLocal<?> k = e.get();
                //直到找到这个threadLocal才返回
                if (k == key)
                    return e;
                if (k == null)
                    //顺带清理一下threadLocal = null的Entry
                    expungeStaleEntry(i);
                else
                    //i++
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }
     

    }
}
```













##### FastThreadLocalThread

<img src="D:\doc\my\studymd\LearningNotes\framework\netty\images\FastThreadLocalThread.png" style="zoom: 50%;" />

```java
package io.netty.util.concurrent;

//提供对FastThreadLocal变量的快速访问的特殊Thread
//继承了Thread
public class FastThreadLocalThread extends Thread {

    //内部仿照Thread 设计了一个InternalThreadLocalMap 用于存放FastThreadLocal的值
  private InternalThreadLocalMap threadLocalMap;

    public FastThreadLocalThread() { }

    public FastThreadLocalThread(Runnable target) {
        super(target);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public FastThreadLocalThread(String name) {
        super(name);
    }

    public FastThreadLocalThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public FastThreadLocalThread(Runnable target, String name) {
        super(target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    /**
     * Returns the internal data structure that keeps the thread-local variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }

    /**
     * Sets the internal data structure that keeps the thread-local variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }

}
```















##### FastThreadLocal

```java
package io.netty.util.concurrent;

//netty的threadlocal 比jdk的threadlocal更快
public class FastThreadLocal<V> {

    
    // 下标为0 用于存放所有FastThreadLocal Set集合的位置
    private static final int variablesToRemoveIndex = InternalThreadLocalMap.nextVariableIndex();
    
    //当前FastThreadLocal被分配的下标 
    //一个FastThreadLocal会被分配一个固定的下标
    private final int index;

    public FastThreadLocal() {
        //获取下标
        index = InternalThreadLocalMap.nextVariableIndex();
    }
    
    
    public final void set(V value) {
        //当前值如果不是unset 那么就设置
        if (value != InternalThreadLocalMap.UNSET) {
            //获取当前线程的InternalThreadLocalMap
            set(InternalThreadLocalMap.get(), value);
        } else {
            remove();
        }
    }
    
    
    public final void set(InternalThreadLocalMap threadLocalMap, V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            //拿到InternalThreadLocalMap 直接在对应的index中存放value
            if (threadLocalMap.setIndexedVariable(index, value)) {
                //存放成功后 在0下标的Set集合中加上当前这个FastThreadLocal
                addToVariablesToRemove(threadLocalMap, this);
            }
        } else {
            remove(threadLocalMap);
        }
    }
    
    //在0下标的Set集合中加FastThreadLocal
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        //获取0下标的集合
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        Set<FastThreadLocal<?>> variablesToRemove;
        //没有就创建一个Set集合
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<FastThreadLocal<?>, Boolean>());
            threadLocalMap.setIndexedVariable(variablesToRemoveIndex, variablesToRemove);
        } else {
            variablesToRemove = (Set<FastThreadLocal<?>>) v;
        }
		//加上FastThreadLocal
        variablesToRemove.add(variable);
    }
    
    

    //get方法 通过FastThreadLocalThread内部的InternalThreadLocalMap来get
 	public final V get() {
        return get(InternalThreadLocalMap.get());
    }

    /**
     * Returns the current value for the specified thread local map.
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final V get(InternalThreadLocalMap threadLocalMap) {
        //如果get到了指定的值 直接返回
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }
		//否则进行一下初始化赋值
        return initialize(threadLocalMap);
    }

    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            //初始化赋值 调用子类的initialValue()方法
            v = initialValue();
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        //将值设置进入线程本地缓存的map中去
        threadLocalMap.setIndexedVariable(index, v);
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    
   	public final void remove() {
        remove(InternalThreadLocalMap.getIfSet());
    }
    
    public final void remove(InternalThreadLocalMap threadLocalMap) {
        if (threadLocalMap == null) {
            return;
        }

        //从当前InternalThreadLocalMap中移除当前FastThreadLocal
        Object v = threadLocalMap.removeIndexedVariable(index);
        //从0下标的Set集合中移除当前FastThreadLocal
        removeFromVariablesToRemove(threadLocalMap, this);

        if (v != InternalThreadLocalMap.UNSET) {
            try {
                //调用FastThreadLocal的onRemoval()方法
                onRemoval((V) v);
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        }
    }

    //移除0下标集合中的FastThreadLocal
    private static void removeFromVariablesToRemove(
            InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {

        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);

        if (v == InternalThreadLocalMap.UNSET || v == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }
    
    
    //子类继承的初始化value的方法
    protected V initialValue() throws Exception {
        return null;
    }

    //子类继承的移除方法
    /**
     * Invoked when this thread local variable is removed by {@link #remove()}.
     */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception { }

    
    
    //移除当前线程的所有FastThreadLocal的方法
    public static void removeAll() {
        //获取当前线程的InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return;
        }

        try {
            //拿出0下标集合
            Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
            if (v != null && v != InternalThreadLocalMap.UNSET) {
                @SuppressWarnings("unchecked")
                Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
                FastThreadLocal<?>[] variablesToRemoveArray =
                        variablesToRemove.toArray(new FastThreadLocal[variablesToRemove.size()]);
                //对每一个FastThreadLocal调用remove方法进行移除
                for (FastThreadLocal<?> tlv: variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            //最后移除整个InternalThreadLocalMap
            InternalThreadLocalMap.remove();
        }
    }


}
```







##### InternalThreadLocalMap

从 InternalThreadLocalMap 内部实现来看，与 ThreadLocalMap 一样都是采用数组的存储方式。但是 InternalThreadLocalMap 并没有使用线性探测法来解决 Hash 冲突，而是在 FastThreadLocal 初始化的时候分配一个数组索引 index，index 的值采用原子类 AtomicInteger 保证顺序递增，通过调用 InternalThreadLocalMap.nextVariableIndex() 方法获得。然后在读写数据的时候通过数组下标 index 直接定位到 FastThreadLocal 的位置，时间复杂度为 O(1)。如果数组下标递增到非常大，那么数组也会比较大，所以 FastThreadLocal 是通过空间换时间的思想提升读写性能。下面通过一幅图描述 InternalThreadLocalMap、index 和 FastThreadLocal 之间的关系。

<img src="D:\doc\my\studymd\LearningNotes\framework\netty\images\InternalThreadLocalMap.png" style="zoom: 33%;" />

通过上面 FastThreadLocal 的内部结构图，我们对比下与 ThreadLocal 有哪些区别呢？FastThreadLocal 使用 Object 数组替代了 Entry 数组，Object[0] 存储的是一个Set> 集合，从数组下标 1 开始都是直接存储的 value 数据，不再采用 ThreadLocal 的键值对形式进行存储。

假设现在我们有一批数据需要添加到数组中，分别为 value1、value2、value3、value4，对应的 FastThreadLocal 在初始化的时候生成的数组索引分别为 1、2、3、4。如下图所示。

<img src="D:\doc\my\studymd\LearningNotes\framework\netty\images\InternalThreadLocalMap02.png" style="zoom: 33%;" />



```java
package io.netty.util.internal;

//FastThreadLocalThread内部存放FastThreadLocal的map
public final class InternalThreadLocalMap extends UnpaddedInternalThreadLocalMap {

    private static final int DEFAULT_ARRAY_LIST_INITIAL_CAPACITY = 8;

    //标志填充空闲空间的对象
    public static final Object UNSET = new Object();
    
    
    public static InternalThreadLocalMap get() {
        //获取当前线程
        Thread thread = Thread.currentThread();
        if (thread instanceof FastThreadLocalThread) {
            //如果是FastThreadLocalThread
            return fastGet((FastThreadLocalThread) thread);
        } else {
            //普通Thread
            return slowGet();
        }
    }
    
    
    private static InternalThreadLocalMap fastGet(FastThreadLocalThread thread) {
        //获取FastThreadLocalThread中的InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = thread.threadLocalMap();
        if (threadLocalMap == null) {
            //如果没有 进行创建
            thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
        }
        //返回
        return threadLocalMap;
    }

    private static InternalThreadLocalMap slowGet() {
        //使用父类UnpaddedInternalThreadLocalMap中存放的ThreadLocal<InternalThreadLocalMap>
        ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = UnpaddedInternalThreadLocalMap.slowThreadLocalMap;
        //通过jdk的ThreadLocal来获取存放的InternalThreadLocalMap
        InternalThreadLocalMap ret = slowThreadLocalMap.get();
        if (ret == null) {
            ret = new InternalThreadLocalMap();
            slowThreadLocalMap.set(ret);
        }
        return ret;
    }
    
    
    public boolean setIndexedVariable(int index, Object value) {
        //获取存放FastThreadLocal的value的集合
        Object[] lookup = indexedVariables;
        //如果下标在数组len内 直接存放
        if (index < lookup.length) {
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET;
        } else {
            //否则进行扩容
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }
    
    //扩容
    private void expandIndexedVariableTableAndSet(int index, Object value) {
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        //根据index来算新扩容的大小 最接近index的2的幂次
        int newCapacity = index;
        newCapacity |= newCapacity >>>  1;
        newCapacity |= newCapacity >>>  2;
        newCapacity |= newCapacity >>>  4;
        newCapacity |= newCapacity >>>  8;
        newCapacity |= newCapacity >>> 16;
        newCapacity ++;

        //数据迁移
        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        newArray[index] = value;
        indexedVariables = newArray;
    }
    
    
    //移除指定下标的value
    public Object removeIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object v = lookup[index];
            lookup[index] = UNSET;
            return v;
        } else {
            return UNSET;
        }
    }
    
    
    public static void remove() {
        Thread thread = Thread.currentThread();
        if (thread instanceof FastThreadLocalThread) {
            //移除InternalThreadLocalMap
            ((FastThreadLocalThread) thread).setThreadLocalMap(null);
        } else {
            //移除InternalThreadLocalMap
            slowThreadLocalMap.remove();
        }
    }
    
}
```











##### UnpaddedInternalThreadLocalMap



```java
package io.netty.util.internal;


class UnpaddedInternalThreadLocalMap {

    //jdk的ThreadLocal 兼容不是FastThreadLocalThread的情况
    static final ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = new ThreadLocal<InternalThreadLocalMap>();
    //全局自增的索引下标
    static final AtomicInteger nextIndex = new AtomicInteger();

    //存放value的数组
    /** Used by {@link FastThreadLocal} */
    Object[] indexedVariables;

    //下面就是缓存了各种各样的线程本地变量 减少线程冲突用的
    // Core thread-locals
    int futureListenerStackDepth;
    int localChannelReaderStackDepth;
    Map<Class<?>, Boolean> handlerSharableCache;
    IntegerHolder counterHashCode;
    ThreadLocalRandom random;
    Map<Class<?>, TypeParameterMatcher> typeParameterMatcherGetCache;
    Map<Class<?>, Map<String, TypeParameterMatcher>> typeParameterMatcherFindCache;

    // String-related thread-locals
    StringBuilder stringBuilder;
    Map<Charset, CharsetEncoder> charsetEncoderCache;
    Map<Charset, CharsetDecoder> charsetDecoderCache;

    // ArrayList-related thread-locals
    ArrayList<Object> arrayList;

    UnpaddedInternalThreadLocalMap(Object[] indexedVariables) {
        this.indexedVariables = indexedVariables;
    }
}

```















