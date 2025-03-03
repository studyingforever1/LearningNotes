package com.zcq.demo.annotation;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class AnnotationTest {
    public static void main(String[] args) {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("annotation.xml");
        AnnotationConfigApplicationContext ac1 = new AnnotationConfigApplicationContext("com.zcq.demo");



//        testResourceLoader(ac);
    }
    public static void testResourceLoader(AbstractApplicationContext ac){
        ResourceLoader resourceLoader = new PathMatchingResourcePatternResolver();
        Resource resource = resourceLoader.getResource("classpath:myconfig2.properties");
        System.out.println(resource);
        Environment environment = ac.getBean(Environment.class);
        String s = environment.resolvePlaceholders("myconfig2.name");
        System.out.println(s);
    }
}
