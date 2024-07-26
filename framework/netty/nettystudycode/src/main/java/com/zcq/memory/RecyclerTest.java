package com.zcq.memory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.util.concurrent.atomic.AtomicReference;

public class RecyclerTest {


    public static void main(String[] args) throws InterruptedException {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator();
        AtomicReference<ByteBuf> childThreadBuf = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            while (true) {
                synchronized (allocator) {
                    if (Thread.interrupted()) {
                        System.out.println("子线程打断");
                        break;
                    }
                    childThreadBuf.set(allocator.buffer(1));
                    System.out.println("子线程完成");
                    allocator.notify();
                    try {
                        allocator.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    allocator.buffer(1);
                }
            }
        });

        thread.start();

        synchronized (allocator) {
            allocator.wait();

            System.out.println("主线程开始");
            childThreadBuf.get().release();
            System.out.println("主线程完成，打断子线程");
            allocator.notifyAll();
        }
    }

}
