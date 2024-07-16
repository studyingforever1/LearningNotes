package com.zcq;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;

public class Main {
    public static void main(String[] args) {

//        ServerSocketChannel serverSocketChannel = new NioServerSocketChannel();
//        serverSocketChannel.bind(new InetSocketAddress(8080));

        System.out.println(Math.abs(Integer.MAX_VALUE + 1));

        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread(
                () -> {
                    FastThreadLocal<String> fastThreadLocal = new FastThreadLocal<>();
                    fastThreadLocal.set("hello1");
                    fastThreadLocal.set("hello2");
                    fastThreadLocal.set("hello3");
                    fastThreadLocal.set("hello4");
                    fastThreadLocal.set("hello5");
                    fastThreadLocal.set("hello6");
                    fastThreadLocal.set("hello7");
                    System.out.println(Thread.currentThread().getName());
                    System.out.println(fastThreadLocal.get());
                });

        fastThreadLocalThread.start();






    }
}