package com.zcq.demo.test;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Aware , EnvironmentAware, ApplicationContextAware {
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Resource[] resources;
        try {
            resources = applicationContext.getResources("");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            File absoluteFile = resources[0].getFile().getAbsoluteFile();
            System.out.println(absoluteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(Arrays.toString(resources));
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.registerSingleton("bean", new Object());
        BeanDefinition beanDefinition = new RootBeanDefinition();
        MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();
        propertyValues.add("name", "zcq1");
        propertyValues.add("name", "zcq2");
        propertyValues.add("name", "zcq3");
        System.out.println(beanDefinition);
    }


    @Override
    public void setEnvironment(Environment environment) {
        System.out.println(environment);
    }
}
