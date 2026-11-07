package ru.mephi.stay.booking.account;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 60)
    private String username;
    @Column(nullable = false)
    private String password;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Role role;

    User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    void update(AccountDto.Update input, String hash) {
        if (input.username() != null) username = input.username();
        if (hash != null) password = hash;
        if (input.role() != null) role = input.role();
    }
}
