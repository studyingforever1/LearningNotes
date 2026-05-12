package com.zcq;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

public class CasVisibilityDemo {

    // 场景一：无 volatile，CAS 写 + 普通读
    static int plainValue = 0;

    // 场景二：有 volatile，CAS 写 + 普通读（AtomicInteger 的真实做法）
    static volatile int volatileValue = 0;

    static final Unsafe UNSAFE;
    static final long PLAIN_OFFSET;
    static final long VOLATILE_OFFSET;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            PLAIN_OFFSET    = UNSAFE.staticFieldOffset(
                    CasVisibilityDemo.class.getDeclaredField("plainValue"));
            VOLATILE_OFFSET = UNSAFE.staticFieldOffset(
                    CasVisibilityDemo.class.getDeclaredField("volatileValue"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        testPlain();
        testVolatile();
    }

    // -------------------------------------------------------
    // 场景一：无 volatile
    // 现象：B 线程在 JIT 充分优化后可能永远读不到 A 的写，死循环
    // -------------------------------------------------------
    static void testPlain() throws InterruptedException {
        plainValue = 0;

        // 线程 B：普通读，自旋等待 plainValue 变为 1
        Thread b = new Thread(() -> {
            long start = System.currentTimeMillis();
            // JIT 热身后，plainValue 可能被提升到寄存器
            // 一旦提升，即使 A 的 CAS 成功，B 永远看到 0
            while (plainValue != 1) {
                if (System.currentTimeMillis() - start > 3000) {
                    System.out.println("[plain]  B 等待超时，始终读到旧值 0（JIT 寄存器缓存）");
                    return;
                }
            }
            System.out.println("[plain]  B 读到最新值 1（JIT 尚未优化或恰好去缓存读）");
        }, "Thread-B-plain");

        // 线程 A：延迟 1s 后 CAS 更新
        Thread a = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            boolean ok = UNSAFE.compareAndSwapInt(CasVisibilityDemo.class, PLAIN_OFFSET, 0, 1);
            System.out.println("[plain]  A CAS " + (ok ? "成功" : "失败") + "，plainValue=" + plainValue);
        }, "Thread-A-plain");

        b.start();
        a.start();
        b.join();
        a.join();
    }

    // -------------------------------------------------------
    // 场景二：有 volatile
    // 现象：B 每次都从缓存读，必然在 A CAS 后看到最新值，正常退出
    // -------------------------------------------------------
    static void testVolatile() throws InterruptedException {
        volatileValue = 0;

        Thread b = new Thread(() -> {
            long start = System.currentTimeMillis();
            // volatile 禁止 JIT 寄存器缓存，每次都走缓存
            while (volatileValue != 1) {
                if (System.currentTimeMillis() - start > 3000) {
                    System.out.println("[volatile] B 等待超时（不应出现）");
                    return;
                }
            }
            System.out.println("[volatile] B 读到最新值 1 ✅");
        }, "Thread-B-volatile");

        Thread a = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            boolean ok = UNSAFE.compareAndSwapInt(CasVisibilityDemo.class, VOLATILE_OFFSET, 0, 1);
            System.out.println("[volatile] A CAS " + (ok ? "成功" : "失败") + "，volatileValue=" + volatileValue);
        }, "Thread-A-volatile");

        b.start();
        a.start();
        b.join();
        a.join();
    }
}
