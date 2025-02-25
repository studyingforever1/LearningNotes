package com.zcq.demo.test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

    public static void main(String[] args) {
        System.getProperties().setProperty("zcq","tx");
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("/*.xml");
        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
    }
}
