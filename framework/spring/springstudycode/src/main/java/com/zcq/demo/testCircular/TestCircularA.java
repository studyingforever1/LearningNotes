package com.zcq.demo.testCircular;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestCircularA {

    @Autowired
    public TestCircularA(TestCircularB testCircularB){

    }


}
