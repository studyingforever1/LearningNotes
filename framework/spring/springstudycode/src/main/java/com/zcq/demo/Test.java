package com.zcq.demo;

import com.example.demo.beanPostProcessor.MyInstantiationAwareBeanPostProcessor;
import org.springframework.util.ClassUtils;

import java.util.function.Consumer;

public class Test {

//    @DeclareParents(value = "com.example.demo.aop.xml.service.MyCalculator")
//    private String a;

    public static void main(String[] args) {

        Object object = new Object();
        System.out.println(object.getClass().isAssignableFrom(int.class));
        System.out.println(object.getClass().isAssignableFrom(Integer.class));
        System.out.println(int.class.isAssignableFrom(int.class));
        System.out.println("-------------------");
        System.out.println(ClassUtils.isAssignable(object.getClass(), int.class));
        System.out.println(ClassUtils.isAssignable(int.class, Integer.class));


        System.out.println(MyInstantiationAwareBeanPostProcessor.class.getSuperclass());
        // 下面两者是等价的
        Consumer<String> consumer = (var i) -> System.out.println(i);



//        ClassLoader defaultClassLoader = ClassUtils.getDefaultClassLoader();
//        InputStream resourceAsStream = defaultClassLoader.getResourceAsStream("applicationContext.xml");
//        System.out.println(resourceAsStream.available());


//        URL url = new URL("https://www.baidu.com");
//        System.out.println(url);
//        URLConnection urlConnection = url.openConnection();
//        urlConnection.connect();
//        System.out.println(urlConnection);

//        Observable<String> observable = Observable.just("hello","world");
//        Observer<String> observer = new Observer<String>() {
//            @Override
//            public void onCompleted() {
//
//            }
//
//            @Override
//            public void onError(Throwable e) {
//
//            }
//
//            @Override
//            public void onNext(String s) {
//
//            }
//        };
//
//        observable.subscribe(observer);

    }
}
