package com.xujiayao.discord_mc_chat.server.linking;

import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Manages temporary verification codes for the secure account linking workflow.
 * <p>
 * Verification codes are 6-character alphanumeric strings that are valid for 5 minutes.
 * Codes are stored in-memory and mapped from code to the pending verification details.
 * <p>
 * This manager runs on the Server side and is the single source of truth for code validation.
 *
 * @author Xujiayao
 */
public final class VerificationCodeManager {

	private static final int CODE_LENGTH = 6;
	private static final long CODE_EXPIRY_MILLIS = 5 * 60 * 1000L; // 5 minutes
	private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // No I/O/0/1 for readability
	private static final SecureRandom RANDOM = new SecureRandom();

	// Code -> PendingVerification
	private static final Map<String, PendingVerification> PENDING_CODES = new ConcurrentHashMap<>();

	// Minecraft UUID -> Code (for fast lookup by player UUID)
	private static final Map<String, String> UUID_TO_CODE = new ConcurrentHashMap<>();

	private VerificationCodeManager() {
	}

	/**
	 * Generates or refreshes a verification code for a Minecraft player.
	 * <p>
	 * If the player already has an unexpired code, the same code is returned
	 * with its expiry time reset to 5 minutes from now. If the code has expired
	 * or does not exist, a new code is generated.
	 *
	 * @param minecraftUuid The UUID of the Minecraft player.
	 * @param playerName    The display name of the Minecraft player.
	 * @return The verification code.
	 */
	public static String generateOrRefreshCode(String minecraftUuid, String playerName) {
		purgeExpired();

		String existingCode = UUID_TO_CODE.get(minecraftUuid);
		if (existingCode != null) {
			PendingVerification existing = PENDING_CODES.get(existingCode);
			if (existing != null && existing.expiresAt() > System.currentTimeMillis()) {
				// Refresh expiry time for the existing code
				long newExpiry = System.currentTimeMillis() + CODE_EXPIRY_MILLIS;
				PENDING_CODES.put(existingCode, new PendingVerification(minecraftUuid, playerName, newExpiry));
				LOGGER.info(I18nManager.getDmccTranslation("linking.verification.refreshed", playerName));
				return existingCode;
			} else {
				// Code has expired, remove it
				PENDING_CODES.remove(existingCode);
				UUID_TO_CODE.remove(minecraftUuid);
			}
		}

		// Generate a new unique code
		String code;
		do {
			code = generateCode();
		} while (PENDING_CODES.containsKey(code));

		long expiresAt = System.currentTimeMillis() + CODE_EXPIRY_MILLIS;
		PENDING_CODES.put(code, new PendingVerification(minecraftUuid, playerName, expiresAt));
		UUID_TO_CODE.put(minecraftUuid, code);

		LOGGER.info(I18nManager.getDmccTranslation("linking.verification.generated", playerName));
		return code;
	}

	/**
	 * Validates and consumes a verification code. If the code is valid and not expired,
	 * it is removed from the pending map and the associated player info is returned.
	 *
	 * @param code The verification code to validate.
	 * @return The PendingVerification details if the code is valid, or null if invalid/expired.
	 */
	public static PendingVerification consumeCode(String code) {
		purgeExpired();

		String upperCode = code.toUpperCase();
		PendingVerification pending = PENDING_CODES.remove(upperCode);

		if (pending == null) {
			return null;
		}

		if (pending.expiresAt() <= System.currentTimeMillis()) {
			UUID_TO_CODE.remove(pending.minecraftUuid());
			return null;
		}

		UUID_TO_CODE.remove(pending.minecraftUuid());
		LOGGER.info(I18nManager.getDmccTranslation("linking.verification.consumed", pending.playerName()));
		return pending;
	}

	/**
	 * Clears all pending verification codes.
	 */
	public static void clear() {
		PENDING_CODES.clear();
		UUID_TO_CODE.clear();
	}

	/**
	 * Removes expired codes from the pending map.
	 */
	private static void purgeExpired() {
		long now = System.currentTimeMillis();
		PENDING_CODES.entrySet().removeIf(entry -> {
			if (entry.getValue().expiresAt() <= now) {
				UUID_TO_CODE.remove(entry.getValue().minecraftUuid());
				return true;
			}
			return false;
		});
	}

	/**
	 * Generates a random 6-character verification code.
	 *
	 * @return The generated code.
	 */
	private static String generateCode() {
		StringBuilder sb = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
		}
		return sb.toString();
	}

	/**
	 * A pending verification entry.
	 *
	 * @param minecraftUuid The UUID of the Minecraft player requesting linking.
	 * @param playerName    The display name of the Minecraft player (for messages only).
	 * @param expiresAt     The timestamp (epoch millis) when this code expires.
	 */
	public record PendingVerification(String minecraftUuid, String playerName, long expiresAt) {
	}
}
