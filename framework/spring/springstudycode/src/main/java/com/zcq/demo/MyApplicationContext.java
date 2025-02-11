package com.zcq.demo;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyApplicationContext extends ClassPathXmlApplicationContext {

    public MyApplicationContext(String... configLocations) {
        super(configLocations);
    }

    @Override
    protected void initPropertySources() {
        System.out.println("扩展方法");
//        getEnvironment().setRequiredProperties("abvccssa");
//        setAllowBeanDefinitionOverriding(true);
//        setAllowCircularReferences(true);
        super.initPropertySources();
//        super.addBeanFactoryPostProcessor();
        setAllowCircularReferences(true);

    }

    @Override
    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    protected void registerListeners() {
        super.registerListeners();
        ApplicationEventMulticaster applicationEventMulticaster = getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
        applicationEventMulticaster.multicastEvent(new ApplicationEvent(this) {
            @Override
            public Object getSource() {
                return super.getSource();
            }
        });
    }

    @Override
    public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
        super.addBeanFactoryPostProcessor(postProcessor);
    }
}
