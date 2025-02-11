package com.zcq.demo;

import com.example.demo.lookup.Fruit;

public class SpringTestDemo {
    public static void main(String[] args) {
//        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");
//        System.getProperties().setProperty("zcq","applicationContext");
//        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("${zcq}.xml");
//        applicationContext.getBean("");

        System.getProperties().setProperty("zcq", "applicationContext3");
        MyApplicationContext applicationContext = new MyApplicationContext("${zcq}.xml");
        Fruit bean = (Fruit) applicationContext.getBean("fruit");
//        Fruit bean2 = (Fruit) applicationContext.getBean("fruit2");
        Fruit fruit = bean.getFruit();
        Fruit fruit2 = bean.getFruit();
        System.out.println(fruit);
        System.out.println(fruit2);
//        System.out.println(bean2.getFruit());

    }
}
