package com.zcq.io;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.FutureTask;

public class SocketNIO {

    public static void main(String[] args) throws Exception {
        ServerSocketChannel serverSocketChannel = null;
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(6666));

        SocketChannel socketChannel = serverSocketChannel.accept();
        serverSocketChannel.close();



    }
}
