package com.zcq.demo.tx.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class LazyBean {

    public LazyBean() {
        System.out.println("初始化了");
    }


    int i = 0;

    public void test() {
        System.out.println("lazy test" + i++);
    }

}
