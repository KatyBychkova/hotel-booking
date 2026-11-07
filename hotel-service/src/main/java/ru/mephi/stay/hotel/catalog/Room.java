package ru.mephi.stay.hotel.catalog;

import jakarta.persistence.*;
import lombok.*;
import ru.mephi.stay.common.RoomView;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}),
        indexes = @Index(columnList = "available, times_booked, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;
    @Column(nullable = false, length = 30)
    private String number;
    @Column(nullable = false)
    private boolean available;
    @Column(name = "times_booked", nullable = false)
    private long timesBooked;

    Room(Hotel hotel, CatalogDto.RoomInput input) {
        this.hotel = hotel;
        update(input);
    }

    void update(CatalogDto.RoomInput input) {
        number = input.number().strip();
        available = input.available();
    }

    void reserved() { timesBooked++; }
    void released() { timesBooked--; }

    RoomView view() {
        return new RoomView(id, hotel.getId(), number, available, timesBooked);
    }
}
