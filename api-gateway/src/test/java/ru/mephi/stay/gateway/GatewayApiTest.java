package ru.mephi.stay.gateway;

import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.mephi.stay.common.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayApiTest {
    static final AtomicReference<String> authorization = new AtomicReference<>();
    static final AtomicReference<String> trace = new AtomicReference<>();
    static final AtomicInteger visits = new AtomicInteger();
    static final HttpServer backend = backend();
    @LocalServerPort int port;
    @Value("${app.jwt-secret}") String secret;
    WebTestClient client;

    @BeforeEach
    void connect() { client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build(); }

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry properties) {
        for (String service : new String[]{"booking-service", "hotel-service"}) {
            properties.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    () -> "http://127.0.0.1:" + backend.getAddress().getPort());
        }
    }

    @AfterAll
    static void shutdown() { backend.stop(0); }

    @Test
    void routesToBothServicesAndPassesJwtAndTrace() {
        String bearer = "Bearer " + token("USER", Instant.now().plusSeconds(3600));
        for (String path : new String[]{"/api/hotels", "/api/rooms/recommend", "/api/bookings"}) {
            client.get().uri(path).header("Authorization", bearer).header(TraceIds.HEADER, "trace-test").exchange()
                    .expectStatus().isOk().expectHeader().values(TraceIds.HEADER,
                            values -> assertThat(values).containsExactly("trace-test"))
                    .expectBody(String.class).isEqualTo(path);
            assertThat(authorization.get()).isEqualTo(bearer);
            assertThat(trace.get()).isEqualTo("trace-test");
        }
    }

    @Test
    void invalidOrExpiredTokenAndInternalPathsNeverReachTheBackend() {
        int before = visits.get();
        client.get().uri("/api/hotels").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/hotels").header("Authorization", "Bearer broken").exchange().expectStatus().isUnauthorized();
        String expired = token("USER", Instant.now().minusSeconds(600));
        client.get().uri("/api/hotels").header("Authorization", "Bearer " + expired).exchange().expectStatus().isUnauthorized();
        String admin = token("ADMIN", Instant.now().plusSeconds(300));
        for (String action : new String[]{"release", "confirm-availability"}) {
            client.post().uri("/api/rooms/1/" + action).header("Authorization", "Bearer " + admin).exchange()
                    .expectStatus().isForbidden().expectBody().jsonPath("$.traceId").exists();
        }
        assertThat(visits.get()).isEqualTo(before);
    }

    @Test
    void registrationIsPublicAndReceivesATraceId() {
        client.post().uri("/api/bookings/user/register").exchange().expectStatus().isOk()
                .expectHeader().exists(TraceIds.HEADER);
        assertThat(trace.get()).isNotBlank();
    }

    private String token(String role, Instant expiry) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtSupport.key(secret)));
        var claims = JwtClaimsSet.builder().issuer(JwtSupport.ISSUER).subject("1").issuedAt(expiry.minusSeconds(3600))
                .expiresAt(expiry).claim("role", role).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static HttpServer backend() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    visits.incrementAndGet();
                    trace.set(exchange.getRequestHeaders().getFirst(TraceIds.HEADER));
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    exchange.getResponseHeaders().set(TraceIds.HEADER, trace.get());
                    byte[] path = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, path.length);
                    exchange.getResponseBody().write(path);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
