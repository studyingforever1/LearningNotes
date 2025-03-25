package com.zcq.demo.getbean.autowired;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy(value = true)
public class MyLazyBean {

    public MyLazyBean() {
        System.out.println("MyLazyBean init");
    }

}
