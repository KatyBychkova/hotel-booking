package ru.mephi.stay.booking.account;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static ru.mephi.stay.booking.account.AccountDto.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class AccountService {
    private final UserRepository users;
    private final TokenService tokens;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    @Transactional
    Token register(Credentials input) {
        return tokens.forUser(save(input.username(), input.password(), Role.USER));
    }

    Token login(Credentials input) {
        var user = users.findByUsername(input.username()).filter(u -> passwords.matches(input.password(), u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"));
        return tokens.forUser(user);
    }

    List<View> list() { return users.findAll().stream().map(View::of).toList(); }
    View get(long id) { return View.of(users.findById(id).orElseThrow(this::missing)); }

    @Transactional
    View create(Create input) { return View.of(save(input.username(), input.password(), input.role())); }

    @Transactional
    View update(long id, Update input) {
        User user = users.lock(id).orElseThrow(this::missing);
        user.update(input, input.password() == null ? null : hash(input.password()));
        return View.of(users.saveAndFlush(user));
    }

    @Transactional
    void delete(long id) { users.delete(users.lock(id).orElseThrow(this::missing)); }

    private User save(String username, String password, Role role) {
        return users.saveAndFlush(new User(username, hash(password), role));
    }

    private String hash(String password) {
        if (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль должен содержать не более 72 байт UTF-8");
        }
        return passwords.encode(password);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
    }
}
