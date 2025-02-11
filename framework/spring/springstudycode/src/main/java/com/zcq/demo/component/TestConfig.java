package com.zcq.demo.component;

import com.example.demo.converter.MyConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import javax.annotation.ManagedBean;

@Component
@ManagedBean
@ComponentScan(basePackages = "com.example.demo")
//@Import(Import.class)
//@ImportResource(value = "classpath:a1.txt")
@PropertySource(value = "classpath:a.txt", encoding = "UTF-8")//加载进来扔到StandEnv对象里面
public class TestConfig {


    @Bean
    public Converter<?,?> getConverter(){
        return new MyConverter();
    }


    private String ss;

    public TestConfig() {

    }

    public String getSs() {
        return ss;
    }

    public void setSs(String ss) {
        this.ss = ss;
    }
}
