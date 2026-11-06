package com.tripbudget.tripbudget_core.auth.password;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/** Preserve Node bcrypt's 72-byte behavior, including a boundary inside a UTF-8 character. */
@Component
public class PasswordHasher {
    private final SecureRandom random = new SecureRandom();

    public String encode(String password) {
        return BCrypt.hashpw(bcryptBytes(password), BCrypt.gensalt("$2b", 12, random));
    }

    public boolean matches(String password, String hash) {
        if (password == null || hash == null) return false;
        return BCrypt.checkpw(bcryptBytes(password), hash);
    }

    private byte[] bcryptBytes(String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        return bytes.length > 72 ? Arrays.copyOf(bytes, 72) : bytes;
    }
}
