package com.zcq.demo.converter;

import org.springframework.core.convert.converter.Converter;

public class MyConverter implements Converter<String, Integer> {

    @Override
    public Integer convert(String source) {
        return Integer.parseInt(source);
    }
}
