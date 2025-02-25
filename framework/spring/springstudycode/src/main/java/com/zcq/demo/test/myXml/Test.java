package com.zcq.demo.test.myXml;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

    public static void main(String[] args) {
        System.getProperties().setProperty("zcq","myXml");
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("${zcq}.xml");
        User bean = ac.getBean(User.class);
        System.out.println(bean);
    }
}
