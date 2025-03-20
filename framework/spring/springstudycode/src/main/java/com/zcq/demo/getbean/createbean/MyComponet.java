package com.zcq.demo.getbean.createbean;

import org.springframework.stereotype.Component;

import javax.annotation.ManagedBean;
import javax.inject.Named;

@Component
@ManagedBean
public class MyComponet {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public MyComponet() {
    }

    public MyComponet(String value) {
        this.value = value;
    }
}
