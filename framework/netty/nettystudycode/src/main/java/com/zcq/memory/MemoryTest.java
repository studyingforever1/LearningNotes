package com.zcq.memory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class MemoryTest {

    public static void main(String[] args) {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        ByteBuf byteBuf = pooledByteBufAllocator.buffer();
        byteBuf.writeBytes(new byte[1024 * 1024 * 10]);
        System.out.println(byteBuf.refCnt());


        System.out.println(Integer.toBinaryString(8192));
        System.out.println(Integer.toBinaryString(~(8192-1)));
    }
}
