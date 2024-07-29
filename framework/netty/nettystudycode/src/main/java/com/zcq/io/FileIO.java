package com.zcq.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.FileChannel;

public class FileIO {

    public static void main(String[] args) throws IOException {

        //get classpath
        URL resource = FileIO.class.getClassLoader().getResource("fileio.txt");
        String file = resource.getFile();
        FileInputStream fileInputStream = new FileInputStream(file);
        FileChannel channel = fileInputStream.getChannel();
        int read = fileInputStream.read();
        System.out.println(read);
    }
}
