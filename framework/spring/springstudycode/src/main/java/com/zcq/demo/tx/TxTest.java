package com.zcq.demo.tx;

import com.zcq.demo.tx.service.MyService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.springframework.cglib.core.DebuggingClassWriter.DEBUG_LOCATION_PROPERTY;

public class TxTest {
    public static void main(String[] args) {
        configAop();
        AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext("com.zcq.demo.tx");
        MyService bean = ac.getBean(MyService.class);
        bean.query();

//        MyService myServiceSon = new MyServiceSon();
//        myServiceSon.query();

    }

    private static void configAop() {
        String path = "D:\\doc\\my\\studymd\\LearningNotes\\framework\\spring\\springstudycode\\src\\main\\java\\com\\zcq\\demo\\tx\\aopclazz";
        System.out.println(path);
        System.setProperty(DEBUG_LOCATION_PROPERTY, path);
    }
}
