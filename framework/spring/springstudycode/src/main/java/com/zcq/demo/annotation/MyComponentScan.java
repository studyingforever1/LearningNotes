package com.zcq.demo.annotation;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ComponentScan("com.zcq.demo")
public class MyComponentScan {

    @ComponentScan("com.zcq.demo")
    @Configuration
    @Order(90)
    class InnerClass{

    }

}
