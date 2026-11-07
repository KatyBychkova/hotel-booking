package ru.mephi.stay.hotel.catalog;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import ru.mephi.stay.common.SlotCommand;

@Entity
@Table(name = "bookings", indexes = @Index(columnList = "room_id, released, start_date, end_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RoomSlot {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Column(nullable = false)
    private boolean released;

    RoomSlot(Room room, SlotCommand cmd, boolean released) {
        this.id = cmd.bookingId();
        this.room = room;
        this.startDate = cmd.startDate();
        this.endDate = cmd.endDate();
        this.released = released;
    }

    boolean matches(long roomId, SlotCommand cmd) {
        return room.getId() == roomId && startDate.equals(cmd.startDate()) && endDate.equals(cmd.endDate());
    }

    void release() { released = true; }
}
