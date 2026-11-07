package ru.mephi.stay.booking.reservation;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.mephi.stay.common.RoomView;
import static ru.mephi.stay.booking.reservation.BookingDto.*;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
class BookingController {
    private final BookingService service;
    private final BookingStore store;

    @PostMapping
    ResponseEntity<View> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create input) {
        var result = service.create(userId(jwt), input);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        if (result.booking().status() == BookingStatus.PENDING) status = HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result.booking());
    }

    @GetMapping
    History history(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "20") int size) {
        return store.history(userId(jwt), page, size);
    }

    @GetMapping("/{id}")
    View get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return store.get(id, userId(jwt)); }

    @DeleteMapping("/{id}")
    View cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.cancel(userId(jwt), id); }

    @GetMapping("/recommend")
    List<RoomView> recommend(@RequestParam(required = false) Long hotelId,
                             @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return service.recommendations(hotelId, startDate, endDate);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
