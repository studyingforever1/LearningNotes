package com.zcq.demo.annotation;

import org.springframework.context.annotation.ImportResource;
import org.springframework.stereotype.Component;

@Component
@ImportResource(value = {"classpath:myconfig3.yml"})
public class MyImportResouce {

}
