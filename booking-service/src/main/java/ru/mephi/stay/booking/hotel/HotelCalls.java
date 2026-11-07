package ru.mephi.stay.booking.hotel;

import io.github.resilience4j.circuitbreaker.*;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
class HotelCalls {
    private static final Logger log = LoggerFactory.getLogger(HotelCalls.class);
    private final CircuitBreaker breaker;
    private final Duration delay;

    HotelCalls(CircuitBreaker breaker, @Value("${app.retry-delay}") Duration delay) {
        this.delay = delay;
        this.breaker = breaker;
    }

    <T> T request(String operation, boolean release, Supplier<T> call) {
        try {
            // Даже при открытой цепи пробуем снять уже взятую блокировку.
            return release ? retry(operation, call) : breaker.executeSupplier(() -> retry(operation, call));
        } catch (CallNotPermittedException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel Service временно недоступен", ex);
        }
    }

    private <T> T retry(String operation, Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RestClientException ex) {
                if (ex instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()) {
                    throw new ResponseStatusException(response.getStatusCode(), "Hotel Service отклонил операцию " + operation, ex);
                }
                log.atWarn().addKeyValue("operation", operation).addKeyValue("attempt", attempt)
                        .addKeyValue("reason", ex.getClass().getSimpleName()).log("hotel.call.failed");
                if (attempt == 3) {
                    boolean timeout = ex.getMostSpecificCause() instanceof SocketTimeoutException;
                    var status = timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
                    throw new ResponseStatusException(status, "Не получено подтверждение от Hotel Service", ex);
                }
                try {
                    Thread.sleep(delay.multipliedBy(1L << (attempt - 1)).toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Запрос прерван", interrupted);
                }
            }
        }
    }
}
