package com.zcq.threadlocal;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.TtlRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadLocalTest {

    static ThreadLocal<TestUser> inheritableThreadLocal = new InheritableThreadLocal<>();
    static ThreadLocal<TestUser> threadLocal = new ThreadLocal<>();
    static ExecutorService executorService = Executors.newFixedThreadPool(1);

    static TransmittableThreadLocal<TestUser> transmittableThreadLocal = new TransmittableThreadLocal<>();

    public static void main(String[] args) throws Exception {
//        testInheritableThreadLocal();
        testTransmittableThreadLocal();
    }

    private static void testInheritableThreadLocal() throws Exception {
        //inheritableThreadLocal的值会传递给子线程 如果是引用类型的值 子线程和父线程操作同一个对象的时候会产生线程安全问题
        //根本原因是inheritableThreadLocal复制value的时候是浅拷贝
        TestUser testUser = new TestUser();
        testUser.setAge(18);
        inheritableThreadLocal.set(testUser);
        //这里能传递是因为线程池第一次会创建线程 然后会继承父线程的ThreadLocal
        executorService.execute(() -> System.out.println(inheritableThreadLocal.get()));
        Thread.sleep(20);
        testUser.setAge(20);
        Thread.sleep(20);
        executorService.execute(() -> System.out.println(inheritableThreadLocal.get()));
        executorService.shutdown();
    }

    private static void testTransmittableThreadLocal() {

        //先提交一个任务让线程池创建好线程
        executorService.execute(() -> System.out.println("---"));

        //1.使用transmittableThreadLocal进行传递

        TestUser testUser2 = new TestUser();
        testUser2.setAge(30);
        transmittableThreadLocal.set(testUser2);
        //通过TtlRunnable包装一下Runnable 就可以在已经存在的线程池中拿到要传递的transmittableThreadLocal
        TtlRunnable ttlRunnable = TtlRunnable.get(() -> System.out.println(transmittableThreadLocal.get()));
        executorService.execute(ttlRunnable);

        //2.使用threadLocal进行传递

        TestUser testUser3 = new TestUser();
        testUser3.setAge(35);
        threadLocal.set(testUser3);
        //需要将普通threadLocal预先注册到Transmitter中
        TransmittableThreadLocal.Transmitter.registerThreadLocalWithShadowCopier(threadLocal);
        TtlRunnable ttlRunnable2 = TtlRunnable.get(() -> System.out.println(threadLocal.get()));
        executorService.execute(ttlRunnable2);

        executorService.shutdown();
    }
}

class TestUser {
    private Integer age;

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "TestUser{" +
                "age=" + age +
                '}';
    }
}
