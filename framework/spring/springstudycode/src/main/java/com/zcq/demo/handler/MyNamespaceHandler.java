package com.zcq.demo.handler;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


public class MyNamespaceHandler implements NamespaceHandler {


    @Override
    public void init() {

    }

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        System.out.println(element);
        return null;
    }

    @Override
    public BeanDefinitionHolder decorate(Node source, BeanDefinitionHolder definition, ParserContext parserContext) {
        return null;
    }
}
