package com.zcq.demo.myaware;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class MyAwareBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MyAware){
            ((MyAware) bean).setMyAware(new Object());
        }
        return bean;
    }
}
