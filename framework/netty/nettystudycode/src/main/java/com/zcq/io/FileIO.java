package com.zcq.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileIO {

    public static void main(String[] args) throws IOException {
        FileInputStream fileInputStream = new FileInputStream("D:\\code\\nettystudycode\\src\\main\\resources\\fileio.txt");
        FileChannel channel = fileInputStream.getChannel();
        int read = fileInputStream.read();
        System.out.println(read);
    }
}
