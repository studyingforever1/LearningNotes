package com.zcq.demo.testWeight;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.beans.ConstructorProperties;

@Component
public class MyWeight {

    @Autowired(required = false)
    public MyWeight(UserDao userDao){
        System.out.println("userDao");
    }

    @Autowired(required = false)
    @ConstructorProperties({})
    public MyWeight(UserDaoImpl userDao){
        System.out.println("UserDaoImpl");
    }
}
