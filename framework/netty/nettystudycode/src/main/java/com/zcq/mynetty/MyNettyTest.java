package com.zcq.mynetty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.FastThreadLocal;

import java.net.InetSocketAddress;
import java.util.List;

public class MyNettyTest {


    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        FastThreadLocal<byte[]> threadLocal = new FastThreadLocal<byte[]>();
        ChannelFuture bind = bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(8080))
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof NioSocketChannel){
                            NioSocketChannel channel = (NioSocketChannel) msg;
                            System.out.println("serverSocket接收到连接:" + channel.localAddress() + ":" + channel.remoteAddress());
                            System.out.println("serverSocket接收到数据:" + channel);
                        }
                        ctx.fireChannelRead(msg);
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }).childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        System.out.println("socket接收到连接:" + ctx.channel().localAddress() + ":" + ctx.channel().remoteAddress());
                        System.out.println("socket接收到数据:" + msg);
                        if (msg instanceof ByteBuf) {
                            ByteBuf buf = (ByteBuf) msg;
                            if (buf.isReadable()) {
                                byte[] bytes = new byte[buf.readableBytes()];
                                buf.readBytes(bytes);
                                if (threadLocal.get() == null) {
                                    threadLocal.set(bytes);
                                } else {
                                    byte[] bytes1 = threadLocal.get();
                                    int len = bytes1.length + bytes.length;
                                    byte[] bytes2 = new byte[len];
                                    System.arraycopy(bytes1, 0, bytes2, 0, bytes1.length);
                                    System.arraycopy(bytes, 0, bytes2, bytes1.length, bytes.length);
                                    threadLocal.set(bytes2);
                                }
                            }
                        }
                        ctx.fireChannelRead(msg);
                    }

                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                        byte[] bytes = threadLocal.get();
                        if (bytes != null){
                            System.out.println("channelReadComplete socket接收到数据:" + new String(bytes));
                        }
                        ctx.fireChannelReadComplete();
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }).bind();

        bind.channel().closeFuture().sync();
    }

}
