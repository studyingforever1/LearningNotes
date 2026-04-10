package com.zcq.demo.tx.service;

import jakarta.annotation.Resource;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MyService implements ApplicationContextAware {

    @Resource
    JdbcTemplate jdbcTemplate;
    @Resource
    MyServiceOther myServiceOther;
    @Resource
    @Lazy
    LazyBean lazyBean;


    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void query() {
        System.out.println(ac);
        lazyBean.test();
//        LazyBean lazyBean1 = (LazyBean) ac.getBean("lazyBean");
//        lazyBean1.test();
        jdbcTemplate.query("select * from s1", (rs, rowNum) -> {
            System.out.println(rs.getString("id"));
            return null;
        });
        myServiceOther.query();
    }

    @Transactional(propagation = Propagation.NEVER)
    public void update() {
        jdbcTemplate.update("insert into gamer(name) values (?)", "zcq");
    }

    ApplicationContext ac;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ac = applicationContext;
    }

    @Override
    public String toString() {
        return "";
    }
}

