package com.zcq.demo.test.per;

public class MyProperties {
    private MyProperty myProperty;

    public MyProperty getMyProperty() {
        return myProperty;
    }

    public void setMyProperty(MyProperty myProperty) {
        this.myProperty = myProperty;
    }

    @Override
    public String toString() {
        return "MyProperties{" +
                "myProperty=" + myProperty +
                '}';
    }
}
