package com.zcq;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

public class Main {

    private static Unsafe unsafe = null;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        unsafeTest();
    }


    private static void unsafeTest() throws Exception {
//        unsafe.park(true, 1000);
//        unsafe.unpark(Thread.currentThread());

        User user = new User(0);

        CountDownLatch countDownLatch = new CountDownLatch(2);


        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                synchronized (Main.class) {
                    for (int j = 0; j < 1000; j++) {
                        int age = unsafe.getIntVolatile(user, 0L);
                        System.out.println("age:" + age);
//                        unsafe.putOrderedInt(user, 0L, age + 1);
                        System.out.println(unsafe.compareAndSwapInt(user, 0L, age, age + 1));
                        System.out.println("子线程读的:" + user.age);
                    }
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();

        //这里读的是主内存 此时这个缓存行已经被其他线程置为脏的 读的时候就需要利用MESI的规则刷新
        System.out.println(unsafe.getInt(user, 0L));
        System.out.println(unsafe.getIntVolatile(user, 0L));
        //这里读的是主线程的缓存
        System.out.println("主线程读的:" + user.age);

//        System.out.println(unsafe.compareAndSwapInt(user, 0L, 0, 1));

//        System.out.println("主线程读的:" + user.age);

    }

    static class User {
        private int age;

        public User(int age) {
            this.age = age;
        }
    }
}