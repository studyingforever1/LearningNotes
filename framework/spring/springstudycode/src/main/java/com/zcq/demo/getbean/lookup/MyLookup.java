package com.zcq.demo.getbean.lookup;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

@Component
public abstract class MyLookup {

    @Lookup("myLookupObject")
    public abstract MyLookupObject getMyLookupObject();
}

