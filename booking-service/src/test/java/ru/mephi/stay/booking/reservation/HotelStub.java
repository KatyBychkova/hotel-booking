package ru.mephi.stay.booking.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** HTTP-заглушка позволяет задержать ответ уже после записи слота. */
class HotelStub implements AutoCloseable {
    enum Mode { OK, FAIL, TIMEOUT, CONFLICT_FIRST }
    final AtomicInteger confirms = new AtomicInteger();
    final AtomicInteger releases = new AtomicInteger();
    final Set<UUID> held = ConcurrentHashMap.newKeySet();
    final Set<UUID> cancelled = ConcurrentHashMap.newKeySet();
    volatile Mode mode = Mode.OK;
    volatile boolean failRelease;
    volatile String trace;
    volatile String authorization;
    private final HttpServer server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ObjectMapper json = new ObjectMapper();

    HotelStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/api/rooms/", this::handle);
        server.setExecutor(pool);
        server.start();
    }

    String address() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    void reset() {
        confirms.set(0);
        releases.set(0);
        held.clear();
        cancelled.clear();
        mode = Mode.OK;
        failRelease = false;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            trace = exchange.getRequestHeaders().getFirst("X-Request-Id");
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/recommend")) {
                byte[] rooms = """
                        [{"id":1,"hotelId":1,"number":"101","available":true,"timesBooked":0},
                         {"id":2,"hotelId":1,"number":"102","available":true,"timesBooked":1}]
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, rooms.length);
                exchange.getResponseBody().write(rooms);
                return;
            }
            var command = json.readTree(exchange.getRequestBody());
            UUID id = UUID.fromString(command.get("bookingId").asText());
            if (path.endsWith("/release")) {
                releases.incrementAndGet();
                if (failRelease) {
                    exchange.sendResponseHeaders(503, -1);
                } else {
                    cancelled.add(id);
                    held.remove(id);
                    exchange.sendResponseHeaders(204, -1);
                }
                return;
            }
            confirms.incrementAndGet();
            if (mode == Mode.FAIL) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            if (cancelled.contains(id) || (mode == Mode.CONFLICT_FIRST && path.contains("/1/"))) {
                exchange.sendResponseHeaders(409, -1);
                return;
            }
            held.add(id);
            if (mode == Mode.TIMEOUT) {
                try { Thread.sleep(1200); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            exchange.sendResponseHeaders(204, -1);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdownNow();
    }
}
