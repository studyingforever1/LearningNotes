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





