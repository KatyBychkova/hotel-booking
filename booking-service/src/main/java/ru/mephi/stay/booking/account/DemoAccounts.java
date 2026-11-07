package ru.mephi.stay.booking.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
class DemoAccounts implements CommandLineRunner {
    private final AccountService accounts;
    private final String password;

    DemoAccounts(AccountService accounts, @Value("${app.admin-password}") String password) {
        this.accounts = accounts;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (password.length() < 8) throw new IllegalArgumentException("ADMIN_PASSWORD должен быть не короче 8 символов");
        accounts.create(new AccountDto.Create("admin", password, Role.ADMIN));
    }
}
