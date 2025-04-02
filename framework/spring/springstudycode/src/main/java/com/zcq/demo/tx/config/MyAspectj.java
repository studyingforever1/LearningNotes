package com.zcq.demo.tx.config;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class MyAspectj {

    @Pointcut("execution(* com.zcq.demo.tx.*.*.* (..))")
    public void pointcut(){
    }

    @Before("pointcut()")
    public void before(JoinPoint joinPoint){
        System.out.println(joinPoint.getSignature().getName() + " before");
    }
    @After("pointcut()")
    public void after(JoinPoint joinPoint){
        System.out.println(joinPoint.getSignature().getName() + " after");
    }


}
