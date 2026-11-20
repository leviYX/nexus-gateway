package dev.nexus.gateway.server;

import dev.nexus.gateway.config.GatewayConfig;
import dev.nexus.gateway.handler.HttpRequestHandler;
import dev.nexus.gateway.route.RouteRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

final class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
    private final RouteRegistry routes;
    private final ChannelGroup connections;

    HttpServerInitializer(RouteRegistry routes, ChannelGroup connections) {
        this.routes = routes;
        this.connections = connections;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        connections.add(channel);
        channel.pipeline()
                .addLast("httpCodec", new HttpServerCodec())
                // Aggregator sends 413 when a body exceeds the configured limit.
                .addLast("httpAggregator", new HttpObjectAggregator(GatewayConfig.MAX_CONTENT_LENGTH))
                .addLast("httpHandler", new HttpRequestHandler(routes));
    }
}
