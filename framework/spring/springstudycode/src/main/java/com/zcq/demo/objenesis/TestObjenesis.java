package com.zcq.demo.objenesis;

import org.springframework.objenesis.Objenesis;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

public class TestObjenesis {
    public static void main(String[] args) {
        Objenesis objenesis = new ObjenesisStd();
        ObjectInstantiator<TestObjenesis> instantiatorOf = objenesis.getInstantiatorOf(TestObjenesis.class);
        TestObjenesis testObjenesis = instantiatorOf.newInstance();
        System.out.println(testObjenesis);
    }
}
