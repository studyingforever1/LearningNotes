package com.zcq.thread;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;

public class ThreadLocalTest {

    public static void main(String[] args) throws Exception{

        testFastThreadLocal();
    }
    public static void testFastThreadLocal() throws Exception {

        DefaultThreadFactory defaultThreadFactory = new DefaultThreadFactory("test");
        FastThreadLocalThread fastThreadLocalThread = (FastThreadLocalThread) defaultThreadFactory.newThread(() -> {

            FastThreadLocal fastThreadLocal = new FastThreadLocal<>();
            fastThreadLocal.set("abc");
            System.out.println(fastThreadLocal.get());

            fastThreadLocal.remove();

            FastThreadLocal.removeAll();



            while (true){

            }

        });
        fastThreadLocalThread.start();
        fastThreadLocalThread.join();
    }
    public static void testThreadLocal() {
        Thread thread = Thread.currentThread();
        MyThreadLocal myThreadLocal1 = new MyThreadLocal();
        myThreadLocal1.set("abc");
        myThreadLocal1.get();

        myThreadLocal1 = null;

        MyThreadLocal myThreadLocal2 = new MyThreadLocal();
        myThreadLocal2.set("ffffffffffff");
        myThreadLocal2.get();
        myThreadLocal2.remove();

        System.out.println("thread:" + thread.getName());
    }


    static class MyThreadLocal extends ThreadLocal<String> {

    }
}
