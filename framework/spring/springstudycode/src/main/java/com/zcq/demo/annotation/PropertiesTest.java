package com.zcq.demo.annotation;

import java.net.URL;
import java.util.Properties;

public class PropertiesTest {
    public static void main(String[] args) throws Exception {

        URL resource = PropertiesTest.class.getClassLoader().getResource("myconfig2.properties");
        URL resource1 = PropertiesTest.class.getClassLoader().getResource("myconfig3.yml");

        Properties properties = new Properties();
        properties.load(resource.openStream());
        System.out.println(properties);

        String property = properties.getProperty("myconfig2.name");

        Properties properties2 = new Properties();
        properties2.load(resource1.openStream());
        System.out.println(properties2);
    }
}
