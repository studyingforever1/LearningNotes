package com.zcq.reference;

import java.lang.ref.*;

public class ReferenceTest {

    //强软弱虚
    public static void main(String[] args) throws Exception{

//        testWeakReference();
//        testPhantomReference();
//        testReference();
        testFinalReference();
    }

    private static void testFinalReference() {
        MyFinalReference myFinalReference = new MyFinalReference();
        System.out.println(myFinalReference);
        myFinalReference = null;
        System.gc();
    }

    static class MyReference<T> extends WeakReference<T> {

        public MyReference(T referent) {
            super(referent);
        }

        public MyReference(T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
        }

        @Override
        protected void finalize() {
            System.out.println("finalize");
        }
    }

    static class MyFinalReference {

        @Override
        protected void finalize() {
            System.out.println("finalizeddddddd");
        }
    }

    static class User{
        private String name;
        private Integer age;
        public User(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }





    private static void testReference() throws Exception {
        User user = new User("李四", 13);
        ReferenceQueue<Integer> referenceQueue = new ReferenceQueue<>();
        MyReference o = new MyReference(user, referenceQueue);
        System.out.println(user);
        user = null;
        System.gc();
        System.out.println(referenceQueue.remove());
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
