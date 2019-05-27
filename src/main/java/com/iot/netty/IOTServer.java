package com.iot.netty;

import com.iot.config.BackendConfig;
import com.iot.config.NettyConfig;
import com.iot.config.IOTConfig;
import com.iot.netty.handler.EchoServerHandler;
import com.iot.netty.handler.IOTServerHandler;
import com.iot.netty.handler.MqReceiver;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class IOTServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IOTServer.class);

    @Resource
    private NettyConfig nettyConfig;

    @Autowired
    private BackendConfig backendConfig;

    @Autowired
    private StringRedisTemplate redisTemplate;

    ServerBootstrap b = new ServerBootstrap();

    EventLoopGroup bossGroup = new NioEventLoopGroup();        // 用来接收进来的连接
    EventLoopGroup workerGroup = new NioEventLoopGroup();    // 用来处理已经被接收的连接


    /**
     * 关闭服务器方法
     */
    @PreDestroy
    public void close() {
        LOGGER.info("关闭服务器....");
        // 优雅退出
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Bean
    public IOTServerHandler getIotServerHandler() {
        IOTServerHandler iotServerHandler = new IOTServerHandler(redisTemplate);
        iotServerHandler.setBackendConfig(backendConfig);
        return iotServerHandler;
    }

    @PostConstruct
    public void start() throws Exception {
        int port = nettyConfig.getPort();

        System.out.println("准备运行端口：" + port);

        try {

            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)            // 这里告诉Channel如何接收新的连接
                    .childHandler( new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast("idleStateHandler",
                                    new IdleStateHandler(IOTConfig.tcp_client_idle_minutes, 0, 0, TimeUnit.MINUTES));
                            //数据上报
                            ch.pipeline().addLast(getIotServerHandler());
                            ch.pipeline().addLast(new EchoServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            MqReceiver mqReceiver = new MqReceiver();
            mqReceiver.start(redisTemplate);

            LOGGER.info("netty服务器在[{}]端口启动监听", port);
            // 绑定端口，开始接收进来的连接
            ChannelFuture f = b.bind(port).sync();

            // 等待服务器socket关闭
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            LOGGER.error("[出现异常] 释放资源");
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
