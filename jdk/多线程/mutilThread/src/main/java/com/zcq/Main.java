package com.zcq;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

public class Main {

    private static Unsafe unsafe = null;
    private static long ageOffset;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            assert unsafe != null;
            ageOffset = unsafe.objectFieldOffset(User.class.getDeclaredField("age"));
        } catch (Exception ignored) {
        }
    }

    static class User {
        private int age;

        public User(int age) {
            this.age = age;
        }
    }

    public static void main(String[] args) throws Exception {
        unsafeTest();
//        testCas();
    }


    private static void unsafeTest() throws Exception {
//        unsafe.park(true, 1000);
//        unsafe.unpark(Thread.currentThread());

        User user = new User(0);


        int threadNum = 2;

        CountDownLatch countDownLatch = new CountDownLatch(threadNum);

        //找了半天还是没找到getInt和getIntVolatile的区别
        //找了半天还是没找到setInt和setIntVolatile的区别

        new Thread(() -> {
            Thread.currentThread().setName("线程1");
            int age = unsafe.getInt(user, ageOffset);
            System.out.println(Thread.currentThread().getName() + ":" + age);
            if (age == 0) {
                unsafe.putInt(user, ageOffset, 18);
                System.out.println(Thread.currentThread().getName() + ":" + unsafe.getInt(user, ageOffset) + "设置完了");
            }
            countDownLatch.countDown();
        }).start();

        new Thread(() -> {
            Thread.currentThread().setName("线程2");
            int age = unsafe.getInt(user, ageOffset);
            System.out.println(Thread.currentThread().getName() + ":" + age);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
             age = unsafe.getInt(user, ageOffset);
            System.out.println(Thread.currentThread().getName() + ":" + age);
            if (age == 0) {
                unsafe.putInt(user, ageOffset, 18);
                System.out.println(Thread.currentThread().getName() + ":" + unsafe.getInt(user, ageOffset) + "设置完了");
            }
            countDownLatch.countDown();
        }).start();






//        for (int i = 0; i < threadNum; i++) {
//            new Thread(() -> {
//
//                long start = System.nanoTime();
//
//                for (int j = 0; j < 1000; j++) {
////                    int age = unsafe.getInt(user, ageOffset);
//                    int age = unsafe.getInt(user, ageOffset);
//                    System.out.println("age:" + age);
////                    unsafe.putOrderedInt(user, ageOffset, age + 1);
////                    unsafe.putInt(user, ageOffset, age + 1);
//                    unsafe.putInt(user, ageOffset, age + 1);
//                    System.out.println("子线程读的:" + user.age);
//                }
//                System.out.println("花费时间:" + (System.nanoTime() - start));
//                countDownLatch.countDown();
//
//                try {
//                    Thread.sleep(11111111);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }).start();
//        }

        countDownLatch.await();


//        System.out.println("主线程读的:" + user.age);
//
//        System.out.println(unsafe.getInt(user, ageOffset));
//        System.out.println(unsafe.getIntVolatile(user, ageOffset));
//        System.out.println("主线程读的:" + user.age);
//
    }


    public static void testCas() throws Exception {

        User user = new User(0);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                int age;
                do {
                    age = user.age;
                    System.out.println(Thread.currentThread().getName() + ":" + age);
                } while (!unsafe.compareAndSwapInt(user, ageOffset, age, age + 1));
            }).start();
        }

        Thread.sleep(1000);
        System.out.println(user.age);
    }


}