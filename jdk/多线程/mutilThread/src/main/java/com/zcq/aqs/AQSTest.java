package com.zcq.aqs;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.LockSupport;

public class AQSTest {

    public static void main(String[] args) {
        MyLock myLock = new MyLock();

        new Thread(()->{
            myLock.lock();
            System.out.println("线程" + Thread.currentThread().getName() + "获取锁");
            try {
                Thread.sleep(1000);
            }catch (Exception e){

            }finally {
                myLock.unlock();
            }
        }).start();

        new Thread(()->{
            myLock.lock();
            System.out.println("线程" + Thread.currentThread().getName() + "获取锁");
            try {
                Thread.sleep(1000);
            }catch (Exception e){

            }finally {
                myLock.unlock();
            }
        }).start();


        LockSupport.park();
    }
}

class MyLock extends AbstractQueuedSynchronizer {

    public void lock() {
        acquire(1);
    }

    public void unlock() {
        release(1);
    }


    @Override
    protected boolean tryAcquire(int arg) {
        if (getState() == 0) {
            if (compareAndSetState(getState(), arg)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
        } else {
            if (getExclusiveOwnerThread().equals(Thread.currentThread())) {
                setState(getState() + arg);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean tryRelease(int arg) {
        if (getExclusiveOwnerThread().equals(Thread.currentThread())) {
            int state = getState() - arg;
            if (compareAndSetState(getState(), state)) {
                if (state == 0) {
                    setExclusiveOwnerThread(null);
                }
                return true;
            }
        }
        return false;
    }
}
