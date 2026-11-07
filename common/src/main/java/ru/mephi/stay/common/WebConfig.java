package ru.mephi.stay.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import javax.crypto.SecretKey;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
@Import(ApiErrors.class)
public class WebConfig {
    @Bean
    SecretKey signingKey(@Value("${app.jwt-secret}") String secret) {
        return JwtSupport.key(secret);
    }

    @Bean
    JwtDecoder decoder(SecretKey key) {
        return JwtSupport.decoder(key);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) -> sendError(mapper, req, res, 401, "Требуется действующий JWT");
        AccessDeniedHandler denied = (req, res, ex) -> sendError(mapper, req, res, 403, "Недостаточно прав");
        return http.csrf(cs -> cs.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/bookings/user/auth", "/api/bookings/user/register",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtSupport.roles()))
                        .authenticationEntryPoint(unauthorized).accessDeniedHandler(denied))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(denied))
                .build();
    }

    private void sendError(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
                           int status, String message) throws IOException {
        res.setStatus(status);
        if (status == 401) res.setHeader("WWW-Authenticate", "Bearer");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ErrorDto.of(status, message, req.getRequestURI(), MDC.get("traceId")));
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> traceFilter() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String traceId = TraceIds.from(req.getHeader(TraceIds.HEADER));
                try (var ignored = MDC.putCloseable("traceId", traceId)) {
                    res.setHeader(TraceIds.HEADER, traceId);
                    chain.doFilter(req, res);
                }
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    OpenAPI openApi(@Value("${spring.application.name}") String name) {
        var bearer = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
        return new OpenAPI().info(new Info().title(name).version("1.0"))
                .components(new Components().addSecuritySchemes("jwt", bearer))
                .addSecurityItem(new SecurityRequirement().addList("jwt"));
    }
}
