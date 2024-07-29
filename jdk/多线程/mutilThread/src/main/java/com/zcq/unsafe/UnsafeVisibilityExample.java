package com.zcq.unsafe;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

class User {
    private int sharedVar = 0;
}

public class UnsafeVisibilityExample {

    private static final Unsafe unsafe;
    private static final long sharedVarOffset;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            sharedVarOffset = unsafe.objectFieldOffset(
                    User.class.getDeclaredField("sharedVar")
            );
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        User user = new User();
        Thread writerThread = new Thread(() -> {
            try {
                Thread.sleep(1000); // 让写线程晚一些开始
                unsafe.putInt(user, sharedVarOffset, 1);
                System.out.println("Writer thread has updated the value to 1");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread readerThread = new Thread(() -> {
            while (unsafe.getInt(user, sharedVarOffset) == 0) {
                // 空循环，等待sharedVar被修改
            }
            System.out.println("Reader thread sees: " + unsafe.getInt(user, sharedVarOffset));
        });

        readerThread.start();
        writerThread.start();

        // 等待两个线程结束
        readerThread.join();
        writerThread.join();
    }
}
