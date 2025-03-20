package com.zcq.demo.getbean.createbean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class CreateBeanTest {
    public static void main(String[] args) {

        AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext("com.zcq.demo.getbean.createbean");

        applicationContext.close();
    }
}
