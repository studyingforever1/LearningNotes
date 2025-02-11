package com.zcq.demo.converter;

import com.example.demo.component.TestConfig;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.annotation.Annotation;

public class TestConverter {
    public static void main(String[] args) {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        String str = "3";
        defaultConversionService.addConverter(new MyConverter());
        TypeDescriptor sourceDescriptor = TypeDescriptor.forObject(str);
        TypeDescriptor targetDescriptor = TypeDescriptor.valueOf(Integer.class);
        TypeDescriptor test = new TypeDescriptor(ResolvableType.forClass(TestConfig.class), TestConfig.class, TestConfig.class.getAnnotations());
        Annotation[] annotations = test.getAnnotations();
        Object convert = defaultConversionService.convert(str, sourceDescriptor, targetDescriptor);
        System.out.println(convert);
        defaultConversionService.addConverterFactory(new MyConverterFactory());


    }
}
