package com.zcq.threadlocal;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.TtlRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadLocalTest {

    static ThreadLocal<TestUser> inheritableThreadLocal = new InheritableThreadLocal<>();

    static ExecutorService executorService = Executors.newFixedThreadPool(1);

    static TransmittableThreadLocal<TestUser> transmittableThreadLocal = new TransmittableThreadLocal<>();

    public static void main(String[] args) throws Exception {

        //inheritableThreadLocal的值会传递给子线程 如果是引用类型的值 子线程和父线程操作同一个对象的时候会产生线程安全问题
        //根本原因是inheritableThreadLocal复制value的时候是浅拷贝

        TestUser testUser = new TestUser();
        testUser.setAge(18);
        inheritableThreadLocal.set(testUser);
        executorService.execute(() -> System.out.println(inheritableThreadLocal.get()));
        Thread.sleep(20);
        testUser.setAge(20);
        Thread.sleep(20);
        executorService.execute(() -> System.out.println(inheritableThreadLocal.get()));

        TestUser testUser2 = new TestUser();
        testUser2.setAge(30);
        transmittableThreadLocal.set(testUser2);
        TtlRunnable ttlRunnable = TtlRunnable.get(() -> System.out.println(transmittableThreadLocal.get()));
        executorService.execute(ttlRunnable);

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
