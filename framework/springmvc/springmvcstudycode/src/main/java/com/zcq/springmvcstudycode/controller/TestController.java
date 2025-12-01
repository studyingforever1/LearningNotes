package com.zcq.springmvcstudycode.controller;

import com.zcq.springmvcstudycode.bean.Student;
import com.zcq.springmvcstudycode.bean.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.core.ResolvableType;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import static org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event;

@Controller
public class TestController {

    @InitBinder
    public void initBinder(WebDataBinder webDataBinder) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        CustomDateEditor dateEditor = new CustomDateEditor(df, true);
        webDataBinder.registerCustomEditor(Date.class, dateEditor);
    }


    @RequestMapping("/param")
    public String params(@RequestParam("date") Date date, Map<String, Object> map) {
        System.out.println(date);
        map.put("name", "zhangsan");
        map.put("age", 18);
        map.put("sex", "男");
        return "map";
    }

    @RequestMapping("/user")
    @ResponseBody
    public User user(@RequestParam("date") Date date, Map<String, Object> map) {
        System.out.println(date);
        return new User("zhangsan", 12);
    }

    @RequestMapping("/user2")
    @ResponseBody
    public User user2(@RequestParam(value = "string") String string) {
        System.out.println(string);
        return new User();
    }

    @RequestMapping("/user3")
    @ResponseBody
    public User user3(User user) {
        return user;
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

    @RequestMapping("/requestParamMapMethodArgumentResolver")
    public Map<String, Object> requestParamMapMethodArgumentResolver(@RequestParam Map<String, Object> map) {
        return map;
    }

    @RequestMapping("/test")
    public Map<String, Object> test(Map<String, Object> map) {
        return map;
    }

    @RequestMapping("/pathVariableMethodArgumentResolver/{param}")
    public String pathVariableMethodArgumentResolver(@PathVariable(name = "param") String param) {
        return param;
    }

    @RequestMapping("/matrixVariableMapMethodArgumentResolver/cars/{carId}/owners/{ownerId}")
    public Map<String, Object> matrixVariableMapMethodArgumentResolver(@MatrixVariable Map<String, Object> map) {
        return map;
    }


    @RequestMapping("/modelAttributeMethodProcessor")
    public ModelAndView modelAttributeMethodProcessor(@Validated @ModelAttribute String string, ModelAndView modelAndView) {
        Map<String, Object> model = modelAndView.getModel();
        model.forEach((k, v) -> {
            System.out.println(k);
            System.out.println(v);
        });
        return modelAndView;
    }

    @RequestMapping("/requestResponseBodyMethodProcessor")
    public String requestResponseBodyMethodProcessor(@RequestBody Object string) {
        return string.toString();
    }

    @RequestMapping("/requestPartMethodArgumentResolver")
    public String requestPartMethodArgumentResolver(@RequestPart(name = "file") MultipartFile file,
                                                    @RequestPart(name = "user") User user,
                                                    @RequestPart(name = "string") String string,
                                                    HttpServletRequest request) {
        System.out.println(string);
        Map<String, String[]> parameterMap = request.getParameterMap();
        parameterMap.forEach((k, v) -> {
            System.out.println(k);
            System.out.println(Arrays.toString(v));
        });
        return "success";
    }

    @RequestMapping("/redirectAttributesMethodArgumentResolver")
    public String redirectAttributesMethodArgumentResolver(RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("name", "zhangsan");
        redirectAttributes.addFlashAttribute("test", "testsss");
        return "redirect:/testRedirect";
    }

    @RequestMapping("/testRedirect")
    public String testRedirect(@RequestParam(name = "name") String name, @ModelAttribute(name = "test") String test
    ) {
        System.out.println(name);
        System.out.println(test);
        return "ok";
    }


    @RequestMapping("/ssetest")
    public SseEmitter ssetest() {
        SseEmitter emitter = new SseEmitter();
        new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    emitter.send(event().name("update").id("1").data("Message " + i));
                    Thread.sleep(1000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    @RequestMapping("/responseBodyEmittertest")
    public ResponseBodyEmitter responseBodyEmittertest() {
        Random random = new Random();
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    emitter.send(new User("222", 3), MediaType.APPLICATION_JSON);
                    emitter.send("\r\n");
                    Thread.sleep(1000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }


    @GetMapping("/deferred")
    public DeferredResult<String> handleDeferred() {
        DeferredResult<String> deferredResult = new DeferredResult<>();
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                deferredResult.setResult("Hello from DeferredResult");
            } catch (InterruptedException e) {
                deferredResult.setErrorResult("Error");
            }
        }).start();

        return deferredResult;
    }

    @GetMapping(value = "/stream")
    public ResponseEntity<ResponseBodyEmitter> streamLogs() {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        new Thread(() -> {
            try {
                emitter.send(new User("222", 3), MediaType.APPLICATION_JSON);
                emitter.send("\r\n");
                emitter.send("ssss".getBytes(), MediaType.IMAGE_PNG);
                emitter.send("\r\n");
                Thread.sleep(1000);
                emitter.complete();
            } catch (Exception e) {
                // 出现异常时结束响应并传递错误信息
                emitter.completeWithError(e);
            }
        }).start();

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }


    @GetMapping("/mixed")
    public ResponseEntity<MultiValueMap<String, Object>> getMixed() {

        // --- Part 1: JSON ---
        String json = "{\"message\": \"hello\"}";
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> jsonPart = new HttpEntity<>(json, jsonHeaders);

        // --- Part 2: 文本文件 ---
        String text = "This is a text file.";
        HttpHeaders txtHeaders = new HttpHeaders();
        txtHeaders.setContentType(MediaType.TEXT_PLAIN);
        txtHeaders.setContentDisposition(
                ContentDisposition.attachment().filename("info.txt").build());
        HttpEntity<String> textPart = new HttpEntity<>(text, txtHeaders);

        // --- 组合 multipart/mixed ---
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("json", jsonPart);
        body.add("file", textPart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_MIXED);

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


    @GetMapping("/download")
    public StreamingResponseBody download() {
        return out -> {
            for (int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(1000);
                    out.write("1".repeat(8192).getBytes());
                    out.flush();
                } catch (InterruptedException ignored) {}
            }
        };
    }

    @GetMapping("/problem")
    public ProblemDetail problem() {
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
