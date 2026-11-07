package ru.mephi.stay.hotel.catalog;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mephi.stay.common.*;
import static ru.mephi.stay.hotel.catalog.CatalogDto.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
class RoomController {
    private final CatalogService catalog;
    private final SlotService slots;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    List<RoomView> free(@RequestParam(required = false) Long hotelId,
                        @RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate) {
        return catalog.free(hotelId, startDate, endDate, false);
    }

    @GetMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER','ADMIN','INTERNAL')")
    List<RoomView> recommend(@RequestParam(required = false) Long hotelId,
                             @RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate) {
        return catalog.free(hotelId, startDate, endDate, true);
    }

    @GetMapping("/stats")
    List<RoomStats> stats(@RequestParam(required = false) Long hotelId,
                          @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return catalog.stats(hotelId, startDate, endDate);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    RoomView get(@PathVariable long id) { return catalog.room(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoomView create(@Valid @RequestBody RoomInput input) { return catalog.createRoom(input); }

    @PutMapping("/{id}")
    RoomView update(@PathVariable long id, @Valid @RequestBody RoomInput input) { return catalog.updateRoom(id, input); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { catalog.deleteRoom(id); }

    @PostMapping("/{id}/confirm-availability")
    @PreAuthorize("hasRole('INTERNAL')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@PathVariable long id, @Valid @RequestBody SlotCommand cmd) { slots.confirm(id, cmd); }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasRole('INTERNAL')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable long id, @Valid @RequestBody SlotCommand cmd) { slots.release(id, cmd); }
}
