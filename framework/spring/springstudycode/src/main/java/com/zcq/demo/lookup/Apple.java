package com.zcq.demo.lookup;

import org.springframework.beans.factory.support.MethodReplacer;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;
import java.lang.reflect.Method;

//@Component
//@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Apple extends Fruit implements MethodReplacer {

    @Resource
    ApplicationContext applicationContext;

    @Override
    public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
        System.out.println("替换");
        return applicationContext.getBean("apple");
    }
}
