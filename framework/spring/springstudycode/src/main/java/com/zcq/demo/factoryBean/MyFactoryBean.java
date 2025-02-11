package com.zcq.demo.factoryBean;

import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.stereotype.Component;

@Component
public class MyFactoryBean implements SmartFactoryBean<String> {

    @Override
    public String getObject() throws Exception {
        return new String("xxxxxxxxx");
    }

    @Override
    public Class<?> getObjectType() {
        return String.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
