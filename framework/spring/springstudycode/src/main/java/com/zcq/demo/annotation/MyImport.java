package com.zcq.demo.annotation;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Component
@Import(MyImportSelector.class)
@Order(HIGHEST_PRECEDENCE)
public class MyImport {
}
