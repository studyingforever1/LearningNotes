package com.zcq.demo.getbean.createbean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@Component
public class MyFactoryMethod {

    public TestObject myFactoryMethodNoStatic() {
        System.out.println("MyFactoryMethod createMyFactoryMethod");
        return new TestObject();
    }

    public static TestObject myFactoryMethodStatic() {
        System.out.println("MyFactoryMethod createMyFactoryMethod");
        return new TestObject();
    }
}

@Component
@ComponentScan
class TestObject {

}

@Component
class MyFactoryMethodFactoryBeanPostProcessor implements BeanFactoryPostProcessor{
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition) beanFactory.getBeanDefinition("testObject");
        // 设置工厂方法
        beanDefinition.setFactoryBeanName("myFactoryMethod");
        beanDefinition.setFactoryMethodName("myFactoryMethodNoStatic");
        //静态工厂
//        beanDefinition.setBeanClass(MyFactoryMethod.class);
//        beanDefinition.setFactoryMethodName("myFactoryMethodStatic");
    }
}
