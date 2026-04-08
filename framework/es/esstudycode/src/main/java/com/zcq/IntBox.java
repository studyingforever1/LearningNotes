package com.zcq;

import java.util.List;

public class IntBox implements Comparable<IntBox> {
    List<String> names;
    List<String> getNames() { return names; }
    int val;
    @Override
    public int compareTo(IntBox o) { return Integer.compare(val, o.val); }
}
