package com.zcq.demo.testclassloader;

public class Test {
    public static void main(String[] args) {


//        String class_path = System.getProperty("java.class.path");
//        String boot_path = System.getProperty("sun.boot.class.path");
//        String ext_path = System.getProperty("java.ext.dirs");
//        String vm_version = System.getProperty("java.version");
//
//        System.out.println("java.class.path:" + class_path);
//        System.out.println("sun.boot.class.path:" + boot_path);
//        System.out.println("java.ext.dirs:" + ext_path);
//        System.out.println("java.version:" + vm_version);
//        String path = System.getProperty("java.class.path");
//        StringTokenizer tok = new StringTokenizer(path, File.pathSeparator);
//        System.out.println(tok);

          System.getProperties().getProperty("java.class.path");








//        ClassLoader classLoader = Test.class.getClassLoader();
//        System.out.println(classLoader);
//        ClassLoader parentClassLoader = classLoader.getParent();
//        System.out.println(parentClassLoader);
//        ClassLoader parent = parentClassLoader.getParent();
//        System.out.println(parent);
//
//        System.out.println(String.class.getClassLoader());


//        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
//        ClassLoader platformClassLoader = ClassLoader.getPlatformClassLoader();
//        System.out.println(systemClassLoader);
//        System.out.println(platformClassLoader);
    }
}
