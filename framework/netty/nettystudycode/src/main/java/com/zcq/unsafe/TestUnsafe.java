package com.zcq.unsafe;


import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

public class TestUnsafe {

    public static void main(String[] args) throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);

//        int[] arr = new int[]{1, 2, 3, 4, 5};
//
//        ByteBuffer allocate = ByteBuffer.allocate(10);
//
//        int arrayBaseOffset = unsafe.arrayBaseOffset(int[].class);
//        int arrayIndexScale = unsafe.arrayIndexScale(int[].class);
//
//        unsafe.putInt(arr, arrayBaseOffset + arrayIndexScale * 3L, 200);
//        System.out.println(Arrays.toString(arr));

        ByteOrder order = ByteOrder.nativeOrder();
        System.out.println(order);

        long allocateMemory = unsafe.allocateMemory(4);

        unsafe.putInt(allocateMemory, 2000000000);
        unsafe.setMemory(allocateMemory, 1, new Integer(68).byteValue());
        System.out.println("读出来的数字:" + unsafe.getInt(allocateMemory));

        System.out.println(Integer.toBinaryString(2000000000));
        System.out.println(Integer.toBinaryString(2000000068));
        System.out.println(Integer.toBinaryString(68));

        //10000000000000000000000000000000
        //1111111111111111111111111111111
//        System.out.println(Integer.toBinaryString(Integer.MAX_VALUE));
//        System.out.println(Integer.MAX_VALUE + 1);
//        System.out.println(Integer.toBinaryString(new Integer(Integer.MAX_VALUE).byteValue()));
//
//        System.out.println(Integer.toBinaryString(68));
//        System.out.println(Integer.toBinaryString(1145324612));
//
//        System.out.println(Arrays.toString(arr));

        test();

    }


    public static void test() {
        System.out.println(ByteOrder.nativeOrder());

        int x = 0x0104;
        System.out.println(Integer.toBinaryString(x));
        System.out.println(Integer.toBinaryString(Integer.reverseBytes(x)));
        //00000000,00000000,00000001,00000100
        //00000100,00000001,00000000,00000000

        int x1 = 0x01020304;
        System.out.println(Integer.toBinaryString(x1));
        System.out.println(Integer.toBinaryString(Integer.reverseBytes(x1)));

        //00000001,00000010,00000011,00000100
        //00000100,00000011,00000010,00000001
    }
}
