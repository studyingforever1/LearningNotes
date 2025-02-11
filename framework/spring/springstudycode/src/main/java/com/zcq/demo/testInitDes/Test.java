package com.zcq.demo.testInitDes;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext7.xml");
//        ReflectionUtils.doWithLocalMethods(MyInitBean.class, System.out::println);
        applicationContext.close();
    }
}
