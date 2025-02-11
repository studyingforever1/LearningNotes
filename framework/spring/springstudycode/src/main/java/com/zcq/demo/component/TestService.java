package com.zcq.demo.component;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Service;

@Service
public abstract class TestService {

    @Lookup
    public abstract void t();
}
