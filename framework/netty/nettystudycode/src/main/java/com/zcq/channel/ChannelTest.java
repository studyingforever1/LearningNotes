package com.zcq.channel;

import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ChannelTest {
    public static void main(String[] args) throws Exception {
        InetAddress byName = InetAddress.getByName("www.baidu.com");
        System.out.println(byName);
        NioSocketChannel nioSocketChannel = new NioSocketChannel();
        String ipv4 = byName.getHostAddress();
        nioSocketChannel.connect(new InetSocketAddress(ipv4, 80)).sync();
        nioSocketChannel.read();
        nioSocketChannel.close();
    }
}
