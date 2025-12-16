package com.zcq.springbootcodestudy.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
//@ServletComponentScan
public class SpringbootcodestudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootcodestudyApplication.class, args);
    }


//    @Bean
//    public ServletRegistrationBean<MyServlet> getServletRegistrationBean(){
//        ServletRegistrationBean<MyServlet> bean = new ServletRegistrationBean<>(new MyServlet());
//        bean.setLoadOnStartup(1);
//        return bean;
//    }

}
