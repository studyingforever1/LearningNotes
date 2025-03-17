import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

public class Test {
    public static void main(String[] args) {
//        //通过应用类加载器可以加载到java.class.path下的文件
//        ClassLoader classLoader = com.zcq.demo.test.Test.class.getClassLoader();
//        URL resource = classLoader.getResource("hello.c");
//        System.out.println(resource);
//        //通常java.class.path的所有扫描路径保存在properties中
//        Properties properties = System.getProperties();
//        String property = System.getProperty("java.class.path");
//        String[] split = property.split(";");
//        System.out.println(Arrays.toString(split));


        System.out.println(parseDate("2007-12-01"));

        System.out.println(String.format("0 {1}","撒大苏打","撒的"));

        System.out.println("123.asd".matches("-?\\d+(\\.\\d+)?"));
        System.out.println(new Boolean("true").equals(Boolean.TRUE));
        System.out.println(new Boolean("false").equals(Boolean.TRUE));
        System.out.println(new Boolean("true").compareTo(Boolean.TRUE) == 0);
        System.out.println(new Boolean("false").compareTo(Boolean.TRUE) == 0);
        System.out.println(DatePattern.NORM_DATETIME_PATTERN);
        System.out.println(StrUtil.removeAllLineBreaks("123213\r\n"));
        System.out.println(DateUtil.beginOfDay(new Date()));
        System.out.println(DateUtil.endOfDay(new Date()));
        System.out.println(DateUtil.month(new Date()));
//        DateUtil.isIn()

    }


    public static Date parseDate(String string) {
        DateFormat dateInstance = DateFormat.getDateInstance();
        try {
            return dateInstance.parse(string);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }


}
