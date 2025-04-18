package com.zcq.demo.tx.service;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MyService {

    @Resource
    JdbcTemplate jdbcTemplate;
    @Resource
    MyServiceOther myServiceOther;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void query() {
        jdbcTemplate.query("select * from gamer", (rs, rowNum) -> {
            System.out.println(rs.getString("name"));
            return null;
        });
        myServiceOther.update();
    }

    @Transactional(propagation = Propagation.NEVER)
    public void update() {
        jdbcTemplate.update("insert into gamer(name) values (?)", "zcq");
    }
}

