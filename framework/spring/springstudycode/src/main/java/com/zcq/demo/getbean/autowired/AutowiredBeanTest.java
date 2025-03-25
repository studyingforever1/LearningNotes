package com.zcq.demo.getbean.autowired;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AutowiredBeanTest {
    public static void main(String[] args) {

        AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext("com.zcq.demo.getbean.autowired");

        MyAutowiredBean bean = applicationContext.getBean(MyAutowiredBean.class);
        System.out.println(bean);
        MyLazyBean myLazyBean = (MyLazyBean) applicationContext.getBean("myLazyBean");
        System.out.println(myLazyBean);


        applicationContext.close();
    }
}
