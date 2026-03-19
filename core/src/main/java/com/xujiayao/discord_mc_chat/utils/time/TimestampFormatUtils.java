package com.xujiayao.discord_mc_chat.utils.time;

import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Shared formatter for Discord timestamp syntax and relative time text.
 *
 * @author Xujiayao
 */
public class TimestampFormatUtils {

	private TimestampFormatUtils() {
	}

	/**
	 * Formats Discord timestamp token payload to localized display text.
	 *
	 * @param epoch Epoch seconds.
	 * @param style Discord timestamp style marker.
	 * @return Localized formatted text.
	 */
	public static String formatDiscordTimestamp(long epoch, String style) {
		Instant instant = Instant.ofEpochSecond(epoch);
		long nowEpochSecond = Instant.now().getEpochSecond();
		Locale locale = toLocale(I18nManager.getLanguage());
		ZoneId zone = ZoneId.systemDefault();
		String timestampStyle = style == null ? "f" : style;
		return switch (timestampStyle) {
			case "t" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			case "T" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(zone));
			case "d" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			case "D" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(instant.atZone(zone));
			case "F" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			case "R" -> formatRelative(nowEpochSecond - epoch);
			case "s", "S" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
			default -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone));
		};
	}

	/**
	 * Formats relative time by using DMCC translation keys.
	 *
	 * @param diffSeconds now - target epoch seconds.
	 * @return Relative time text.
	 */
	public static String formatRelative(long diffSeconds) {
		boolean past = diffSeconds >= 0;
		long abs = Math.abs(diffSeconds);
		String unitKey;
		long value;
		if (abs < 60) {
			value = abs;
			unitKey = "second";
		} else if (abs < 3600) {
			value = abs / 60;
			unitKey = "minute";
		} else if (abs < 86400) {
			value = abs / 3600;
			unitKey = "hour";
		} else if (abs < 2592000) {
			value = abs / 86400;
			unitKey = "day";
		} else if (abs < 31536000) {
			value = abs / 2592000;
			unitKey = "month";
		} else {
			value = abs / 31536000;
			unitKey = "year";
		}

		String unit = I18nManager.getDmccTranslation(
				"discord.message_parser.relative.units." + unitKey + "." + (value == 1 ? "one" : "other")
		);
		return past
				? I18nManager.getDmccTranslation("discord.message_parser.relative.past", value, unit)
				: I18nManager.getDmccTranslation("discord.message_parser.relative.future", value, unit);
	}

	/**
	 * Maps DMCC language code to Locale.
	 *
	 * @param languageCode DMCC language code.
	 * @return Locale instance.
	 */
	public static Locale toLocale(String languageCode) {
		if (languageCode == null || languageCode.isBlank()) {
			return Locale.ENGLISH;
		}
		String tag = languageCode.replace('_', '-');
		Locale locale = Locale.forLanguageTag(tag);
		return locale.getLanguage().isBlank() ? Locale.ENGLISH : locale;
	}
}
