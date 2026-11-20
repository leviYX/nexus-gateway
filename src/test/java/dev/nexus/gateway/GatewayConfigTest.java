package dev.nexus.gateway;

import dev.nexus.gateway.config.GatewayConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayConfigTest {
    @Test
    void systemPropertiesOverrideEnvironmentAndInvalidPortFails() {
        String originalHost = System.getProperty("gateway.host");
        String originalPort = System.getProperty("gateway.port");
        try {
            System.setProperty("gateway.host", "127.0.0.1");
            System.setProperty("gateway.port", "9090");
            assertEquals(new GatewayConfig("127.0.0.1", 9090), GatewayConfig.load());
            System.setProperty("gateway.port", "not-a-port");
            assertThrows(IllegalArgumentException.class, GatewayConfig::load);
        } finally {
            restore("gateway.host", originalHost);
            restore("gateway.port", originalPort);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
