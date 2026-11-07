package ru.mephi.stay.booking.account;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.*;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ru.mephi.stay.common.JwtSupport;

@Service
public class TokenService {
    private final JwtEncoder encoder;

    public TokenService(SecretKey key) {
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    AccountDto.Token forUser(User user) {
        return new AccountDto.Token(issue(user.getId().toString(), user.getRole().name(), Duration.ofHours(1)), 3600);
    }

    public String forHotel() {
        return issue("booking-service", "INTERNAL", Duration.ofMinutes(2));
    }

    private String issue(String subject, String role, Duration lifetime) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(JwtSupport.ISSUER).subject(subject).issuedAt(now)
                .expiresAt(now.plus(lifetime)).claim("role", role).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
