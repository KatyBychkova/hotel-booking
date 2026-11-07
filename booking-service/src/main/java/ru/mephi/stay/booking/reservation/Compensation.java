package ru.mephi.stay.booking.reservation;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.stay.booking.hotel.HotelClient;
import static ru.mephi.stay.booking.reservation.BookingDto.View;

@Service
@RequiredArgsConstructor
class Compensation {
    private static final Logger log = LoggerFactory.getLogger(Compensation.class);
    private final BookingStore store;
    private final HotelClient hotel;

    void release(UUID id) {
        store.releaseTask(id).ifPresent(this::send);
    }

    private void send(View booking) {
        try {
            hotel.release(booking.roomId(), booking.slot());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                log.atWarn().addKeyValue("bookingId", booking.id()).addKeyValue("status", ex.getStatusCode().value())
                        .log("booking.release.deferred");
                return;
            }
        }
        store.released(booking.id());
        log.atInfo().addKeyValue("bookingId", booking.id()).log("booking.release.completed");
    }

    @Scheduled(fixedDelayString = "${app.release-interval:15000}")
    void retryPending() {
        for (UUID id : store.awaitingRelease()) {
            try (var ignored = MDC.putCloseable("traceId", id.toString())) {
                release(id);
            }
        }
    }
}
