package ru.mephi.stay.booking.reservation;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.*;
import ru.mephi.stay.booking.account.User;
import static ru.mephi.stay.booking.reservation.BookingDto.*;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}),
        indexes = {@Index(columnList = "user_id, created_at"), @Index(columnList = "release_pending")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Booking {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(nullable = false)
    private boolean autoSelect;
    private Long hotelId;
    private Long roomId;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private BookingStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(name = "release_pending", nullable = false)
    private boolean releasePending;

    Booking(User user, Create input) {
        this.user = user;
        requestId = input.requestId();
        autoSelect = input.autoSelect();
        hotelId = autoSelect ? input.hotelId() : null;
        roomId = autoSelect ? null : input.roomId();
        startDate = input.startDate();
        endDate = input.endDate();
        createdAt = Instant.now();
        status = BookingStatus.PENDING;
    }

    boolean matches(Create input) {
        return autoSelect == input.autoSelect() && startDate.equals(input.startDate()) && endDate.equals(input.endDate())
                && (autoSelect ? Objects.equals(hotelId, input.hotelId()) : Objects.equals(roomId, input.roomId()));
    }

    void choose(long id) { roomId = id; }
    void confirm() { status = BookingStatus.CONFIRMED; }
    void cancel(boolean release) {
        status = BookingStatus.CANCELLED;
        releasePending = release;
    }
    void released() { releasePending = false; }

    View view() { return new View(id, requestId, roomId, startDate, endDate, status, createdAt); }
}
