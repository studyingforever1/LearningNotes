package com.zcq.springmvcstudycode.controller;

import com.zcq.springmvcstudycode.bean.Student;
import com.zcq.springmvcstudycode.bean.User;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Controller
public class TestController {

    @InitBinder
    public void initBinder(WebDataBinder webDataBinder) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        CustomDateEditor dateEditor = new CustomDateEditor(df, true);
        webDataBinder.registerCustomEditor(Date.class, dateEditor);
    }


    @RequestMapping("/param")
    public String params(Date date, Map<String, Object> map) {
        System.out.println(date);
        map.put("name", "zhangsan");
        map.put("age", 18);
        map.put("sex", "男");
        return "map";
    }

    @InitBinder("user")
    public void initBinderUser(WebDataBinder webDataBinder) {
        webDataBinder.setFieldDefaultPrefix("u.");
    }

    @InitBinder("stu")
    public void initBinderStu(WebDataBinder webDataBinder) {
        webDataBinder.setFieldDefaultPrefix("s.");
    }

    @RequestMapping("/getBean")
    public ModelAndView getBean(User user, @ModelAttribute("stu") Student stu) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("user", user);
        modelAndView.addObject("stu", stu);
        modelAndView.setViewName("success");
        return modelAndView;
    }

}
