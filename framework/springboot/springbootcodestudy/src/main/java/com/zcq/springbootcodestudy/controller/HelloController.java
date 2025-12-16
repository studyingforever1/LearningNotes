package com.zcq.springbootcodestudy.controller;

import jakarta.validation.constraints.Past;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@Validated
public class HelloController {

    @RequestMapping("/hello")
    public String hello(@RequestParam(name = "data") @Past @DateTimeFormat(pattern = "yyyy-MM-dd") Date date){
        return "hello world";
    }

}
