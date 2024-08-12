package com.zcq.mynetty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class MyClientTest {
    public static void main(String[] args) throws Exception {
        testNettyClient();
//        Socket socket = new Socket();
//        socket.setTcpNoDelay(true);
//        socket.connect(new InetSocketAddress("127.0.0.1", 8080));
//        OutputStream outputStream = socket.getOutputStream();
//        outputStream.write(data.getBytes());
//        outputStream.flush();
//
//        LockSupport.park();
//
//        socket.close();

    }

    private static void testNIO() throws Exception {
        SocketChannel socketChannel = SocketChannel.open();

        socketChannel.connect(new InetSocketAddress("127.0.0.1", 8080));
        socketChannel.socket().setKeepAlive(true);
        socketChannel.socket().setTcpNoDelay(true);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.getBytes().length);
        byteBuffer.put(data.getBytes());
        byteBuffer.flip();
        while (byteBuffer.hasRemaining()) {
            socketChannel.write(byteBuffer);
        }
        socketChannel.close();
    }

    private static void testNettyClient() throws Exception {
        NioEventLoopGroup eventExecutors = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();

        Channel channel = bootstrap.group(eventExecutors)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new MyClientHandler());
                    }
                }).connect("127.0.0.1", 8080).sync().channel();
        ByteBuf buf = channel.alloc().buffer(data.length());
        buf.writeBytes(data.getBytes(), 0, buf.capacity() / 2);
        channel.writeAndFlush(buf);
        buf.writeBytes(data.getBytes(), buf.capacity() / 2, buf.capacity() / 2);
        channel.writeAndFlush(buf);

        channel.closeFuture().sync();
    }

    static StringBuilder bytes = new StringBuilder();

    static {
        for (int i = 0; i < 10000; i++) {
            bytes.append("11111");
        }
    }

    static String data = "GET /xxl-job-admin/jobinfo HTTP/1.1\n" +
            "Host: localhost:8080\n" +
            "Connection: keep-alive\n" +
            "Cache-Control: max-age=0\n" +
            "sec-ch-ua: \"Not)A;Brand\";v=\"99\", \"Google Chrome\";v=\"127\", \"Chromium\";v=\"127\"\n" +
            "sec-ch-ua-mobile: ?0\n" +
            "sec-ch-ua-platform: \"Windows\"\n" +
            "Upgrade-Insecure-Requests: 1\n" +
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36\n" +
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\n" +
            "Sec-Fetch-Site: cross-site\n" +
            "Sec-Fetch-Mode: navigate\n" +
            "Sec-Fetch-User: ?1\n" +
            "Sec-Fetch-Dest: document\n" +
            "Accept-Encoding: gzip, deflate, br, zstd\n" +
            "Accept-Language: zh-CN,zh;q=0.9\n" +
            "Cookie: SELLER=xy05156; NG_TRANSLATE_LANG_KEY=%22zh%22; XXL_JOB_LOGIN_IDENTITY=7b226964223a312c22757365726e616d65223a2261646d696e222c2270617373776f7264223a226338663734326338396638303364363434376263656136353139343961626665222c22726f6c65223a312c227065726d697373696f6e223a6e756c6c7d; JSESSIONID=51BD8FFE9C611EC654F41F2854A72D41; SESSION=YjM0ZjcwNDgtZmMwNi00ZDllLTk1NDUtZTE5Mjg1NzZjMTdi\n" +
            "\n"
            + bytes.toString();
}

class MyClientHandler extends ChannelInboundHandlerAdapter {
    static StringBuilder bytes = new StringBuilder();

    static {
        for (int i = 0; i < 10000; i++) {
            bytes.append("11111");
        }
    }

    static String data = "GET /xxl-job-admin/jobinfo HTTP/1.1\n" +
            "Host: localhost:8080\n" +
            "Connection: keep-alive\n" +
            "Cache-Control: max-age=0\n" +
            "sec-ch-ua: \"Not)A;Brand\";v=\"99\", \"Google Chrome\";v=\"127\", \"Chromium\";v=\"127\"\n" +
            "sec-ch-ua-mobile: ?0\n" +
            "sec-ch-ua-platform: \"Windows\"\n" +
            "Upgrade-Insecure-Requests: 1\n" +
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36\n" +
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\n" +
            "Sec-Fetch-Site: cross-site\n" +
            "Sec-Fetch-Mode: navigate\n" +
            "Sec-Fetch-User: ?1\n" +
            "Sec-Fetch-Dest: document\n" +
            "Accept-Encoding: gzip, deflate, br, zstd\n" +
            "Accept-Language: zh-CN,zh;q=0.9\n" +
            "Cookie: SELLER=xy05156; NG_TRANSLATE_LANG_KEY=%22zh%22; XXL_JOB_LOGIN_IDENTITY=7b226964223a312c22757365726e616d65223a2261646d696e222c2270617373776f7264223a226338663734326338396638303364363434376263656136353139343961626665222c22726f6c65223a312c227065726d697373696f6e223a6e756c6c7d; JSESSIONID=51BD8FFE9C611EC654F41F2854A72D41; SESSION=YjM0ZjcwNDgtZmMwNi00ZDllLTk1NDUtZTE5Mjg1NzZjMTdi\n" +
            "\n"
            + bytes.toString();

}
