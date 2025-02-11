package com.zcq.demo;

import org.springframework.core.env.AbstractPropertyResolver;

public class MyPropertyResolver extends AbstractPropertyResolver {


    public void ff() {


        String xxx = resolveRequiredPlaceholders("${username}");
    }


    @Override
    protected String getPropertyAsRawString(String key) {
        return null;
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType) {
        return null;
    }
}
