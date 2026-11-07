package ru.mephi.stay.hotel.catalog;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static ru.mephi.stay.hotel.catalog.CatalogDto.*;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
class HotelController {
    private final CatalogService catalog;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    List<HotelView> list() { return catalog.hotels(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    HotelView get(@PathVariable long id) { return catalog.hotel(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    HotelView create(@Valid @RequestBody HotelInput input) { return catalog.createHotel(input); }

    @PutMapping("/{id}")
    HotelView update(@PathVariable long id, @Valid @RequestBody HotelInput input) {
        return catalog.updateHotel(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { catalog.deleteHotel(id); }
}
