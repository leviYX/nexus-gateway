package dev.nexus.gateway;

import dev.nexus.gateway.config.GatewayConfig;
import dev.nexus.gateway.server.GatewayServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GatewayServerTest {
    private GatewayServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void start() throws Exception {
        server = new GatewayServer(new GatewayConfig("127.0.0.1", 0));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.port();
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterEach
    void stop() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesHomeAndHealthWithQueryParameters() throws Exception {
        assertEquals(200, request("GET", "/", "").statusCode());
        var response = request("GET", "/health?probe=ready", "");
        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"UP\"}", response.body());
        assertEquals("application/json; charset=UTF-8", response.headers().firstValue("content-type").orElseThrow());
    }

    @Test
    void listsLocalRoutes() throws Exception {
        var response = request("GET", "/gateway/routes", "");
        assertEquals(200, response.statusCode());
        var routes = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).get("routes");
        assertEquals(3, routes.size());
        assertEquals("/health", routes.get(1).get("path").asText());
        assertEquals("local", routes.get(1).get("type").asText());
    }

    @Test
    void returnsNotFound() throws Exception {
        assertEquals(404, request("GET", "/missing", "").statusCode());
    }

    @Test
    void rejectsUnsupportedMethods() throws Exception {
        var response = request("POST", "/health", "hello");
        assertEquals(405, response.statusCode());
        assertEquals("GET, HEAD", response.headers().firstValue("allow").orElseThrow());
    }

    @Test
    void supportsHeadWithoutResponseBody() throws Exception {
        var get = request("GET", "/health", "");
        var head = request("HEAD", "/health", "");
        assertEquals(200, head.statusCode());
        assertEquals("", head.body());
        assertEquals(get.headers().firstValue("content-length"), head.headers().firstValue("content-length"));
    }

    @Test
    void rejectsOversizedBodies() throws Exception {
        assertEquals(413, request("POST", "/health", "x".repeat(1024 * 1024 + 1)).statusCode());
    }

    @Test
    void supportsRepeatedRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            var response = request("GET", "/health", "");
            assertEquals(200, response.statusCode());
            assertEquals("keep-alive", response.headers().firstValue("connection").orElseThrow());
        }
    }

    @Test
    void releasesListeningPortAndCanCloseTwice() throws Exception {
        int port = server.port();
        server.close();
        server.close();
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }

    @Test
    void failsWhenPortIsOccupied() {
        try (var duplicate = new GatewayServer(new GatewayConfig("127.0.0.1", server.port()))) {
            assertThrows(java.net.BindException.class, duplicate::start);
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayConfig("", 8080));
        assertThrows(IllegalArgumentException.class, () -> new GatewayConfig("localhost", -1));
        assertThrows(IllegalArgumentException.class, () -> new GatewayConfig("localhost", 65536));
    }
}
