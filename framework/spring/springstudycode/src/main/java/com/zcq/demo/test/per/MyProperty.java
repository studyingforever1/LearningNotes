package com.zcq.demo.test.per;

public class MyProperty {
    private String name;
    private String age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "MyProperty{" +
                "name='" + name + '\'' +
                ", age='" + age + '\'' +
                '}';
    }
}
