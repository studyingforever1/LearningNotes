package com.zcq.demo.factorymethod;

public class UserStaticFactoryMethod {
    public static User getUserFactoryMethod(String name) {
        User user = new User();
        user.setAge(1);
        user.setUsername(name);
        return user;
    }
}
