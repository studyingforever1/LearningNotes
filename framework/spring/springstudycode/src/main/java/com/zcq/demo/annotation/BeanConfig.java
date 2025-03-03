package com.zcq.demo.annotation;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.ManagedBean;
import javax.annotation.Resource;
import javax.inject.Named;

@Conditional({WindowsCondition.class})
@Configuration
@Named
@ManagedBean
@Description("xxx描述")
@Role(1)
public class BeanConfig {
    @Bean(name = "bill")
    public Person person1(){
        return new Person("Bill Gates",62);
    }
    @Conditional({LinuxCondition.class})
    @Bean("linus")
    public Person person2(){
        return new Person("Linus",48);
    }



 }