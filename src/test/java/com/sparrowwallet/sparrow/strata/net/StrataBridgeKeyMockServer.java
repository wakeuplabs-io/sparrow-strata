package com.sparrowwallet.sparrow.strata.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

class StrataBridgeKeyMockServer implements AutoCloseable {
    private final HttpServer server;
    private volatile String bridgePubkeyHex;

    StrataBridgeKeyMockServer(String bridgePubkeyHex) throws IOException {
        this.bridgePubkeyHex = bridgePubkeyHex;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new JsonRpcHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    String getUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    void setBridgePubkeyHex(String bridgePubkeyHex) {
        this.bridgePubkeyHex = bridgePubkeyHex;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private class JsonRpcHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String responseJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"bridge_pubkey\":\"" + bridgePubkeyHex + "\"}}";
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try(OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}

