package com.zcq.springbootcodestudy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class TestController {

    @PostMapping("/test")
    public Map<String, Object> test(HttpServletRequest request) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. getParameterMap - 获取 URL QueryString + x-www-form-urlencoded + multipart文本字段
        Map<String, String[]> parameterMap = request.getParameterMap();
        result.put("parameterMap", parameterMap);

        // 2. getParts - 获取所有 Part（文本 + 文件）
        Collection<Part> parts = request.getParts();
        List<Map<String, Object>> partList = new ArrayList<>();
        for (Part part : parts) {
            Map<String, Object> partInfo = new LinkedHashMap<>();
            partInfo.put("name", part.getName());
            partInfo.put("contentType", part.getContentType());
            partInfo.put("size", part.getSize());
            partInfo.put("submittedFileName", part.getSubmittedFileName()); // 文件才有，文本字段为null
            partInfo.put("content", new String(part.getInputStream().readAllBytes()));
            partList.add(partInfo);
        }
        result.put("parts", partList);

        return result;
    }
}