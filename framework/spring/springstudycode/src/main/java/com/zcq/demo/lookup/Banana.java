package com.zcq.demo.lookup;

//@Component
//@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Banana extends Fruit{

    @Override
    public Fruit getFruit() {
        return super.getFruit();
    }
}
