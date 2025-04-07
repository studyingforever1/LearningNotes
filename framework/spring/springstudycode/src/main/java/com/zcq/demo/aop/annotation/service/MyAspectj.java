package com.zcq.demo.aop.annotation.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

@Component
@Aspect
@EnableAspectJAutoProxy
public class MyAspectj {

    @Pointcut("@annotation(org.springframework.beans.factory.annotation.Value)")
    public void pointcut() {
    }

    @Before(value = "pointcut()")
    public void before() {
        System.out.println("before");
    }

    @After(value = "pointcut()")
    public void after() {
        System.out.println("after");
    }

    @Around("execution(* com.zcq.demo.aop.annotation.*.*.* (..)) && @annotation(value)")
    public Object around(ProceedingJoinPoint joinPoint, Value value) {
        try {
            System.out.println("around---" + value.value());
            joinPoint.proceed();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return 100;
    }

}
