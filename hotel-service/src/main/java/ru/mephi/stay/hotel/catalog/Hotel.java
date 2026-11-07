package ru.mephi.stay.hotel.catalog;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 240)
    private String address;

    Hotel(CatalogDto.HotelInput input) {
        update(input);
    }

    void update(CatalogDto.HotelInput input) {
        name = input.name().strip();
        address = input.address().strip();
    }
}
