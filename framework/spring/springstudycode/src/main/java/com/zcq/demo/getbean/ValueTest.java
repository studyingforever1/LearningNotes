package com.zcq.demo.getbean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ValueTest {

    @Value("#{1+1}")
    private int value;


    @Value("${user.name}")
    private String name;

}
