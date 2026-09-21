package com.xujiayao.discord_mc_chat.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Cryptographic utilities.
 */
public final class CryptUtils {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

	private CryptUtils() {
	}

	/**
	 * Calculates the SHA-256 hash of a string.
	 *
	 * @param input The input string.
	 * @return The hex string of the hash.
	 */
	public static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new RuntimeException("SHA-256 algorithm not found", e);
		}
	}

	/**
	 * Generates a random alphanumeric string.
	 *
	 * @param length The length of the string.
	 * @return The random string.
	 */
	public static String generateRandomString(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(ALPHANUMERIC[RANDOM.nextInt(ALPHANUMERIC.length)]);
		}
		return sb.toString();
	}
}
