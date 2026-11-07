package ru.mephi.stay.booking.account;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static ru.mephi.stay.booking.account.AccountDto.*;

@RestController
@RequestMapping("/api/bookings/user")
@RequiredArgsConstructor
class AccountController {
    private final AccountService accounts;

    @PostMapping("/register")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    Token register(@Valid @RequestBody Credentials input) { return accounts.register(input); }

    @PostMapping("/auth")
    @SecurityRequirements
    Token login(@Valid @RequestBody Credentials input) { return accounts.login(input); }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<View> list() { return accounts.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    View get(@PathVariable long id) { return accounts.get(id); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    View create(@Valid @RequestBody Create input) { return accounts.create(input); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    View update(@PathVariable long id, @Valid @RequestBody Update input) { return accounts.update(id, input); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { accounts.delete(id); }
}
