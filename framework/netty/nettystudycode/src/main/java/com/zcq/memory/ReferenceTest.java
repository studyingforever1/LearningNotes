package com.zcq.memory;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class ReferenceTest {

    //强软弱虚
    public static void main(String[] args) {

//        testWeakReference();
        testPhantomReference();
    }


    public static void testWeakReference() {
        String string = new String("abc");
        WeakReference<String> weakReference = new WeakReference<>(string);
        System.gc();
        System.out.println(weakReference.get());
        string = null;
        System.gc();
        System.out.println(weakReference.get());
    }
    public static void testPhantomReference() {
        ReferenceQueue referenceQueue = new ReferenceQueue();
        String string = new String("abc");
        PhantomReference<String> phantomReference = new PhantomReference<>(string, referenceQueue);
        System.out.println(phantomReference.get());
        string = null;
        System.gc();
        System.out.println(referenceQueue);
        System.out.println(phantomReference);
        System.out.println(referenceQueue.poll());
    }
}
