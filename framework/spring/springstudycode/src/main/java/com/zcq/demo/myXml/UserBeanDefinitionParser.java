package com.zcq.demo.myXml;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

public class UserBeanDefinitionParser implements BeanDefinitionParser {

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        String id = element.getAttribute("id");
        String name = element.getAttribute("userName");
        String email = element.getAttribute("email");
        String password = element.getAttribute("password");

        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(User.class)
                .addPropertyValue("id", id)
                .addPropertyValue("name", name)
                .addPropertyValue("email", email)
                .addPropertyValue("password", password)
                .getBeanDefinition();

        parserContext.getRegistry().registerBeanDefinition("user", beanDefinition);

        return null;
    }
}
