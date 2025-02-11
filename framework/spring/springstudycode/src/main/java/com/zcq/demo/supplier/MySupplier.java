package com.zcq.demo.supplier;

import com.example.demo.factorymethod.User;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class MySupplier implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition("user");
        AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
        abstractBeanDefinition.setInstanceSupplier(new Supplier<User>() {
            @Override
            public User get() {
                return new User();
            }
        });
    }
}
