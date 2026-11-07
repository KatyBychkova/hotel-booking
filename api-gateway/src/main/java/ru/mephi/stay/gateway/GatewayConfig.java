package ru.mephi.stay.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import ru.mephi.stay.common.*;

@Configuration
public class GatewayConfig {
    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("bookings", r -> r.path("/api/bookings", "/api/bookings/**")
                        .filters(f -> f.dedupeResponseHeader(TraceIds.HEADER, "RETAIN_FIRST")).uri("lb://booking-service"))
                .route("hotels", r -> r.path("/api/hotels", "/api/hotels/{id}",
                        "/api/rooms", "/api/rooms/{id}")
                        .filters(f -> f.dedupeResponseHeader(TraceIds.HEADER, "RETAIN_FIRST")).uri("lb://hotel-service"))
                .build();
    }

    @Bean
    ReactiveJwtDecoder decoder(@Value("${app.jwt-secret}") String secret) {
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(JwtSupport.key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtSupport.ISSUER));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, ObjectMapper mapper) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.POST, "/api/bookings/user/auth", "/api/bookings/user/register").permitAll()
                        .pathMatchers("/api/rooms/*/confirm-availability", "/api/rooms/*/release").denyAll()
                        .anyExchange().hasAnyRole("USER", "ADMIN"))
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(JwtSupport.roles())))
                        .authenticationEntryPoint((exchange, ex) -> error(exchange, mapper, 401, "Требуется действующий JWT")))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((e, t) -> error(e, mapper, 401, "Требуется действующий JWT"))
                        .accessDeniedHandler((e, t) -> error(e, mapper, 403, "Недостаточно прав")))
                .build();
    }

    private Mono<Void> error(ServerWebExchange exchange, ObjectMapper mapper, int status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == 401) response.getHeaders().set("WWW-Authenticate", "Bearer");
        var body = ErrorDto.of(status, message, exchange.getRequest().getPath().value(),
                response.getHeaders().getFirst(TraceIds.HEADER));
        try {
            return response.writeWith(Mono.just(response.bufferFactory().wrap(mapper.writeValueAsBytes(body))));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }

    @Bean
    @Order(-2)
    WebExceptionHandler routingErrors(ObjectMapper mapper) {
        return (exchange, failure) -> {
            if (exchange.getResponse().isCommitted()) return Mono.error(failure);
            if (failure instanceof ResponseStatusException ex) {
                String message = ex.getStatusCode().value() == 404 ? "Маршрут не найден" : "Не удалось выполнить запрос";
                return error(exchange, mapper, ex.getStatusCode().value(), message);
            }
            if (failure instanceof java.io.IOException) {
                return error(exchange, mapper, 502, "Не удалось связаться с сервисом");
            }
            return Mono.error(failure);
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter traceRequests() {
        return (exchange, chain) -> {
            var request = exchange.getRequest();
            String traceId = TraceIds.from(request.getHeaders().getFirst(TraceIds.HEADER));
            exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);
            var traced = exchange.mutate().request(r -> r.headers(h -> h.set(TraceIds.HEADER, traceId))).build();
            return chain.filter(traced).doFinally(signal -> log.atInfo()
                    .addKeyValue("traceId", traceId).addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getPath().value())
                    .addKeyValue("status", exchange.getResponse().getStatusCode()).log("gateway.request"));
        };
    }
}
