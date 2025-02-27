package com.zcq.demo.per;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyApplicationContext extends ClassPathXmlApplicationContext {

    public MyApplicationContext(String configLocations) {
        super(configLocations);
    }

    @Override
    protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
        beanFactory.addPropertyEditorRegistrar(new MyPropertyEditorRegistrar());
        super.customizeBeanFactory(beanFactory);
    }
}
