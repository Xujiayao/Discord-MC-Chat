package com.xujiayao.discord_mc_chat.server.discord;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps common Unicode emoji characters to their short-code representations.
 * <p>
 * This is a lightweight mapper covering frequently used emojis. Unknown emojis
 * will fall back to their original Unicode character.
 *
 * @author Xujiayao
 */
public class EmojiShortCodeMapper {

	private static final Map<String, String> EMOJI_MAP = new HashMap<>();

	static {
		// Smileys & People
		EMOJI_MAP.put("\uD83D\uDE00", ":grinning:");
		EMOJI_MAP.put("\uD83D\uDE01", ":grin:");
		EMOJI_MAP.put("\uD83D\uDE02", ":joy:");
		EMOJI_MAP.put("\uD83D\uDE03", ":smiley:");
		EMOJI_MAP.put("\uD83D\uDE04", ":smile:");
		EMOJI_MAP.put("\uD83D\uDE05", ":sweat_smile:");
		EMOJI_MAP.put("\uD83D\uDE06", ":laughing:");
		EMOJI_MAP.put("\uD83D\uDE07", ":innocent:");
		EMOJI_MAP.put("\uD83D\uDE08", ":smiling_imp:");
		EMOJI_MAP.put("\uD83D\uDE09", ":wink:");
		EMOJI_MAP.put("\uD83D\uDE0A", ":blush:");
		EMOJI_MAP.put("\uD83D\uDE0B", ":yum:");
		EMOJI_MAP.put("\uD83D\uDE0C", ":relieved:");
		EMOJI_MAP.put("\uD83D\uDE0D", ":heart_eyes:");
		EMOJI_MAP.put("\uD83D\uDE0E", ":sunglasses:");
		EMOJI_MAP.put("\uD83D\uDE0F", ":smirk:");
		EMOJI_MAP.put("\uD83D\uDE10", ":neutral_face:");
		EMOJI_MAP.put("\uD83D\uDE11", ":expressionless:");
		EMOJI_MAP.put("\uD83D\uDE12", ":unamused:");
		EMOJI_MAP.put("\uD83D\uDE13", ":sweat:");
		EMOJI_MAP.put("\uD83D\uDE14", ":pensive:");
		EMOJI_MAP.put("\uD83D\uDE15", ":confused:");
		EMOJI_MAP.put("\uD83D\uDE16", ":confounded:");
		EMOJI_MAP.put("\uD83D\uDE17", ":kissing:");
		EMOJI_MAP.put("\uD83D\uDE18", ":kissing_heart:");
		EMOJI_MAP.put("\uD83D\uDE19", ":kissing_smiling_eyes:");
		EMOJI_MAP.put("\uD83D\uDE1A", ":kissing_closed_eyes:");
		EMOJI_MAP.put("\uD83D\uDE1B", ":stuck_out_tongue:");
		EMOJI_MAP.put("\uD83D\uDE1C", ":stuck_out_tongue_winking_eye:");
		EMOJI_MAP.put("\uD83D\uDE1D", ":stuck_out_tongue_closed_eyes:");
		EMOJI_MAP.put("\uD83D\uDE1E", ":disappointed:");
		EMOJI_MAP.put("\uD83D\uDE1F", ":worried:");
		EMOJI_MAP.put("\uD83D\uDE20", ":angry:");
		EMOJI_MAP.put("\uD83D\uDE21", ":rage:");
		EMOJI_MAP.put("\uD83D\uDE22", ":cry:");
		EMOJI_MAP.put("\uD83D\uDE23", ":persevere:");
		EMOJI_MAP.put("\uD83D\uDE24", ":triumph:");
		EMOJI_MAP.put("\uD83D\uDE25", ":disappointed_relieved:");
		EMOJI_MAP.put("\uD83D\uDE26", ":frowning:");
		EMOJI_MAP.put("\uD83D\uDE27", ":anguished:");
		EMOJI_MAP.put("\uD83D\uDE28", ":fearful:");
		EMOJI_MAP.put("\uD83D\uDE29", ":weary:");
		EMOJI_MAP.put("\uD83D\uDE2A", ":sleepy:");
		EMOJI_MAP.put("\uD83D\uDE2B", ":tired_face:");
		EMOJI_MAP.put("\uD83D\uDE2C", ":grimacing:");
		EMOJI_MAP.put("\uD83D\uDE2D", ":sob:");
		EMOJI_MAP.put("\uD83D\uDE2E", ":open_mouth:");
		EMOJI_MAP.put("\uD83D\uDE2F", ":hushed:");
		EMOJI_MAP.put("\uD83D\uDE30", ":cold_sweat:");
		EMOJI_MAP.put("\uD83D\uDE31", ":scream:");
		EMOJI_MAP.put("\uD83D\uDE32", ":astonished:");
		EMOJI_MAP.put("\uD83D\uDE33", ":flushed:");
		EMOJI_MAP.put("\uD83D\uDE34", ":sleeping:");
		EMOJI_MAP.put("\uD83D\uDE35", ":dizzy_face:");
		EMOJI_MAP.put("\uD83D\uDE36", ":no_mouth:");
		EMOJI_MAP.put("\uD83D\uDE37", ":mask:");
		EMOJI_MAP.put("\uD83D\uDE38", ":smile_cat:");
		EMOJI_MAP.put("\uD83D\uDE39", ":joy_cat:");
		EMOJI_MAP.put("\uD83D\uDE3A", ":smiley_cat:");
		EMOJI_MAP.put("\uD83D\uDE3B", ":heart_eyes_cat:");
		EMOJI_MAP.put("\uD83D\uDE3C", ":smirk_cat:");
		EMOJI_MAP.put("\uD83D\uDE3D", ":kissing_cat:");
		EMOJI_MAP.put("\uD83D\uDE3E", ":pouting_cat:");
		EMOJI_MAP.put("\uD83D\uDE3F", ":crying_cat_face:");
		EMOJI_MAP.put("\uD83D\uDE40", ":scream_cat:");
		EMOJI_MAP.put("\uD83D\uDE4B", ":raising_hand:");
		EMOJI_MAP.put("\uD83D\uDE4C", ":raised_hands:");
		EMOJI_MAP.put("\uD83D\uDE4D", ":person_frowning:");
		EMOJI_MAP.put("\uD83D\uDE4E", ":person_pouting:");
		EMOJI_MAP.put("\uD83D\uDE4F", ":pray:");

		// Common symbols
		EMOJI_MAP.put("\u2764", ":heart:");
		EMOJI_MAP.put("\u2764\uFE0F", ":heart:");
		EMOJI_MAP.put("\uD83D\uDC94", ":broken_heart:");
		EMOJI_MAP.put("\uD83D\uDC4D", ":thumbsup:");
		EMOJI_MAP.put("\uD83D\uDC4E", ":thumbsdown:");
		EMOJI_MAP.put("\uD83D\uDC4C", ":ok_hand:");
		EMOJI_MAP.put("\u270C", ":v:");
		EMOJI_MAP.put("\u270C\uFE0F", ":v:");
		EMOJI_MAP.put("\uD83D\uDC4F", ":clap:");
		EMOJI_MAP.put("\uD83D\uDC4B", ":wave:");
		EMOJI_MAP.put("\uD83D\uDD25", ":fire:");
		EMOJI_MAP.put("\u2B50", ":star:");
		EMOJI_MAP.put("\uD83C\uDF1F", ":star2:");
		EMOJI_MAP.put("\u2705", ":white_check_mark:");
		EMOJI_MAP.put("\u274C", ":x:");
		EMOJI_MAP.put("\u2753", ":question:");
		EMOJI_MAP.put("\u2757", ":exclamation:");
		EMOJI_MAP.put("\u26A0", ":warning:");
		EMOJI_MAP.put("\u26A0\uFE0F", ":warning:");
		EMOJI_MAP.put("\uD83D\uDCAF", ":100:");
		EMOJI_MAP.put("\uD83C\uDF89", ":tada:");
		EMOJI_MAP.put("\uD83C\uDF8A", ":confetti_ball:");
	}

	/**
	 * Gets the short-code representation for a Unicode emoji.
	 *
	 * @param emoji The Unicode emoji string.
	 * @return The short-code (e.g. ":blush:"), or the original Unicode character if unknown.
	 */
	public static String getShortCode(String emoji) {
		String code = EMOJI_MAP.get(emoji);
		if (code != null) {
			return code;
		}
		// Fallback: return the original emoji character as-is
		return emoji;
	}
}
