package com.zcq.demo.getbean.createbean;

import com.zcq.demo.getbean.MyCommponet;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if (beanClass.equals(MyCommponet.class)){
            return new MyComponet("zcq");
        }
        return null;
    }
}
