package com.zcq.demo.getbean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.MethodInvoker;

public class GetBeanTest {
    public static void main(String[] args) {

        AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext("com.zcq.demo.getbean");

//        MyLookup myLookup = (MyLookup) applicationContext.getBean("myLookup");
//        System.out.println(myLookup.getMyCommponet());


//        System.out.println(MethodInvoker.getTypeDifferenceWeight(new Class[]{Person.class}, new Object[]{new Person()}));


        //        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("${user.name}");
//        User myFactoryBean = (User) applicationContext.getBean("myFactoryBean");
//        MyFactoryBean myFactoryBean2 = (MyFactoryBean) applicationContext.getBean("&myFactoryBean");

    }
}
