package ru.mephi.stay.booking.account;

import jakarta.validation.constraints.*;

public final class AccountDto {
    private AccountDto() {}
    public static final String LOGIN = "[а-яА-ЯёЁa-zA-Z0-9._-]{3,60}";

    public record Credentials(@NotNull @Pattern(regexp = LOGIN) String username,
                              @NotBlank @Size(min = 8, max = 72) String password) {}
    public record Create(@NotNull @Pattern(regexp = LOGIN) String username,
                         @NotBlank @Size(min = 8, max = 72) String password, @NotNull Role role) {}
    public record Update(@Pattern(regexp = LOGIN) String username, @Size(min = 8, max = 72) String password, Role role) {}
    public record View(long id, String username, Role role) {
        static View of(User user) { return new View(user.getId(), user.getUsername(), user.getRole()); }
    }
    public record Token(String token, long expiresIn) {}
}
