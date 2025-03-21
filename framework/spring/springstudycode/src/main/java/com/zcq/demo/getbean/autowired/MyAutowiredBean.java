package com.zcq.demo.getbean.autowired;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@Component
public class MyAutowiredBean {

    private MyComponet myComponet;

    @Autowired
    @Qualifier("myPropertyBean")
    private MyPropertyBean myPropertyBean;

    @Resource
    private MyResourceBean myResourceBean;

    @Value("${user.name}")
    private String username;

    @Autowired
    List<MyPropertyBean> myBeans;

    @Autowired
    Map<String, Object> map;

    @Autowired
    MyAutowiredBean myAutowiredBean;


    @PostConstruct
    public void postConstruct() {
        System.out.println("postConstruct");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("preDestroy");
    }


    @Autowired
    public MyAutowiredBean(/*@Value("aaaa")*/ MyComponet myComponet) {
        this.myComponet = myComponet;
    }

    @Bean
    public Object myObj(List<MyInterface> myInterface) {
        System.out.println(111);
        System.out.println(myInterface);
        return new Object();
    }

    @Bean
    @Autowired
    public MyBean myBean(MyBeanBean myBeanBean) {
        return new MyBean();
    }

    @Autowired
    public void ttt(MyBeanBean myBeanBean) {
        System.out.println(myBeanBean);
    }

    @Override
    public String toString() {
        return "MyAutowiredBean{" +
                "myComponet=" + myComponet +
                ", myPropertyBean=" + myPropertyBean +
                ", myResourceBean=" + myResourceBean +
                ", username='" + username + '\'' +
                '}';
    }
}



