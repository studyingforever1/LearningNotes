package com.zcq.springmvcstudycode.controller;

import com.zcq.springmvcstudycode.bean.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Administrator
 */
@Controller
public class FileUploadController {

//    @SessionAttribute
    @ModelAttribute
    public Map<String, Object> a() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", "a");
        map.put("b", "b");
        map.put("c", "c");
        return map;
    }

    @RequestMapping("/uploadServlet")
    @ResponseStatus(code = HttpStatus.ACCEPTED)
    @ResponseBody
    protected User handleRequestInternal(HashMap<String, Object> map) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("ok");

        map.put("name", "zhangsan");

        return new User("zhangsan",18);
    }
}
