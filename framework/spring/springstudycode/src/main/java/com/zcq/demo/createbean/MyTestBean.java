package com.zcq.demo.createbean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MyTestBean {

    private String username;
    private Integer age;

    public MyTestBean() {
    }

    public MyTestBean(String username) {
        this.username = username;
    }

    @Autowired
    public MyTestBean(@Value("${username}") String username, @Value("1") Integer age) {
        this.username = username;
        this.age = age;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
