package com.xujiayao.discord_mc_chat.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Cryptographic utilities.
 *
 * @author Xujiayao
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
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
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
