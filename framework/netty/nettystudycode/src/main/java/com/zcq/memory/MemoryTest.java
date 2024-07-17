package com.zcq.memory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class MemoryTest {

    public static void main(String[] args) {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        ByteBuf byteBuf = pooledByteBufAllocator.buffer();
        byteBuf.writeBytes(new byte[1024 * 1024 * 10]);
        System.out.println(byteBuf.refCnt());

        System.out.println(getPower2(Integer.MAX_VALUE));
    }

    public static void testOther() {
        System.out.println((1 & ~15) + 16);
        //00000000000000000000000011100000
        //11111111111111111111111111110000
        System.out.println(Integer.toBinaryString(1));
        System.out.println(Integer.toBinaryString(~(15)));
    }


    /**
     * 通过不断右移和|运算 将最高位后面的位全部置为1 最后再+1
     */

    public static int getPower2(int reqCapacity){
        //假设输入的是3 011
        int normalizedCapacity = reqCapacity;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        //这里减去1的目的应该是为了兼容本来就是2的幂的情况
        //010
        normalizedCapacity --;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        //这里一开始移动1位  010 | 001 = 011
        normalizedCapacity |= normalizedCapacity >>>  1;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        //因为前面的2位都被设置为1了 所以可以移动2位 通过前面的两位把接下来两位设置为1
        normalizedCapacity |= normalizedCapacity >>>  2;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        //那么同理 再用前4位1 把接下来的4位设置为1
        normalizedCapacity |= normalizedCapacity >>>  4;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        normalizedCapacity |= normalizedCapacity >>>  8;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        normalizedCapacity |= normalizedCapacity >>> 16;
        System.out.println(Integer.toBinaryString(normalizedCapacity));
        //因为int只有32位 抛去符号位 所以移动 1+2+4+8+16 = 31位就可以把全部位设置为1了
        //加1取2的幂
        normalizedCapacity ++;
        System.out.println(Integer.toBinaryString(normalizedCapacity));

//        if (normalizedCapacity < 0) {
//            normalizedCapacity >>>= 1;
//        }
        return normalizedCapacity;
    }
}
