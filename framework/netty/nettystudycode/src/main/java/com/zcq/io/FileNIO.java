package com.zcq.io;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FileNIO {

    public static void main(String[] args) throws  Exception{
        FileInputStream fileInputStream = new FileInputStream("D:\\code\\nettystudycode\\src\\main\\resources\\fileio.txt");
        FileChannel channel = fileInputStream.getChannel();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1);
        channel.read(byteBuffer);
        byteBuffer.mark();
        System.out.println(byteBuffer);
//        WeakReference<ByteBuffer> weakReference = new WeakReference<>(byteBuffer);
//        PhantomReference<ByteBuffer> phantomReference = new PhantomReference<>(byteBuffer,null);

        MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1);

    }


}
