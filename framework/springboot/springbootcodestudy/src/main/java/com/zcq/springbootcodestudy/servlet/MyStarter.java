package com.zcq.springbootcodestudy.servlet;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@AutoConfiguration
@ConditionalOnProperty(value = "zcq.enable")
@ConditionalOnClass(value = Object.class)
public class MyStarter {

    static {
        System.out.println("MyStarter...............");
    }
}
