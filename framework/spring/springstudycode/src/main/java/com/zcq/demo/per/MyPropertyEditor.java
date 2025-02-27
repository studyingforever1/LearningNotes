package com.zcq.demo.per;

import java.beans.PropertyEditorSupport;

public class MyPropertyEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        MyProperty myProperty = new MyProperty();
        String[] split = text.split("_");
        myProperty.setName(split[0]);
        myProperty.setAge(split[1]);
        setValue(myProperty);
    }
}
