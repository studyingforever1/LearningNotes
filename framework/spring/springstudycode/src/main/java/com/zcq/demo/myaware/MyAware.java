package com.zcq.demo.myaware;

import org.springframework.beans.factory.Aware;

public interface MyAware extends Aware {

    void setMyAware(Object object);

}
