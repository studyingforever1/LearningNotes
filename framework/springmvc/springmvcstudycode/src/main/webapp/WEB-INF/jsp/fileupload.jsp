<%--
  Created by IntelliJ IDEA.
  User: Administrator
  Date: 2025/4/16
  Time: 20:35
  To change this template use File | Settings | File Templates.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>文件上传</title>
</head>
<body>
<h1>上传文件</h1>
<form action="uploadServlet" method="post" enctype="multipart/form-data">
    <input type="file" name="file" required>
    <label>
        <input type="text" name="text" value="文本文本文本">
    </label>
    <input type="submit" value="上传">
</form>
</body>
</html>