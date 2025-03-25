package com.zcq.demo.getbean.autowired;


import org.springframework.stereotype.Component;

@Component
public class MyResourceBean {
    private String username = "zhangsan";

    public String getUsername() {
        return username;
    }
}