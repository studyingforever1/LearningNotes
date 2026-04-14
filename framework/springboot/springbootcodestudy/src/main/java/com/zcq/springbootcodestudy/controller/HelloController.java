package com.zcq.springbootcodestudy.controller;

import com.zcq.springbootcodestudy.bean.Response;
import com.zcq.springbootcodestudy.bean.User;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@RestController
@Validated
public class HelloController implements ApplicationContextAware {

    @Autowired
    MyProperties myProperties;
    ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping(value = "/hello", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response hello(@RequestPart MultipartFile file, @RequestPart User user, @RequestParam String key) {
        System.out.println(file.getName());
        System.out.println(objectMapper.writeValueAsString(user));
        System.out.println(key);
        return new Response(user);
    }

//    @RequestMapping(value = "/hello")
//    public Response hello2(@RequestPart MultipartFile file, @RequestPart User user, @RequestParam String key) {
//        System.out.println(file.getName());
//        System.out.println(objectMapper.writeValueAsString(user));
//        System.out.println(key);
//        return new Response(user);
//    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println(myProperties);
        System.out.println(myProperties.getName());
        System.out.println(myProperties.getAge());
    }
}
