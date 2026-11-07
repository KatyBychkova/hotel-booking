package ru.mephi.stay.booking.reservation;

import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.stay.booking.hotel.HotelClient;
import ru.mephi.stay.common.*;
import static ru.mephi.stay.booking.reservation.BookingDto.*;

@Service
@RequiredArgsConstructor
class BookingService {
    private final BookingStore store;
    private final HotelClient hotel;
    private final Compensation compensation;

    BookingStore.Start create(long userId, Create input) {
        var start = store.begin(userId, input);
        if (!start.created()) return start;
        View booking = start.booking();
        try (var ignored = MDC.putCloseable("bookingId", booking.id().toString())) {
            try {
                if (input.autoSelect()) {
                    confirmFirstFree(booking, input.hotelId());
                } else {
                    hotel.confirm(booking.roomId(), booking.slot());
                }
                return new BookingStore.Start(store.confirm(booking.id()), true);
            } catch (RuntimeException ex) {
                store.fail(booking.id(), true);
                compensation.release(booking.id());
                throw ex;
            }
        }
    }

    private void confirmFirstFree(View booking, Long hotelId) {
        var rooms = recommendations(hotelId, booking.startDate(), booking.endDate());
        for (RoomView room : rooms) {
            store.choose(booking.id(), room.id());
            try {
                hotel.confirm(room.id(), booking.slot());
                return;
            } catch (ResponseStatusException ex) {
                // Другой запрос успел взять этот номер. Проверим следующий из списка.
                if (ex.getStatusCode() != HttpStatus.CONFLICT) throw ex;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Свободных номеров на эти даты нет");
    }

    List<RoomView> recommendations(Long hotelId, LocalDate start, LocalDate end) {
        Dates.check(start, end);
        return hotel.recommend(hotelId, start, end);
    }

    View cancel(long userId, UUID id) {
        View cancelled = store.cancelOwn(id, userId);
        compensation.release(id);
        return cancelled;
    }
}
