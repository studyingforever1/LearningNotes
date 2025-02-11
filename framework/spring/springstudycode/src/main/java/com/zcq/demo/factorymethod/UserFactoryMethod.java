package com.zcq.demo.factorymethod;

public class UserFactoryMethod {

    public User getUserFactoryMethod(String name) {
        User user = new User();
        user.setAge(1);
        user.setUsername(name);
        return user;
    }
}
