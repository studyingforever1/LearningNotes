package com.zcq.demo.test;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("myFactoryBean")
public class MyFactoryBean implements FactoryBean<Test> {

    @Resource(name = "myFactoryBean")
    private Test test;

    @Resource(name = "&myFactoryBean")
    private MyFactoryBean myFactoryBean;


    @Override
    public Test getObject() throws Exception {
        return new Test();
    }

    @Override
    public Class<?> getObjectType() {
        return Test.class;
    }
}
