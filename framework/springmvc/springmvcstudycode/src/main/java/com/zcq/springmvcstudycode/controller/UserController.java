package com.zcq.springmvcstudycode.controller;

import com.zcq.springmvcstudycode.bean.User;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Controller
public class UserController {

    @CrossOrigin
    @RequestMapping(
            path = "/users/{id}",
            method = RequestMethod.GET,
            produces = "application/json",
            consumes = "application/json",
            headers = "X-Custom-Header=custom-value",
            params = "oid=1"
    )
    @ResponseBody
    public User getUser(@PathVariable Long id,
                        @RequestParam(required = false) Long oid,
                        @RequestBody User user) {
        System.out.println(user);
        System.out.println(id);
        return new User("zhangsan", 18);
    }


    @RequestMapping(value = "/testCache")
    public ResponseEntity<String> testCache() {

        LocalDateTime localDateTime = LocalDateTime.of(2030, 1, 1, 1, 1);
        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.ofHours(8));

        return ResponseEntity.status(HttpStatus.OK)
                .cacheControl(CacheControl.noCache())
                .lastModified(epochSecond)
                .eTag("123")
                .body("datassssssss");
    }
}
