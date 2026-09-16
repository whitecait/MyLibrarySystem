package com.library.identity.internal;

import com.library.contract.InfrastructureFailure;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 口令散列（模块内部实现细节）。JDK PBKDF2WithHmacSHA256，
 * 不引入额外安全框架依赖；存储格式 pbkdf2$&lt;iter&gt;$&lt;saltB64&gt;$&lt;hashB64&gt;。
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        var salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + HexFormat.of().formatHex(salt) + "$"
                + HexFormat.of().formatHex(derive(password, salt, ITERATIONS));
    }

    public static boolean matches(String password, String stored) {
        var parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        var iterations = Integer.parseInt(parts[1]);
        var salt = HexFormat.of().parseHex(parts[2]);
        var expected = HexFormat.of().parseHex(parts[3]);
        return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            throw new InfrastructureFailure("口令散列计算失败", e);
        }
    }
}
