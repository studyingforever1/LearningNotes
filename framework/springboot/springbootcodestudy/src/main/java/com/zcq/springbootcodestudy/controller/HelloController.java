package com.zcq.springbootcodestudy.controller;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Past;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Properties;

@RestController
@Validated
public class HelloController implements ApplicationContextAware {

    @Autowired
    MyProperties myProperties;


    @RequestMapping("/hello")
    public String hello(@RequestParam(name = "data") @Past @DateTimeFormat(pattern = "yyyy-MM-dd") Date date){
        return "hello world";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println(myProperties);
        System.out.println(myProperties.getName());
        System.out.println(myProperties.getAge());
    }
}
