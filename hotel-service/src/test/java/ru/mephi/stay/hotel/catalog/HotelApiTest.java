package ru.mephi.stay.hotel.catalog;

import com.fasterxml.jackson.databind.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import ru.mephi.stay.common.SlotCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HotelApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    long hotelId;
    long roomId;
    LocalDate start = LocalDate.now().plusDays(10);

    @BeforeEach
    void prepareRoom() throws Exception {
        hotelId = create("/api/hotels", Map.of("name", "Тест", "address", "Москва"));
        roomId = addRoom("101", true);
    }

    @Test
    void onlyOneOfEightOverlappingRequestsGetsTheRoom() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var gate = new CountDownLatch(1);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    gate.await();
                    return slot("confirm-availability", command(), "INTERNAL").andReturn().getResponse().getStatus();
                }));
            }
            gate.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : results) statuses.add(future.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(204, 409);
            assertThat(Collections.frequency(statuses, 204)).isEqualTo(1);
            assertThat(rooms("/stats", "ADMIN").get(0).get("timesBooked").asLong()).isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void repeatConfirmAndReleaseAreSafeEvenWhenReleaseArrivesFirst() throws Exception {
        var booking = command();
        for (int i = 0; i < 2; i++) slot("confirm-availability", booking, "INTERNAL").andExpect(status().isNoContent());
        assertThat(rooms("/stats", "ADMIN").get(0).get("occupiedNights").asLong()).isEqualTo(2);
        assertThat(rooms("/stats", "ADMIN").get(0).get("timesBooked").asLong()).isOne();

        for (int i = 0; i < 2; i++) slot("release", booking, "INTERNAL").andExpect(status().isNoContent());
        assertThat(rooms("/stats", "ADMIN").get(0).get("timesBooked").asLong()).isZero();

        var late = command();
        slot("release", late, "INTERNAL").andExpect(status().isNoContent());
        slot("confirm-availability", late, "INTERNAL").andExpect(status().isConflict());
        assertThat(rooms("", "USER")).hasSize(1);
    }

    @Test
    void rankingUsesCountThenIdAndChecksHalfOpenDates() throws Exception {
        long other = addRoom("102", true);
        addRoom("103", false);
        assertThat(rooms("/recommend", "USER").get(0).get("id").asLong()).isEqualTo(roomId);
        var previous = new SlotCommand(UUID.randomUUID(), start.minusDays(2), start);
        slot("confirm-availability", previous, "INTERNAL").andExpect(status().isNoContent());
        var ranked = rooms("/recommend", "USER");
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).get("id").asLong()).isEqualTo(other);
        assertThat(ranked.get(1).get("id").asLong()).isEqualTo(roomId);

        slot("confirm-availability", command(), "INTERNAL").andExpect(status().isNoContent());
        assertThat(rooms("/recommend", "USER")).hasSize(1);
        var stats = rooms("/stats", "ADMIN");
        assertThat(stats.get(0).get("occupancyPercent").asDouble()).isEqualTo(100.0);
        assertThat(stats.get(1).get("occupancyPercent").asDouble()).isZero();
    }

    @Test
    void rolesProtectCatalogAndInternalRoutes() throws Exception {
        mvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.traceId").isString());
        mvc.perform(post("/api/hotels").with(role("USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Отель\",\"address\":\"Москва\"}")).andExpect(status().isForbidden());
        slot("release", command(), "ADMIN").andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms/stats").with(role("USER")).param("startDate", start.toString())
                .param("endDate", start.plusDays(2).toString())).andExpect(status().isForbidden());
        mvc.perform(post("/api/hotels").with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void adminUpdatesAndRemovesCatalogEntries() throws Exception {
        mvc.perform(put("/api/rooms/{id}", roomId).with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("hotelId", hotelId, "number", "202", "available", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        assertThat(rooms("", "USER")).isEmpty();
        mvc.perform(put("/api/hotels/{id}", hotelId).with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Нева\",\"address\":\"Набережная\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/hotels/{id}", hotelId).with(role("ADMIN"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/rooms/{id}", roomId).with(role("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/hotels/{id}", hotelId).with(role("ADMIN"))).andExpect(status().isNoContent());
    }

    private long addRoom(String number, boolean available) throws Exception {
        return create("/api/rooms", Map.of("hotelId", hotelId, "number", number, "available", available));
    }

    private long create(String path, Object body) throws Exception {
        var res = mvc.perform(post(path).with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(res.getContentAsByteArray()).get("id").asLong();
    }

    private JsonNode rooms(String suffix, String role) throws Exception {
        var response = mvc.perform(get("/api/rooms" + suffix).with(role(role)).param("hotelId", Long.toString(hotelId))
                        .param("startDate", start.toString()).param("endDate", start.plusDays(2).toString()))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    private ResultActions slot(String action, SlotCommand booking, String role) throws Exception {
        return mvc.perform(post("/api/rooms/{id}/{action}", roomId, action).with(role(role))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(booking)));
    }

    private SlotCommand command() { return new SlotCommand(UUID.randomUUID(), start, start.plusDays(2)); }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String name) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + name));
    }
}
