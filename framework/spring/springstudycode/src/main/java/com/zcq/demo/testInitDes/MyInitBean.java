package com.zcq.demo.testInitDes;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class MyInitBean {

    public MyInitBean() {
        System.out.println("MyInitBean");
    }

    public static  void aVoid(){

    }


    @PostConstruct
    public void init() {
        System.out.println("init");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("destroy");
    }


}
