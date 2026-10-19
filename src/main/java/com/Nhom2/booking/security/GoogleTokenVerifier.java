package com.Nhom2.booking.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.Nhom2.booking.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifies Google ID tokens (signature, issuer, audience, expiry).
 * One shared verifier so Google's public keys are cached instead of re-downloaded on every login.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    /**
     * Returns the token's email, which Google has verified the user owns.
     * Throws when the token is invalid or expired, or the email is not verified.
     */
    public String verifyEmail(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            throw new UnauthorizedException("error.google.verifyFailed", e);
        }
        if (idToken == null) {
            throw new UnauthorizedException("error.google.invalidToken");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new UnauthorizedException("error.google.emailNotVerified");
        }
        return email;
    }
}
