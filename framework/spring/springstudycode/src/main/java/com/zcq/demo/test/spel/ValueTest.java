package com.zcq.demo.test.spel;

import com.zcq.demo.test.myXml.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ValueTest {

    @Value("#{systemProperties['os.name']}")
    private String name;

    @Value("#{T(java.lang.Math).random() * 100.0}")
    private double randomNumber;

    @Value("#{new java.lang.String('Hello World').toUpperCase()}")
    private String message;

    @Value("#{new java.util.Date()}")
    private String date;

    @Value("#{new com.zcq.demo.test.myXml.User('1','zcq','zcq@163.com','123456')}")
    private User user;

    @Value("${user.name}")
    private String userName;

}
