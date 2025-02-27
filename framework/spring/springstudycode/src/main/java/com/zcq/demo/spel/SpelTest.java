package com.zcq.demo.spel;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class SpelTest {

    public static void main(String[] args) {
        SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
        Expression expression = spelExpressionParser.parseExpression("1+2");
        System.out.println(expression.getValue());
    }

}
