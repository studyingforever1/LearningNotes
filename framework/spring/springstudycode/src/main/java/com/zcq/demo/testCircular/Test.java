package com.zcq.demo.testCircular;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext8.xml");
//        ReflectionUtils.doWithLocalMethods(MyInitBean.class, System.out::println);
        applicationContext.close();
    }
}
