package com.zcq.demo.getbean;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
public class Person {

    private String name;
    private Integer age;


    public Person() {
        System.out.println("Person 无参构造函数");
    }

//    @Autowired(required = false)
    public Person(String name, Integer age) {
        this.name = name;
        this.age = age;
        System.out.println("Person 有参构造函数");
    }
//    @Autowired
    public Person(Integer age, String name) {
        this.age = age;
        this.name = name;
    }
}

@Component
class PersonBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        GenericBeanDefinition beanDefinition = (GenericBeanDefinition) beanFactory.getBeanDefinition("person");
        ConstructorArgumentValues constructorArgumentValues = beanDefinition.getConstructorArgumentValues();
        constructorArgumentValues.addGenericArgumentValue("18");
        constructorArgumentValues.addGenericArgumentValue("zcq");
    }
}