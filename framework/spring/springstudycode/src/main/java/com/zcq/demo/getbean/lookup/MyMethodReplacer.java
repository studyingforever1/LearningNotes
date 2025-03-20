package com.zcq.demo.getbean.lookup;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.MethodOverride;
import org.springframework.beans.factory.support.MethodReplacer;
import org.springframework.beans.factory.support.ReplaceOverride;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class MyMethodReplacer implements MethodReplacer {
    @Override
    public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
        System.out.println("替换增强");
        return new Object();
    }
}

@Component
class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition) beanFactory.getBeanDefinition("myMethod");
        MethodOverride methodOverride = new ReplaceOverride("myMethod", "myMethodReplacer");
        beanDefinition.getMethodOverrides().addOverride(methodOverride);
    }
}