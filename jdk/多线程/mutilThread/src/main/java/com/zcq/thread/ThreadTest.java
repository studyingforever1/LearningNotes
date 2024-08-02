package com.zcq.thread;

import java.util.concurrent.locks.LockSupport;

public class ThreadTest {
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            LockSupport.parkNanos(1000000000L);
            System.out.println("hello1");
        });
//        t1.setDaemon(true);
        t1.setPriority(Thread.MIN_PRIORITY);
        Thread t2 = new Thread(() -> {
            LockSupport.parkNanos(1000000000L);
            System.out.println("hello2");
        });
        t2.setPriority(Thread.MAX_PRIORITY);

        t1.start();
        t2.start();

    }
}
