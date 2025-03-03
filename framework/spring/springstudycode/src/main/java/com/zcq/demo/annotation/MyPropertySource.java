package com.zcq.demo.annotation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource({"classpath:myconfig2.properties"})
@PropertySource({"classpath:myconfig3.yml"})
public class MyPropertySource {

    @Value("${myconfig2.name}")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
