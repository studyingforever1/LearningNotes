package com.zcq.demo.tx;

import com.zcq.demo.tx.service.MyService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TxTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext("com.zcq.demo.tx");
        MyService bean = ac.getBean(MyService.class);
        System.out.println(bean);
        bean.query();
    }
}
