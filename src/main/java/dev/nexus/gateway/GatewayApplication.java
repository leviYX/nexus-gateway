package dev.nexus.gateway;

import dev.nexus.gateway.config.GatewayConfig;
import dev.nexus.gateway.server.GatewayServer;

public final class GatewayApplication {
    private GatewayApplication() {}

    public static void main(String[] args) throws InterruptedException {
        var config = GatewayConfig.load();
        var server = new GatewayServer(config);
        var shutdownHook = new Thread(server::close, "gateway-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try (server) {
            server.start();
            System.getLogger(GatewayApplication.class.getName()).log(System.Logger.Level.INFO,
                    "Nexus Gateway listening on " + config.host() + ":" + server.port());
            server.awaitClose();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown has already started; its hook owns cleanup.
            }
        }
    }
}
