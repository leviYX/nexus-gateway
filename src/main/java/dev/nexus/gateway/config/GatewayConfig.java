package dev.nexus.gateway.config;

public record GatewayConfig(String host, int port) {
    public static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    public GatewayConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("gateway.host must not be blank");
        }
        // Port 0 asks the OS for an available port, useful for integration tests.
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("gateway.port must be between 0 and 65535");
        }
    }

    public static GatewayConfig load() {
        String host = System.getProperty("gateway.host",
                System.getenv().getOrDefault("GATEWAY_HOST", "0.0.0.0"));
        String port = System.getProperty("gateway.port",
                System.getenv().getOrDefault("GATEWAY_PORT", "8080"));
        try {
            return new GatewayConfig(host, Integer.parseInt(port));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("gateway.port must be an integer: " + port, e);
        }
    }
}
