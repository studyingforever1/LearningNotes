package com.zcq.threadpool;

import java.util.concurrent.*;

public class ThreadPoolTest {
    final static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(2, 4, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(300));
    final static ForkJoinPool forkJoinPool = new ForkJoinPool();

    public static void main(String[] args) throws Exception {
        /**
         * ASCII字符 'A' 的 Unicode 编码为 0x41，在UTF-8中仅占用1个字节。
         * 欧元符号 € 的 Unicode 编码为 0x20AC，在UTF-8中占用2个字节。
         * 中文字符 中 的 Unicode 编码为 0x4E2D，在UTF-8中占用3个字节。
         * 补充平面字符 𐀀 的 Unicode 编码为 0x10400，在UTF-8中占用4个字节。
         */
        System.out.println(Integer.toBinaryString('A'));
        System.out.println(Integer.toBinaryString('€'));
        System.out.println(Integer.toBinaryString('中'));
//        System.out.println(Integer.toBinaryString("🤔"));

        System.out.println(Long.MAX_VALUE);
        System.out.println("9223372036854775807".length());
        System.out.println("-9223372036854775807".length());
    }

    public static void testThreadPoolExecutor() throws Exception {
        FutureTask<String> futureTask = new FutureTask<>(() -> {
            System.out.println(Thread.currentThread().getName());
            return "ok";
        });
        threadPoolExecutor.submit(futureTask);
        Thread.sleep(1000);
        if (futureTask.isDone()) {
            System.out.println(futureTask.get());
        } else {
            futureTask.cancel(true);
        }
    }

    public static void testCompletableFuture() {

        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
        }));

        CompletableFuture<Object> objectCompletableFuture = CompletableFuture.anyOf(CompletableFuture.runAsync(() -> {
                    System.out.println(Thread.currentThread().getName());
                }, threadPoolExecutor),
                CompletableFuture.runAsync(() -> {
                    System.out.println(Thread.currentThread().getName());
                })).whenCompleteAsync((r, e) -> {
            System.out.println(Thread.currentThread().getName());
            System.out.println(r);
        });

        objectCompletableFuture.join();
    }
}
