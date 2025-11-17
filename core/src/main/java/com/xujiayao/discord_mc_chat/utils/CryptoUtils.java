package com.xujiayao.discord_mc_chat.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptography utility class for DMCC.
 *
 * @author Xujiayao
 */
public class CryptoUtils {

	private static final String HMAC_SHA256 = "HmacSHA256";

	/**
	 * Generates a cryptographically secure random string of a given length.
	 *
	 * @param length The desired length of the string.
	 * @return A random Base64 URL-safe encoded string.
	 */
	public static String generateRandomString(int length) {
		SecureRandom random = new SecureRandom();
		byte[] bytes = new byte[length];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
	}

	/**
	 * Calculates the HMAC-SHA256 hash of a given data string with a secret key.
	 *
	 * @param secret The secret key.
	 * @param data   The data to hash.
	 * @return The Base64 encoded HMAC-SHA256 hash.
	 * @throws RuntimeException if the HMAC-SHA256 algorithm is not available or the key is invalid.
	 */
	public static String hmacSha256(String secret, String data) {
		try {
			Mac sha256_HMAC = Mac.getInstance(HMAC_SHA256);
			SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
			sha256_HMAC.init(secret_key);
			byte[] hashBytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hashBytes);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			// These exceptions should not happen in a standard Java environment.
			// If they do, it's a critical, unrecoverable configuration error.
			throw new RuntimeException("Failed to calculate HMAC-SHA256. This is a critical issue.", e);
		}
	}
}
