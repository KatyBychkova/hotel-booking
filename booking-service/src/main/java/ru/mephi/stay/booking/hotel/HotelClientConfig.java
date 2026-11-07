package ru.mephi.stay.booking.hotel;

import java.time.Duration;
import io.github.resilience4j.circuitbreaker.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class HotelClientConfig {
    @Bean
    CircuitBreaker hotelBreaker() {
        var config = CircuitBreakerConfig.custom().slidingWindowSize(10).minimumNumberOfCalls(5)
                .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(10))
                .ignoreException(ex -> ex instanceof ResponseStatusException r && r.getStatusCode().is4xxClientError())
                .build();
        return CircuitBreaker.of("hotel-service", config);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder hotelBuilder(@Value("${app.connect-timeout}") Duration connect,
                                    @Value("${app.read-timeout}") Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory);
    }
}
