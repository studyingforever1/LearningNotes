package com.zcq.demo.getbean.autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;

@Component
@Lazy
@Primary
@Priority(1)
public class MyComponet {
}
