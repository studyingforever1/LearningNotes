package com.zcq.timewheel;

import io.netty.util.HashedWheelTimer;

import java.util.concurrent.TimeUnit;

public class TimeWheelTest {
    public static void main(String[] args) {

        HashedWheelTimer timer = new HashedWheelTimer();
        timer.newTimeout((timeout)-> System.out.println("hello"), 1, TimeUnit.SECONDS);
        timer.newTimeout((timeout)-> System.out.println("hello1"), 10, TimeUnit.SECONDS);
        timer.newTimeout((timeout)-> System.out.println("hello2"), 20, TimeUnit.SECONDS);
        timer.newTimeout((timeout)-> System.out.println("hello3"), 30, TimeUnit.SECONDS);

    }
}
