package com.zcq.demo.testWeight;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.stereotype.Component;

@Component
public class ChenssBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) beanFactory;
        GenericBeanDefinition myWeight = (GenericBeanDefinition) defaultListableBeanFactory.getBeanDefinition("myWeight");
        myWeight.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        myWeight.setLenientConstructorResolution(false); // 宽松模式
        //taggerDao.setLenientConstructorResolution(true);// 严格模式
    }
}
