package com.zcq.demo.bfpp;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

public class Test {
    public static void main(String[] args) throws IOException {
//        ClassPathXmlApplicationContext ac = new MyApplicationContext("applicationContext.xml");

        Enumeration<URL> resources = Test.class.getClassLoader().getResources("a.yml");

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            //转换成File对象
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }
        System.out.println();

        Enumeration<URL> resources4 = Test.class.getClassLoader().getResources("");
        while (resources4.hasMoreElements()) {
            URL url = resources4.nextElement();
            //转换成File对象
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }

        System.out.println();

        Enumeration<URL> resources2 = Test.class.getClassLoader().getParent().getResources("com");
        while (resources2.hasMoreElements()) {
            URL url = resources2.nextElement();
            //转换成File对象
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }
        System.out.println();
        Enumeration<URL> resources5 = Test.class.getClassLoader().getParent().getResources("");
        while (resources5.hasMoreElements()) {
            URL url = resources5.nextElement();
            //转换成File对象
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }

        System.out.println();

        Enumeration<URL> resources3 = Test.class.getClassLoader().getParent().getParent().getResources("");
        while (resources3.hasMoreElements()) {
            URL url = resources3.nextElement();
            //转换成File对象
            File file = new File(url.getFile());
            File absoluteFile = file.getAbsoluteFile();
            System.out.println(absoluteFile);
        }

    }
}
