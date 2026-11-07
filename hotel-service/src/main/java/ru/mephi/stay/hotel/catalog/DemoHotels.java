package ru.mephi.stay.hotel.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static ru.mephi.stay.hotel.catalog.CatalogDto.*;

@Component
@RequiredArgsConstructor
class DemoHotels implements CommandLineRunner {
    private final CatalogService catalog;

    @Override
    @Transactional
    public void run(String... args) {
        var first = catalog.createHotel(new HotelInput("Орбита", "Москва, ул. Академическая, 7"));
        var second = catalog.createHotel(new HotelInput("Нева", "Санкт-Петербург, наб. Реки Фонтанки, 18"));
        catalog.createRoom(new RoomInput(first.id(), "101", true));
        catalog.createRoom(new RoomInput(first.id(), "102", true));
        catalog.createRoom(new RoomInput(first.id(), "103", false));
        catalog.createRoom(new RoomInput(second.id(), "201", true));
    }
}
