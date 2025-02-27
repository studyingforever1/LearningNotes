package com.zcq.demo.per;

public class Test {
    public static void main(String[] args) {
        MyApplicationContext context = new MyApplicationContext("per.xml");
        MyProperties bean = context.getBean(MyProperties.class);
        System.out.println(bean);
    }
}
