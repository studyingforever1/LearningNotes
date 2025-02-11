package com.zcq.demo.testWeight;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext6.xml");
        Object myWeight = applicationContext.getBean("myWeight", new UserDaoImpl());
    }
}
