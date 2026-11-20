package dev.nexus.gateway.route;

import java.util.List;

/** Local endpoints only; upstream routing will be introduced with the proxy. */
public final class RouteRegistry {
    public record Route(String id, String path, List<String> methods, String type) {}

    private final List<Route> routes = List.of(
            new Route("home", "/", List.of("GET", "HEAD"), "local"),
            new Route("health", "/health", List.of("GET", "HEAD"), "local"),
            new Route("routes", "/gateway/routes", List.of("GET", "HEAD"), "local")
    );

    public List<Route> routes() {
        return routes;
    }

    public Route find(String path) {
        return routes.stream().filter(route -> route.path().equals(path)).findFirst().orElse(null);
    }
}
