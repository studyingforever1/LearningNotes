package com.zcq.demo.getbean.lookup;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MyMethodOverridesTest {
    public static void main(String[] args) {

        AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext("com.zcq.demo.getbean.lookup");

        MyLookup myLookup = (MyLookup) applicationContext.getBean("myLookup");
        System.out.println(myLookup.getMyLookupObject());

        MyMethod myMethod = (MyMethod) applicationContext.getBean("myMethod");
        System.out.println(myMethod.myMethod());

    }
}
