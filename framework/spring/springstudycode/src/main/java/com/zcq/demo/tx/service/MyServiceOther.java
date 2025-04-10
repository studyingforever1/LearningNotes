package com.zcq.demo.tx.service;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MyServiceOther {

    @Resource
    JdbcTemplate jdbcTemplate;


    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void query(){
        jdbcTemplate.query("select * from gamer", (rs, rowNum) -> {
            System.out.println(rs.getString("name"));
            return null;
        });
        update();
    }

    @Transactional
    public void update(){
        jdbcTemplate.update("insert into gamer(name) values (?)", "zcq");
    }
}

