package com.zcq.demo.bfpp;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyApplicationContext extends ClassPathXmlApplicationContext {


    public MyApplicationContext(String configLocations) {
        super(configLocations);
    }

    @Override
    protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
        addBeanFactoryPostProcessor(new MyBeanDefinitionRegistryPostProcessor());
        super.customizeBeanFactory(beanFactory);
    }
}
