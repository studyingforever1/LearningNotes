package com.zcq.thread;

import io.netty.util.concurrent.FastThreadLocal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class ThreadLocalTest {

    public static void main(String[] args) {


        for (int i = 0; i < 1000_0000; i++) {
            MyThreadLocal myThreadLocal1 = new MyThreadLocal();
            myThreadLocal1.set(i + "");
        }

        Thread thread = Thread.currentThread();

        FastThreadLocal.removeAll();


        System.out.println();
    }



    static class MyThreadLocal extends ThreadLocal<String>{

    }
}
