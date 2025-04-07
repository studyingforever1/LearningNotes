package com.zcq.demo.aop.annotation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class MyComponet {

    @Value("1")
    public void test() {
        System.out.println("test");
    }
    public void test2() {
        System.out.println("test");
    }

}
