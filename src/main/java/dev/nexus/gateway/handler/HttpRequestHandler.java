package dev.nexus.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nexus.gateway.route.RouteRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.util.Map;

/** FullHttpRequest is released by SimpleChannelInboundHandler after channelRead0. */
public final class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(HttpRequestHandler.class.getName());
    private final RouteRegistry routes;

    public HttpRequestHandler(RouteRegistry routes) {
        this.routes = routes;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            respond(ctx, request, HttpResponseStatus.BAD_REQUEST, Map.of("error", "Bad Request"), true);
            return;
        }
        String path;
        try {
            path = new QueryStringDecoder(request.uri()).path();
        } catch (IllegalArgumentException e) {
            respond(ctx, request, HttpResponseStatus.BAD_REQUEST, Map.of("error", "Bad Request"), true);
            return;
        }
        var route = routes.find(path);
        if (route == null) {
            respond(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Not Found"), false);
        } else if (!route.methods().contains(request.method().name())) {
            respond(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED,
                    Map.of("error", "Method Not Allowed"), false);
        } else {
            Object body = switch (route.id()) {
                case "health" -> Map.of("status", "UP");
                case "routes" -> Map.of("routes", routes.routes());
                default -> Map.of("name", "nexus-gateway", "message", "Netty HTTP server is running");
            };
            respond(ctx, request, HttpResponseStatus.OK, body, false);
        }
    }

    private void respond(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status,
                         Object body, boolean close) throws Exception {
        byte[] bytes = JSON.writeValueAsBytes(body);
        boolean head = HttpMethod.HEAD.equals(request.method());
        var response = new DefaultFullHttpResponse(request.protocolVersion(), status,
                head ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        HttpUtil.setContentLength(response, bytes.length);
        if (status.equals(HttpResponseStatus.METHOD_NOT_ALLOWED)) {
            response.headers().set(HttpHeaderNames.ALLOW, "GET, HEAD");
        }
        boolean keepAlive = !close && HttpUtil.isKeepAlive(request);
        response.headers().set(HttpHeaderNames.CONNECTION,
                keepAlive ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(System.Logger.Level.WARNING, "HTTP connection failed: " + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
