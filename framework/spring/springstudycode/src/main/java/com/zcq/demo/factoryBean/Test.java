package com.zcq.demo.factoryBean;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext4.xml");
        Object myFactoryBean1 = applicationContext.getBean("myFactoryBean");
        Object myFactoryBean2 = applicationContext.getBean("&myFactoryBean");
        Object myFactoryBean3 = applicationContext.getBean(MyFactoryBean.class);
        Object myFactoryBean4 = applicationContext.getBean(String.class);

        System.out.println(myFactoryBean1);
        System.out.println(myFactoryBean2);
        System.out.println(myFactoryBean3);
        System.out.println(myFactoryBean4);

        Object testAbstract = applicationContext.getBean("testAbstract");
        System.out.println(testAbstract);
    }
}
