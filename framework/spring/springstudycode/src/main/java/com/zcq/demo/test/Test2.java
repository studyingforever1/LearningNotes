package com.zcq.demo.test;

import java.io.File;
import java.net.URL;

public class Test2 {
    public static void main(String[] args) {
        URL resource1 = Test.class.getClassLoader().getResource("");
        File file = new File(resource1.getFile());
        System.out.println(file);
        File absoluteFile = file.getAbsoluteFile();
        System.out.println(absoluteFile);
    }
}
