package dev.nexus.gateway.server;

import dev.nexus.gateway.config.GatewayConfig;
import dev.nexus.gateway.route.RouteRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** Lifecycle methods belong on the application thread, never an event loop. */
public final class GatewayServer implements AutoCloseable {
    private final GatewayConfig config;
    private final DefaultChannelGroup connections = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true);
    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel channel;
    private boolean closed;

    public GatewayServer(GatewayConfig config) {
        this.config = config;
    }

    public synchronized void start() throws InterruptedException {
        if (closed || boss != null) throw new IllegalStateException("Server can only be started once");
        boolean started = false;
        try {
            boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            workers = new MultiThreadIoEventLoopGroup(0, NioIoHandler.newFactory());
            channel = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new HttpServerInitializer(new RouteRegistry(), connections))
                    .bind(config.host(), config.port()).sync().channel();
            started = true;
        } finally {
            // Netty futures may rethrow checked bind failures without declaring them.
            if (!started) close();
        }
    }

    public synchronized int port() {
        if (channel == null) throw new IllegalStateException("Server has not started");
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    public void awaitClose() throws InterruptedException {
        Channel current;
        synchronized (this) {
            if (channel == null) throw new IllegalStateException("Server has not started");
            current = channel;
        }
        current.closeFuture().sync();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (channel != null) channel.close().awaitUninterruptibly();
        connections.close().awaitUninterruptibly();
        var bossShutdown = boss == null ? null : boss.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        var workerShutdown = workers == null ? null : workers.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        if (bossShutdown != null) bossShutdown.awaitUninterruptibly();
        if (workerShutdown != null) workerShutdown.awaitUninterruptibly();
    }
}
