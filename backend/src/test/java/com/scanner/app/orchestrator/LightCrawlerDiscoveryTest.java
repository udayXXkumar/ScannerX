package com.scanner.app.orchestrator;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LightCrawlerDiscoveryTest {

    @Test
    void discoversSameOriginLinksFormsQueryParametersAndJsonEndpoints() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<html><a href=\"/api/items?search=abc\">items</a><form action=\"/login\"></form></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/items", exchange -> {
            byte[] body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            StepExecutionResult result = new LightCrawlerService().crawl(target, 2, 20);

            assertTrue(result.discoveredUrls().stream().anyMatch(url -> url.endsWith("/api/items?search=abc")));
            assertTrue(result.forms().stream().anyMatch(url -> url.endsWith("/login")));
            assertTrue(result.queryParameters().contains("search"));
            assertTrue(result.jsonEndpoints().stream().anyMatch(url -> url.endsWith("/api/items?search=abc")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void discoversApiRoutesReferencedBySameOriginJavascriptBundles() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<html><script src=\"/assets/app.js\"></script></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/assets/app.js", exchange -> {
            byte[] body = "const search = '/rest/products/search?q=';".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/javascript");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            StepExecutionResult result = new LightCrawlerService().crawl(target, 2, 20);

            assertTrue(result.discoveredUrls().stream().anyMatch(url -> url.contains("/rest/products/search?q=")));
            assertTrue(result.queryParameters().contains("q"));
        } finally {
            server.stop(0);
        }
    }
}
