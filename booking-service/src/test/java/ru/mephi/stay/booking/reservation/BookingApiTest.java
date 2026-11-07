package ru.mephi.stay.booking.reservation;

import com.fasterxml.jackson.databind.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import ru.mephi.stay.booking.account.AccountDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingApiTest {
    static final HotelStub hotel = new HotelStub();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CircuitBreaker breaker;
    @Autowired Compensation compensation;
    @Autowired JwtDecoder decoder;
    String token;
    LocalDate start = LocalDate.now().plusDays(10);

    @DynamicPropertySource
    static void hotelAddress(DynamicPropertyRegistry properties) {
        properties.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", hotel::address);
    }

    @BeforeEach
    void setUp() throws Exception {
        hotel.reset();
        breaker.reset();
        token = register();
    }

    @AfterAll
    static void stopHotel() { hotel.close(); }

    @Test
    void confirmedBookingCanBeReadRepeatedAndCancelledByItsOwner() throws Exception {
        var input = command(false);
        var confirmed = body(create(input).andExpect(status().isCreated()));
        String id = confirmed.get("id").asText();
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(hotel.trace).isEqualTo("booking-api-test");
        var internal = decoder.decode(hotel.authorization.substring(7));
        assertThat(internal.getClaimAsString("role")).isEqualTo("INTERNAL");

        var replay = body(create(input).andExpect(status().isOk()));
        assertThat(replay.get("id").asText()).isEqualTo(id);
        assertThat(hotel.confirms.get()).isOne();
        var changed = new HashMap<>(input);
        changed.put("roomId", 2);
        create(changed).andExpect(status().isConflict());

        String another = register();
        mvc.perform(get("/api/bookings/" + id).header("Authorization", another)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", another)).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings").header("Authorization", another)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/bookings/" + id).header("Authorization", token)).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/bookings/" + id).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        assertThat(hotel.held).isEmpty();
        assertThat(hotel.releases.get()).isOne();
    }

    @Test
    void autoSelectIgnoresGivenRoomAndTriesTheNextCandidateAfterConflict() throws Exception {
        hotel.mode = HotelStub.Mode.CONFLICT_FIRST;
        var input = command(true);
        input.put("roomId", -99);
        create(input).andExpect(status().isCreated()).andExpect(jsonPath("$.roomId").value(2));
        assertThat(hotel.confirms.get()).isEqualTo(2);
        assertThat(hotel.releases.get()).isZero();
    }

    @Test
    void simultaneousDeliveriesOfOneRequestCreateOneBooking() throws Exception {
        var input = command(false);
        var pool = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<String>>();
            for (int i = 0; i < 6; i++) {
                results.add(pool.submit(() -> {
                    gate.await();
                    return body(create(input).andExpect(status().is2xxSuccessful())).get("id").asText();
                }));
            }
            gate.countDown();
            var ids = new HashSet<String>();
            for (var future : results) ids.add(future.get(15, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(hotel.confirms.get()).isOne();
            mvc.perform(get("/api/bookings?size=1").header("Authorization", token))
                    .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].status").value("CONFIRMED"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void serverErrorRetriesThreeTimesAndCompensates() throws Exception {
        hotel.mode = HotelStub.Mode.FAIL;
        create(command(false)).andExpect(status().isBadGateway()).andExpect(jsonPath("$.traceId").value("booking-api-test"));
        assertCancelledAfterRetries();
    }

    @Test
    void lostConfirmationResponseReleasesTheSlot() throws Exception {
        hotel.mode = HotelStub.Mode.TIMEOUT;
        create(command(false)).andExpect(status().isGatewayTimeout());
        assertCancelledAfterRetries();
        assertThat(hotel.held).isEmpty();
        assertThat(hotel.cancelled).hasSize(1);
    }

    @Test
    void unreachableReleaseIsRetriedFromTheDatabase() throws Exception {
        hotel.mode = HotelStub.Mode.FAIL;
        hotel.failRelease = true;
        create(command(false)).andExpect(status().isBadGateway());
        assertThat(hotel.releases.get()).isEqualTo(3);
        hotel.failRelease = false;
        compensation.retryPending();
        compensation.retryPending();
        assertThat(hotel.releases.get()).isEqualTo(4);
        assertThat(hotel.cancelled).hasSize(1);
    }

    @Test
    void openCircuitFailsFastAndStillAllowsCompensation() throws Exception {
        breaker.transitionToOpenState();
        create(command(false)).andExpect(status().isServiceUnavailable());
        assertThat(hotel.confirms.get()).isZero();
        assertThat(hotel.releases.get()).isOne();
    }

    @Test
    void invalidDatesAndPaginationReturn400() throws Exception {
        var input = command(false);
        input.put("endDate", start.toString());
        create(input).andExpect(status().isBadRequest());
        input.put("startDate", start.minusDays(15).toString());
        create(input).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings?size=101").header("Authorization", token)).andExpect(status().isBadRequest());
        assertThat(hotel.confirms.get()).isZero();
    }

    @Test
    void registrationIssuesOneHourUserTokenAndAdminCanManageAccounts() throws Exception {
        var jwt = decoder.decode(token.substring(7));
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(3600);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        mvc.perform(get("/api/bookings/user").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());

        var admin = "Bearer " + body(send("/user/auth", Map.of("username", "admin", "password", "test-admin-password"), null))
                .get("token").asText();
        var credentials = Map.of("username", "managed-user", "password", "new-password", "role", "USER");
        var user = body(send("/user", credentials, admin).andExpect(status().isCreated()));
        String path = "/api/bookings/user/" + user.get("id").asText();
        mvc.perform(patch(path).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"renamed-user\"}")).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", admin)).andExpect(jsonPath("$.username").value("renamed-user"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(delete(path).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization", admin)).andExpect(status().isNotFound());
        send("/user/auth", Map.of("username", "admin", "password", "wrong-password"), null).andExpect(status().isUnauthorized());
    }

    private void assertCancelledAfterRetries() throws Exception {
        mvc.perform(get("/api/bookings").header("Authorization", token))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED")).andExpect(jsonPath("$.total").value(1));
        assertThat(hotel.confirms.get()).isEqualTo(3);
        assertThat(hotel.releases.get()).isOne();
    }

    private String register() throws Exception {
        var input = new AccountDto.Credentials("user-" + UUID.randomUUID(), "guest-password");
        return "Bearer " + body(send("/user/register", input, null).andExpect(status().isCreated())).get("token").asText();
    }

    private Map<String, Object> command(boolean auto) {
        var input = new HashMap<String, Object>();
        input.put("requestId", UUID.randomUUID().toString());
        input.put("roomId", 1);
        input.put("autoSelect", auto);
        input.put("startDate", start.toString());
        input.put("endDate", start.plusDays(2).toString());
        return input;
    }

    private ResultActions create(Map<String, Object> input) throws Exception { return send("", input, token); }

    private ResultActions send(String path, Object input, String bearer) throws Exception {
        var request = post("/api/bookings" + path).header("X-Request-Id", "booking-api-test")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input));
        if (bearer != null) request.header("Authorization", bearer);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
