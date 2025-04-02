package com.zcq.demo.aop.annotation;

import com.zcq.demo.aop.annotation.service.MyComponet;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.springframework.cglib.core.DebuggingClassWriter.DEBUG_LOCATION_PROPERTY;

public class AopTest {

    public static void main(String[] args) {
        configAop();
        AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext("com.zcq.demo.aop.annotation");
//        Enhancer enhancer = new Enhancer();
//        enhancer.create();

        MyComponet bean = ac.getBean(MyComponet.class);
        System.out.println(bean);
        ac.close();
    }

    private static void configAop() {
        String path = "D:\\doc\\my\\studymd\\LearningNotes\\framework\\spring\\springstudycode\\src\\main\\java\\com\\zcq\\demo\\aop\\aopclazz";
        System.out.println(path);
        System.setProperty(DEBUG_LOCATION_PROPERTY, path);
    }
}
