package com.zcq.demo.getbean;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

@Component
public abstract class MyLookup {

    @Lookup
    public abstract MyCommponet getMyCommponet();
}
