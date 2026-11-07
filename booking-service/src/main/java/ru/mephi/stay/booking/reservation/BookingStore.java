package ru.mephi.stay.booking.reservation;

import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.stay.booking.account.*;
import ru.mephi.stay.common.Dates;
import static ru.mephi.stay.booking.reservation.BookingDto.*;

@Service
@RequiredArgsConstructor
@Transactional
class BookingStore {
    record Start(View booking, boolean created) {}
    private static final Logger log = LoggerFactory.getLogger(BookingStore.class);
    private final UserRepository users;
    private final BookingRepository bookings;

    Start begin(long userId, Create input) {
        // Блокировка действует только до записи PENDING. Сетевых вызовов в этой транзакции нет.
        User user = users.lock(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Аккаунт удалён"));
        var previous = bookings.findByUserIdAndRequestId(userId, input.requestId());
        if (previous.isPresent()) {
            Booking booking = previous.get();
            if (!booking.matches(input)) throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId уже использован с другими данными");
            return new Start(booking.view(), false);
        }
        Dates.check(input.startDate(), input.endDate());
        if (input.startDate().isBefore(LocalDate.now())) throw bad("Дата заезда уже прошла");
        if (!input.autoSelect() && (input.roomId() == null || input.roomId() < 1)) throw bad("Укажите положительный roomId");
        Booking booking = bookings.saveAndFlush(new Booking(user, input));
        logState(booking);
        return new Start(booking.view(), true);
    }

    View choose(UUID id, long roomId) {
        Booking booking = locked(id);
        booking.choose(roomId);
        return booking.view();
    }

    View confirm(UUID id) {
        Booking booking = locked(id);
        booking.confirm();
        logState(booking);
        return booking.view();
    }

    View fail(UUID id, boolean release) {
        return cancel(locked(id), release);
    }

    View cancelOwn(UUID id, long userId) {
        Booking booking = locked(id);
        if (booking.getUser().getId() != userId) throw missing();
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Дождитесь завершения подтверждения");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) return booking.view();
        return cancel(booking, true);
    }

    private View cancel(Booking booking, boolean release) {
        booking.cancel(release && booking.getRoomId() != null);
        logState(booking);
        return booking.view();
    }

    @Transactional(readOnly = true)
    List<UUID> awaitingRelease() { return bookings.awaitingRelease(); }

    @Transactional(readOnly = true)
    Optional<View> releaseTask(UUID id) {
        return bookings.findById(id).filter(Booking::isReleasePending).map(Booking::view);
    }

    void released(UUID id) { locked(id).released(); }

    @Transactional(readOnly = true)
    View get(UUID id, long userId) {
        return bookings.findByIdAndUserId(id, userId).orElseThrow(this::missing).view();
    }

    @Transactional(readOnly = true)
    History history(long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("page >= 0, size от 1 до 100");
        var order = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var result = bookings.findByUserId(userId, PageRequest.of(page, size, order));
        return new History(result.map(Booking::view).getContent(), page, size, result.getTotalElements());
    }

    private Booking locked(UUID id) { return bookings.lock(id).orElseThrow(this::missing); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Бронь не найдена"); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private void logState(Booking booking) {
        log.atInfo().addKeyValue("bookingId", booking.getId()).addKeyValue("requestId", booking.getRequestId())
                .addKeyValue("status", booking.getStatus()).log("booking.state");
    }
}
