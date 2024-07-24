package com.zcq.thread;

public class ThreadLocalTest {

    public static void main(String[] args) {

    }
    public static void testFastThreadLocal() {

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
