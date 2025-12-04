# java基础

## 强软弱虚

强引用 StrongReference、软引用 SoftReference、弱引用 WeakReference 和虚引用 PhantomReference

### 强引用

```java
//这种就是强引用了，是不是在代码中随处可见，最亲切。 只要某个对象有强引用与之关联，这个对象永远不会被回收，即使内存不足，JVM宁愿抛出OOM，也不会去回收。
Object o = new Object();
//那么什么时候才可以被回收呢？当强引用和对象之间的关联被中断了，就可以被回收了。
o = null;
```

### 软引用

```java
       //当内存不足，会触发JVM的GC，如果GC后，内存还是不足，就会把软引用的包裹的对象给干掉，也就是只有在内存不足，JVM才会回收该对象。
	   //常用做 缓存系统
	    SoftReference<byte[]> softReference = new SoftReference<byte[]>(new byte[1024*1024*10]);
        System.out.println(softReference.get());
        System.gc();
        System.out.println(softReference.get());

        byte[] bytes = new byte[1024 * 1024 * 10];
        System.out.println(softReference.get());
```

### 弱引用

```java
        //弱引用的特点是不管内存是否足够，只要发生GC，都会被回收： 
	    //常用做 伴生引用
	    WeakReference<byte[]> weakReference = new WeakReference<byte[]>(new byte[1]);
        System.out.println(weakReference.get());
        System.gc();
        System.out.println(weakReference.get());
```

### 虚引用

```java
      //无法通过虚引用来获取对一个对象的真实引用。

      //虚引用必须与ReferenceQueue一起使用，当GC准备回收一个对象，如果发现它还有虚引用，就会在回收之前，把这个虚引用加入到与之关联的ReferenceQueue中。
	//常用做 跟踪对象
	    ReferenceQueue queue = new ReferenceQueue();
        List<byte[]> bytes = new ArrayList<>();
        PhantomReference<Student> reference = new PhantomReference<Student>(new Student(),queue);
        new Thread(() -> {
            for (int i = 0; i < 100;i++ ) {
                bytes.add(new byte[1024 * 1024]);
            }
        }).start();

        new Thread(() -> {
            while (true) {
                Reference poll = queue.poll();
                if (poll != null) {
                    System.out.println("虚引用被回收了：" + poll);
                }
            }
        }).start();
        Scanner scanner = new Scanner(System.in);
        scanner.hasNext();
```



实际对虚引用和Cleaner处理的类在Reference中

```java
package java.lang.ref;


public abstract class Reference<T> {
    
    
    //在对象被JVM回收后 在这里对cleaner和虚引用做处理
    //只是调用cleaner的clean方法 以及把当前虚引用加入到绑定的引用队列中而已
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

### 最终引用

```java
package java.lang.ref;

class FinalReference<T> extends Reference<T> {

    public FinalReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

    @Override
    public boolean enqueue() {
        throw new InternalError("should never reach here");
    }
}
```

### Finalizer

```java
package java.lang.ref;

//当对象重写了finalize方法 那么就会在回收时封装一个Finalizer对象进行处理
final class Finalizer extends FinalReference<Object> { /* Package-private; must be in
                                                          same package as the Reference
                                                          class */
	//包含了所有Finalizer的队列
    private static ReferenceQueue<Object> queue = new ReferenceQueue<>();
    //队列头
    private static Finalizer unfinalized = null;
    private static final Object lock = new Object();

    //双向队列
    private Finalizer next = null , prev = null;
    
    
    //静态代码块中启动处理Finalizer的FinalizerThread
    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());
        Thread finalizer = new FinalizerThread(tg);
        finalizer.setPriority(Thread.MAX_PRIORITY - 2);
        finalizer.setDaemon(true);
        finalizer.start();
    }
    
    //jvm进行调用的创建对象的方法 将对象和queue绑定
    private Finalizer(Object finalizee) {
        super(finalizee, queue);
        //
        add();
    }
    
    //加到Finalizer的队列中
    private void add() {
        synchronized (lock) {
            //头插法
            if (unfinalized != null) {
                this.next = unfinalized;
                unfinalized.prev = this;
            }
            unfinalized = this;
        }
    }
    
    
    //处理Finalizer的线程
    private static class FinalizerThread extends Thread {
        private volatile boolean running;
        FinalizerThread(ThreadGroup g) {
            super(g, "Finalizer");
        }
        public void run() {
            // in case of recursive call to run()
            if (running)
                return;

            // Finalizer thread starts before System.initializeSystemClass
            // is called.  Wait until JavaLangAccess is available
            while (!VM.isBooted()) {
                // delay until VM completes initialization
                try {
                    VM.awaitBooted();
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
            final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
            running = true;
            //死循环调用Finalizer的finalize()方法
            for (;;) {
                try {
                    Finalizer f = (Finalizer)queue.remove();
                    f.runFinalizer(jla);
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
        }
    }
    
    
    private void runFinalizer(JavaLangAccess jla) {
        synchronized (this) {
            if (hasBeenFinalized()) return;
            remove();
        }
        try {
            Object finalizee = this.get();
            if (finalizee != null && !(finalizee instanceof java.lang.Enum)) {
                //通过jvm调用finalize()方法
                jla.invokeFinalize(finalizee);

                /* Clear stack slot containing this variable, to decrease
                   the chances of false retention with a conservative GC */
                finalizee = null;
            }
        } catch (Throwable x) { }
        //由于finalize()方法可能能重新将对象和其他对象产生关联 所以实际上JVM并没有清理掉对象 绑定的对象并没有被清理掉 需要手动进行清理
        //referent = null 让JVM可以清理掉对象了
        super.clear();
    }
}

```





### Reference

强引用并不会被加入到ReferenceHandler进行处理，只有软弱虚final四种继承的类的对象，才会在绑定对象被回收后，放入到ReferenceHandler进行处理

<img src=".\images\Reference.png" alt="image-20240802153111223" style="zoom:50%;" />

```java
package java.lang.ref;


public abstract class Reference<T> {
    
    //当前引用对象
    private T referent;         /* Treated specially by GC */

    //当前引用对象绑定的ReferenceQueue
    volatile ReferenceQueue<? super T> queue;

    /* When active:   NULL
     *     pending:   this
     *    Enqueued:   next reference in queue (or this if last)
     *    Inactive:   this
     */
    //用于ReferenceQueue中链表的构成
    @SuppressWarnings("rawtypes")
    volatile Reference next;

    /* When active:   next element in a discovered reference list maintained by GC (or this if last)
     *     pending:   next element in the pending list (or null if last)
     *   otherwise:   NULL
     */
    //由JVM赋值的 被回收对象的链表构成 通过这个找到下一个Reference
    transient private Reference<T> discovered;  /* used by VM */

    /* Object used to synchronize with the garbage collector.  The collector
     * must acquire this lock at the beginning of each collection cycle.  It is
     * therefore critical that any code holding this lock complete as quickly
     * as possible, allocate no new objects, and avoid calling user code.
     */
    static private class Lock { }
    private static Lock lock = new Lock();


    /* List of References waiting to be enqueued.  The collector adds
     * References to this list, while the Reference-handler thread removes
     * them.  This list is protected by the above lock object. The
     * list uses the discovered field to link its elements.
     */
    //由JVM赋值的 被回收对象的链表构成 链表的头
    private static Reference<Object> pending = null;
    
    
    
     static {
         //静态代码块 启动ReferenceHandler 对垃圾回收的对象进行处理 
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        /* If there were a special system-only priority greater than
         * MAX_PRIORITY, it would be used here
         */
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();

        // provide access in SharedSecrets
        SharedSecrets.setJavaLangRefAccess(new JavaLangRefAccess() {
            @Override
            public boolean tryHandlePendingReference() {
                return tryHandlePending(false);
            }
        });
    }

    
    
    
     private static class ReferenceHandler extends Thread {

        private static void ensureClassInitialized(Class<?> clazz) {
            try {
                Class.forName(clazz.getName(), true, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw (Error) new NoClassDefFoundError(e.getMessage()).initCause(e);
            }
        }

        static {
            // pre-load and initialize InterruptedException and Cleaner classes
            // so that we don't get into trouble later in the run loop if there's
            // memory shortage while loading/initializing them lazily.
            ensureClassInitialized(InterruptedException.class);
            ensureClassInitialized(Cleaner.class);
        }

        ReferenceHandler(ThreadGroup g, String name) {
            super(g, name);
        }

         //死循环执行 处理pending链表
        public void run() {
            while (true) {
                tryHandlePending(true);
            }
        }
    }
    
    
    
    //处理pending链表的对象
    static boolean tryHandlePending(boolean waitForNotify) {
        Reference<Object> r;
        Cleaner c;
        try {
            synchronized (lock) {
                //如果pending链表的头不为空
                if (pending != null) {
                    //取出Reference
                    r = pending;
                    // 'instanceof' might throw OutOfMemoryError sometimes
                    // so do this before un-linking 'r' from the 'pending' chain...
                    //如果这个Reference属于Cleaner 就赋值
                    c = r instanceof Cleaner ? (Cleaner) r : null;
                    // unlink 'r' from 'pending' chain
                    //把当前这个Reference从pending链表中去除
                    pending = r.discovered;
                    r.discovered = null;
                } else {
                    //pending为空的话 等待JVM唤醒
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

        //调用Cleaner.clean()
        // Fast path for cleaners
        if (c != null) {
            c.clean();
            return true;
        }

        //如果当前Reference有绑定的ReferenceQueue 
        //把这个Reference加入到ReferenceQueue中
        ReferenceQueue<? super Object> q = r.queue;
        if (q != ReferenceQueue.NULL) q.enqueue(r);
        return true;
    }

}
```











### ReferenceQueue



```java
package java.lang.ref;

public class ReferenceQueue<T> {

    /**
     * Constructs a new reference-object queue.
     */
    public ReferenceQueue() { }

    private static class Null<S> extends ReferenceQueue<S> {
        boolean enqueue(Reference<? extends S> r) {
            return false;
        }
    }

    static ReferenceQueue<Object> NULL = new Null<>();
    static ReferenceQueue<Object> ENQUEUED = new Null<>();

    static private class Lock { };
    private Lock lock = new Lock();
    //当前引用队列的头
    private volatile Reference<? extends T> head = null;
    private long queueLength = 0;

    //由ReferenceHandler调用 将Reference插入到绑定的队列中
    boolean enqueue(Reference<? extends T> r) { /* Called only by Reference class */
        synchronized (lock) {
            // Check that since getting the lock this reference hasn't already been
            // enqueued (and even then removed)
            ReferenceQueue<?> queue = r.queue;
            if ((queue == NULL) || (queue == ENQUEUED)) {
                return false;
            }
            assert queue == this;
            r.queue = ENQUEUED;
            //头插法 插入队列
            r.next = (head == null) ? r : head;
            head = r;
            queueLength++;
            //如果当前引用是FinalReference类型的 那么计数器+1
            if (r instanceof FinalReference) {
                sun.misc.VM.addFinalRefCount(1);
            }
            //唤醒所有阻塞的线程
            lock.notifyAll();
            return true;
        }
    }
    
    //删除此队列中的下一个引用对象，直到其中一个对象可用或给定的超时期限到期为止。
    public Reference<? extends T> remove(long timeout)
        throws IllegalArgumentException, InterruptedException
    {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout value");
        }
        synchronized (lock) {
            Reference<? extends T> r = reallyPoll();
            if (r != null) return r;
            long start = (timeout == 0) ? 0 : System.nanoTime();
            for (;;) {
                lock.wait(timeout);
                r = reallyPoll();
                if (r != null) return r;
                if (timeout != 0) {
                    long end = System.nanoTime();
                    timeout -= (end - start) / 1000_000;
                    if (timeout <= 0) return null;
                    start = end;
                }
            }
        }
}

```



## Unsafe类的一些知识

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

### 大小端

> ### 大端（Big Endian）
>
> 在大端字节顺序中，高字节存储在低地址处，低字节存储在高地址处。也就是说，最显著的字节（即最高有效字节）位于地址最低的内存位置上，而最低显著的字节位于地址最高的内存位置上。
>
> #### 示例
>
> - 最高**有效字节**（MSB）为 `0x12`。
> - 最低**有效字节**（LSB）为 `0x34`。
>
> 那么，在大端系统中，这个整数会被存储为：
>
> - 地址 `0x0000`：`0x12`（最高有效字节）
> - 地址 `0x0001`：`0x34`（最低有效字节）
>
> #### 二进制表示
>
> - 地址 `0x0000`：`00010010`（最高有效字节）
> - 地址 `0x0001`：`00110100`（最低有效字节）
>
> ### 小端（Little Endian）
>
> 在小端字节顺序中，低字节存储在低地址处，高字节存储在高地址处。也就是说，最低显著的字节位于地址最低的内存位置上，而最显著的字节位于地址最高的内存位置上。
>
> #### 示例
>
> - 最高**有效字节**（MSB）为 `0x12`。
> - 最低**有效字节**（LSB）为 `0x34`。
>
> 那么，在小端系统中，这个整数会被存储为：
>
> - 地址 `0x0000`：`0x34`（最低有效字节）
> - 地址 `0x0001`：`0x12`（最高有效字节）
>
> #### 二进制表示
>
> - 地址 `0x0000`：`00110100`（最低有效字节）
> - 地址 `0x0001`：`00010010`（最高有效字节）

**大小端的差异是以字节为单位的字节序不同，而不是以bit为单位**

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
		//理想中 如果是大端序 因为高位字节位于地址低位 这个68的一个字节数据会写入到四个字节的第一个字节的位置 即最高位字节 形成 01000100 01101011001010000000000 这样的拼接的东西
        System.out.println("读出来的数字:" + unsafe.getInt(allocateMemory));
		//可实质上 读出的数字是 2000000068 
		//对比来看一下
		//1110111001101011001010000000000 2000000000
		//1110111001101011001010001000100 2000000068
		//                       01000100  68
		//也就是说 68被直接写到了低位字节位置上 
		//那么证明这台机器其实是小端排列 低位字节在第一个位置 写入的时候覆盖了低位字节 读取的时候Java做了大小端屏蔽 统一大端输出 所以才出现了这样的结果
		//这里实际上是因为本台机器是小端 那么低地址存储的是低位字节 所以在写入一个字节时 覆盖低位
```



## Class

```java
//通过应用类加载器可以加载到java.class.path下的文件   	
ClassLoader classLoader = Main.class.getClassLoader();
        URL resource = classLoader.getResource("hello.c");
        System.out.println(resource);
//通常java.class.path的所有扫描路径保存在properties中
        Properties properties = System.getProperties();
        System.out.println(System.getProperty("java.class.path"));

//getResource("") 方法由类加载器调用，传入空字符串作为参数，表示获取类加载器的默认资源查找路径。
//对于 Maven 项目，在编译后的 target/classes 目录对应的路径
URL resource1 = Test.class.getClassLoader().getResource("");
```


```cmd
#java.class.path下包含当前项目的class文件路径以及依赖的jar包路径
#java.class.path的部分来源于jvm启动时的-classpath参数 在idea中默认会将所有的依赖的jar包都添加到classpath中

C:\Users\Administrator\.jdks\corretto-1.8.0_412\bin\java.exe 
"-javaagent:D:\softwares\idea\IntelliJ IDEA 2023.1\lib\idea_rt.jar=52337:D:\softwares\idea\IntelliJ IDEA 2023.1\bin" 
-Dfile.encoding=UTF-8 
-classpath C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\charsets.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\access-bridge-64.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\cldrdata.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\dnsns.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\jaccess.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\jfxrt.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\localedata.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\nashorn.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\sunec.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\sunjce_provider.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\sunmscapi.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\sunpkcs11.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\zipfs.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\jce.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\jfr.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\jfxswt.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\jsse.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\management-agent.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\resources.jar;C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\rt.jar;D:\doc\my\studymd\LearningNotes\framework\spring\springstudycode\target\classes;D:\softwares\LocalRepository\com\google\zxing\core\3.3.3\core-3.3.3.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot-starter-web\2.3.1.RELEASE\spring-boot-starter-web-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot-starter\2.3.1.RELEASE\spring-boot-starter-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot\2.3.1.RELEASE\spring-boot-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot-autoconfigure\2.3.1.RELEASE\spring-boot-autoconfigure-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot-starter-logging\2.3.1.RELEASE\spring-boot-starter-logging-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\ch\qos\logback\logback-classic\1.2.3\logback-classic-1.2.3.jar;D:\softwares\LocalRepository\ch\qos\logback\logback-core\1.2.3\logback-core-1.2.3.jar;D:\softwares\LocalRepository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;D:\softwares\LocalRepository\org\apache\logging\log4j\log4j-to-slf4j\2.13.3\log4j-to-slf4j-2.13.3.jar;D:\softwares\LocalRepository\org\apache\logging\log4j\log4j-api\2.13.3\log4j-api-2.13.3.jar;D:\softwares\LocalRepository\org\slf4j\jul-to-slf4j\1.7.30\jul-to-slf4j-1.7.30.jar;D:\softwares\LocalRepository\jakarta\annotation\jakarta.annotation-api\1.3.5\jakarta.annotation-api-1.3.5.jar;D:\softwares\LocalRepository\org\yaml\snakeyaml\1.26\snakeyaml-1.26.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot-starter-json\2.3.1.RELEASE\spring-boot-starter-json-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-databind\2.11.0\jackson-databind-2.11.0.jar;D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-annotations\2.11.0\jackson-annotations-2.11.0.jar;D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-core\2.11.0\jackson-core-2.11.0.jar;D:\softwares\LocalRepository\com\fasterxml\jackson\datatype\jackson-datatype-jdk8\2.11.0\jackson-datatype-jdk8-2.11.0.jar;D:\softwares\LocalRepository\com\fasterxml\jackson\datatype\jackson-datatype-jsr310\2.11.0\jackson-datatype-jsr310-2.11.0.jar;D:\softwares\LocalRepository\com\fasterxml\jackson\module\jackson-module-parameter-names\2.11.0\jackson-module-parameter-names-2.11.0.jar;D:\softwares\LocalRepository\org\springframework\boot\spring-boot-starter-tomcat\2.3.1.RELEASE\spring-boot-starter-tomcat-2.3.1.RELEASE.jar;D:\softwares\LocalRepository\org\apache\tomcat\embed\tomcat-embed-core\9.0.36\tomcat-embed-core-9.0.36.jar;D:\softwares\LocalRepository\org\glassfish\jakarta.el\3.0.3\jakarta.el-3.0.3.jar;D:\softwares\LocalRepository\org\apache\tomcat\embed\tomcat-embed-websocket\9.0.36\tomcat-embed-websocket-9.0.36.jar;D:\softwares\LocalRepository\org\springframework\spring-web\5.2.7.RELEASE\spring-web-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-webmvc\5.2.7.RELEASE\spring-webmvc-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-aop\5.2.7.RELEASE\spring-aop-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-context\5.2.7.RELEASE\spring-context-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-expression\5.2.7.RELEASE\spring-expression-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\com\alibaba\druid\1.2.9\druid-1.2.9.jar;D:\softwares\LocalRepository\org\springframework\spring-jdbc\5.3.20\spring-jdbc-5.3.20.jar;D:\softwares\LocalRepository\org\springframework\spring-beans\5.2.7.RELEASE\spring-beans-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-core\5.2.7.RELEASE\spring-core-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-jcl\5.2.7.RELEASE\spring-jcl-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\org\springframework\spring-tx\5.2.7.RELEASE\spring-tx-5.2.7.RELEASE.jar;D:\softwares\LocalRepository\mysql\mysql-connector-java\8.0.29\mysql-connector-java-8.0.29.jar;D:\softwares\LocalRepository\org\aspectj\aspectjrt\1.9.5\aspectjrt-1.9.5.jar;D:\softwares\LocalRepository\org\aspectj\aspectjweaver\1.9.21\aspectjweaver-1.9.21.jar;D:\softwares\LocalRepository\com\vdurmont\emoji-java\4.0.0\emoji-java-4.0.0.jar;D:\softwares\LocalRepository\org\json\json\20170516\json-20170516.jar;D:\softwares\LocalRepository\cn\hutool\hutool-all\5.8.25\hutool-all-5.8.25.jar;D:\softwares\LocalRepository\javax\annotation\javax.annotation-api\1.3.2\javax.annotation-api-1.3.2.jar;D:\softwares\LocalRepository\javax\inject\javax.inject\1\javax.inject-1.jar;D:\softwares\LocalRepository\io\reactivex\rxjava\1.3.8\rxjava-1.3.8.jar;D:\softwares\LocalRepository\com\jcraft\jsch\0.1.55\jsch-0.1.55.jar;D:\softwares\LocalRepository\org\ow2\asm\asm\9.6\asm-9.6.jar 
com.zcq.demo.test.Test
```








## String

在 Java 9 之前，Java 字符串使用的是 `char[]` 数组来存储字符数据，其中每个 `char` 占用 16 位（2 字节）。这意味着即使字符串中只包含 ASCII 字符（每个字符只需要 1 个字节），也会占用 2 倍于实际所需的内存。这种浪费在内存资源有限的情况下尤其明显。

```java
public static void main(String[] args) {
    String w = new String("😀"); //超过ascii，utf-8编码占用4个字节
    String a = new String("a");//ascii字符，utf-8编码占用1个字节
    String s = new String("a😀");
    System.out.println(w);
}
```

<img src=".\images\String01.png" alt="image-20241009145648963" style="zoom:50%;" />

### COMPACT_STRINGS 的实现

> **Latin-1**，也称为 ISO 8859-1，是一种字符编码标准，广泛用于早期的计算机系统和互联网协议中。它定义了一个单字节编码方案，能够表示 256 个不同的字符。这些字符包括：
>
> - **ASCII 字符**（0-127）：包括英文大小写字母、数字、标点符号以及一些控制字符。
> - **西欧语言特殊字符**（128-255）：包括德语、法语、西班牙语、意大利语等西欧语言中常用的字母和符号。如 ä, ö, ü, é, è, ç 等

从 Java 9 开始，Java 引入了 `COMPACT_STRINGS` 特性，默认启用。该特性允许 JVM 根据字符串内容自动选择使用 1 字节或 2 字节的编码来存储字符串：

- 如果字符串中的所有字符都在 Latin-1 编码范围内（即 0 到 255），则使用 1 字节的编码。即coder = LATIN1 = 0
- 如果字符串中有任何字符超出了 Latin-1 编码范围，则使用 2 字节的编码。即coder = UTF16 = 1

<img src=".\images\String02.png" alt="image-20241009145547146" style="zoom:50%;" />



## 类加载器加载文件原理

类加载器加载文件采用**双亲委派原理**，逐级向上委派给父类加载器加载文件，每个类加载器都负责不同的加载范围

```java
package java.lang;

public abstract class ClassLoader {
    
    public Enumeration<URL> getResources(String name) throws IOException {
        @SuppressWarnings("unchecked")
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[2];
        //委派给父类进行加载资源
        if (parent != null) {
            tmp[0] = parent.getResources(name);
        } else {
            //使用启动类加载器加载
            tmp[0] = getBootstrapResources(name);
        }
        //根据自己的负责范围加载资源
        tmp[1] = findResources(name);

        return new CompoundEnumeration<>(tmp);
    }
}
```

类加载器加载**类和资源**的负责范围如下

- 启动类加载器（Bootstrap ClassLoader）

  **启动类加载器**负责**加载 Java 的核心类库**，通常是 JDK 安装目录下的 `jre/lib` 目录，启动类加载器一般不会直接被 Java 代码获取到，因为在 Java 代码中调用 `getClassLoader()` 对于核心类库的类会返回 `null`

```java
	//负责范围是jdk下的核心类和资源的加载
	//因为获取不到启动类加载器 所以会产生null异常
		Enumeration<URL> resources3 = Test.class.getClassLoader().getParent().getParent().getResources("a.yml");
        while (resources3.hasMoreElements()) {
            URL url = resources3.nextElement();
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }
```

- 扩展类加载器（Extension ClassLoader）

  扩展类加载器负责加载 **JDK 扩展目录**下的类和资源，通常是 `jre/lib/ext` 目录。如果 `Test` 类是由扩展类加载器加载的，那么 `getResources("")` 查找的根路径就是 `jre/lib/ext` 目录及其子目录。

```java
//查找路径就是 jdk扩展目录jre/lib/ext 下去找相对于当前目录下的a.yml文件
//先委托给父类启动类加载器找a.yml 再自己找
Enumeration<URL> resources5 = Test.class.getClassLoader().getParent().getResources("a.yml");
while (resources5.hasMoreElements()) {
    URL url = resources5.nextElement();
    //转换成File对象
    File file = new File(url.getFile());
    File absoluteFile = file.getAbsoluteFile();
    System.out.println(absoluteFile);
}
```

- 应用程序类加载器（Application ClassLoader）

  应用程序类加载器负责**加载用户类路径（`classpath`）上的类和资源**。

  classpath 包括**当前项目的类和资源路径** + **依赖Jar包的类和资源路径**

在不同的开发环境下，根路径有所不同：

- 开发环境下(IDE)：当前项目的类和资源路径 （`target/classes`）+ 依赖Jar包的类和资源路径 （`D:\softwares\LocalRepository\kkk.jar/`）

  这里因为在开发环境中，并没有实际将依赖Jar包和当前项目文件打包，类加载器还能找到对应的依赖是因为IDEA在启动项目时在命令行加上了参数

  ```shell
  java -Dfile.encoding=UTF-8 -classpath D:\softwares\LocalRepository\kkk.jar;D:\softwares\LocalRepository\fff.jar;
  ```

- 打成Jar包下：当前项目的类和资源路径 (`xxx.jar/BOOT-INF/classes/`) + 依赖Jar包的类和资源路径 （`xxx.jar/BOOT-INF/lib/kkkk.jar/`）

```java
//查找路径就是所有的classpath路径下 找相对于每一条classpath下的a.yml文件
//先去委托给父类扩展类加载器再找a.yml + 先委托给启动类加载器去找 + 再自己找
Enumeration<URL> resources = Test.class.getClassLoader().getResources("com");
while (resources.hasMoreElements()) {
    URL url = resources.nextElement();
    //转换成File对象
    File file = new File(url.getFile());
    File absoluteFile = file.getAbsoluteFile();
    System.out.println(absoluteFile);
}


//结果如下 ：
//在扩展类加载器找到的
D:\doc\my\studymd\LearningNotes\file:\C:\Users\Administrator\.jdks\corretto-1.8.0_412\jre\lib\ext\jfxrt.jar!\com
//在应用类加载器找到的
//当前项目的类和资源路径下的com文件
D:\doc\my\studymd\LearningNotes\framework\spring\springstudycode\target\classes\com
//假设当前依赖两个jar包，那么每个jar包的路径都可以看作一个独立的查找路径，在每个jar包下查找对应的文件
//依赖Jar包的类和资源路径的com文件
D:\doc\my\studymd\LearningNotes\file:\D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-databind\2.11.0\jackson-databind-2.11.0.jar!\com
//依赖Jar包的类和资源路径的com文件
D:\doc\my\studymd\LearningNotes\file:\D:\softwares\LocalRepository\com\fasterxml\jackson\core\jackson-annotations\2.11.0\jackson-annotations-2.11.0.jar!\com
```

当前项目的类和资源路径 （`target/classes`）的目录结构 ： **顶级类包** + **开发时resources目录下的所有文件**

<img src=".\images\image-20250227140553290.png" alt="image-20250227140553290" style="zoom:50%;" />

依赖Jar包的类和资源路径 （`D:\softwares\LocalRepository\kkk.jar/`）的目录结构 ：和上面基本一致  **顶级类包** + **所有文件资源文件**

<img src=".\images\image-20250227140835971.png" alt="image-20250227140835971" style="zoom:50%;" />

## Properties

在JDK中，Properties支持读取yml和properties文件，能直接将属性转换成key-value形式

```java
public class PropertiesTest {
    public static void main(String[] args) throws Exception {

        URL resource = PropertiesTest.class.getClassLoader().getResource("myconfig2.properties");
        URL resource1 = PropertiesTest.class.getClassLoader().getResource("myconfig3.yml");

        Properties properties = new Properties();
        properties.load(resource.openStream());
        System.out.println(properties);

        String property = properties.getProperty("myconfig2.name");

        Properties properties2 = new Properties();
        properties2.load(resource1.openStream());
        System.out.println(properties2);
    }
}
```



## MessageDigest

`MessageDigest` 是 Java 中一个用于实现消息摘要算法的类，它位于 `java.security` 包下。消息摘要算法是一种将任意长度的数据转换为固定长度哈希值的算法，这些哈希值通常用于数据完整性验证、密码存储等场景。



## 原反补码

### 算数右移和除法

- 正数：右移 `n` 位和除以 `2^n` 是等效的。
- 负数：算术右移 `n` 位相当于将该数除以 `2^n` 并向负无穷取整，整数除法是向零取整，二者处理方式不同，结果也就不同。

```java
   		System.out.println(196658 >> 16); //3
	    System.out.println(196658 / (1 << 16)); //3
        System.out.println(196658 & ((1 << 16) - 1)); //50 取余数

        System.out.println(-196658 >> 16); //-4
        System.out.println(-196658 / (1 << 16)); //-3
        System.out.println(-196658 & ((1 << 16) - 1)); //65486
```



## 泛型

因为 **Java 的泛型是编译期泛型（伪泛型）**。

在 Java 5 引入泛型时，为了与旧版本 JVM 兼容，Java 设计成：

**泛型只在编译期有效，运行期不保留真实类型信息。**

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
//运行时无法区分：
a.getClass() == b.getClass();  // true
//运行期只有：
ArrayList.class
```

### 类型擦除带来什么问题？

```java
List<User>
Map<String, Order>
Response<List<User>>
//运行时都只剩下：
List
Map
Response
```

因为类型擦除机制，运行时期会将类的泛型擦除，**泛型只在编译期有效**，所以能获取到真实泛型的地方如下

可以理解为泛型信息被记录在类上，获取泛型需要通过泛型的拥有类

- 字段 (`Field`)
- 方法参数 (`MethodParameter`)
- 方法返回值 (`Method`)
- 构造器参数 (`Constructor`)
- 类 (`Class`)

### Java 的 Type 体系（关键）

| Type 子接口           | 含义         | 示例                               |
| --------------------- | ------------ | ---------------------------------- |
| **Class**             | 非泛型类型   | `User.class`                       |
| **ParameterizedType** | 有参数的泛型 | `List<User>`、`Map<String, Order>` |
| **GenericArrayType**  | 泛型数组     | `T[]`                              |
| **TypeVariable**      | 类型变量     | `T`                                |
| **WildcardType**      | 通配符类型   | `? extends Number`                 |

通过子接口来获取泛型的类型

```java
//List<User>
Type: ParameterizedType
rawType: List
typeArguments: [User]
```

```java
class User {}
class Order {}

class Example<T> {
    public User user;                      // Class
    public List<User> userList;            // ParameterizedType
    public Map<String, Order> orderMap;    // ParameterizedType
    public T[] genericArray;               // GenericArrayType
    public T typeVariable;                 // TypeVariable
    public List<? extends User> wildcard;  // WildcardType
}

public class TypeDemo {
    Example<Integer> example = new Example<>();

    public static void main(String[] args) throws NoSuchFieldException {
        Class<?> clazz = TypeDemo.class;

        // 1. 获取 example 字段
        Field exampleField = clazz.getDeclaredField("example");
        Type exampleType = exampleField.getGenericType();
        System.out.println("example field type: " + exampleType);

        // 判断 exampleType 是否是 ParameterizedType
        if (exampleType instanceof ParameterizedType pt) {
            Type actualTypeArg = pt.getActualTypeArguments()[0]; // T 对应的实际类型 Integer
            System.out.println("T 的实际类型: " + actualTypeArg);

            // 获取 Example<Integer> 内部字段
            Class<?> exampleClass = Example.class; // 泛型类
            for (Field f : exampleClass.getFields()) {
                Type fieldType = f.getGenericType();

                // 如果字段是 TypeVariable，需要替换成实际类型
                if (fieldType instanceof TypeVariable<?>) {
                    System.out.println(f.getName() + " : " + actualTypeArg);
                } else if (fieldType instanceof GenericArrayType gat) {
                    Type componentType = gat.getGenericComponentType();
                    if (componentType instanceof TypeVariable<?>) {
                        System.out.println(f.getName() + " : " + actualTypeArg + "[]");
                    } else {
                        System.out.println(f.getName() + " : " + fieldType);
                    }
                } else if (fieldType instanceof ParameterizedType p) {
                    System.out.print(f.getName() + " : " + p.getRawType() + "<");
                    Type[] argsArr = p.getActualTypeArguments();
                    for (int i = 0; i < argsArr.length; i++) {
                        if (argsArr[i] instanceof TypeVariable<?>) {
                            System.out.print(actualTypeArg.getTypeName());
                        } else if (argsArr[i] instanceof WildcardType wt) {
                            System.out.print("?");
                            Type[] upper = wt.getUpperBounds();
                            if (upper.length > 0) {
                                System.out.print(" extends " + upper[0].getTypeName());
                            }
                        } else {
                            System.out.print(argsArr[i].getTypeName());
                        }
                        if (i < argsArr.length - 1) System.out.print(", ");
                    }
                    System.out.println(">");
                } else {
                    System.out.println(f.getName() + " : " + fieldType.getTypeName());
                }
            }
        }
    }
}
```





### ResolvableType

因为类型擦除机制，运行时期会将类的泛型擦除，**泛型只在编译期有效**，所以能获取到真实泛型的地方如下

可以理解为泛型信息被记录在类上，获取泛型需要通过泛型的拥有类

- 字段 (`Field`)
- 方法参数 (`MethodParameter`)
- 方法返回值 (`Method`)
- 构造器参数 (`Constructor`)
- 类 (`Class`)

```java
public class Test {

    // 泛型类示例
    static class GenericClass<T> {
        List<String> stringList;            // ParameterizedType
        T[] genericArray;                   // 泛型数组
        T genericField;                     // TypeVariable
        List<? extends Number> wildcardList; // WildcardType
    }
    
    //定义该字段时必须指定好泛型 后续才能获取到
    private static GenericClass<Integer> integerGenericClass;

    public static void main(String[] args) throws NoSuchFieldException {
        
        //通过integerGenericClass字段的拥有者Test来获取字段的泛型 可以获取到
        ResolvableType t = ResolvableType.forField(Test.class.getDeclaredField("integerGenericClass"));
        System.out.println(t.getGeneric(0).resolve()); // Integer 可以获取到泛型
        
	    //获取不到Integer 运行期间List<Integer>会被擦除为List 可以理解为List<Integer> list这个泛型信息没有被记录在任何类上 
        List<Integer> list = new ArrayList<>();
        ResolvableType type = ResolvableType.forInstance(list);
        System.out.println(type);
    }
}
```
