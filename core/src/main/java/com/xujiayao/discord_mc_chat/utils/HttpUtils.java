package com.xujiayao.discord_mc_chat.utils;

import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.OK_HTTP_CLIENT;

public final class HttpUtils {

	/**
	 * Cache policy for requests that must bypass any local cache; all three directives end up in the
	 * {@code Cache-Control} request header, so none of them can be dropped.
	 */
	private static final CacheControl NO_CACHE = new CacheControl.Builder()
			.noCache()
			.noStore()
			.maxAge(0, TimeUnit.SECONDS)
			.build();

	private HttpUtils() {
	}

	/**
	 * @throws IOException If the request fails or returns a non-successful status code
	 */
	public static String get(String url) throws IOException {
		return execute(OK_HTTP_CLIENT, new Request.Builder().url(url).build());
	}

	/**
	 * Performs a GET request while forcing network usage to bypass local cache.
	 *
	 * @throws IOException If the request fails or returns a non-successful status code
	 */
	public static String getNoCache(String url) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.cacheControl(NO_CACHE)
				.build();

		return execute(OK_HTTP_CLIENT, request);
	}

	/**
	 * Performs a GET request with a caller-supplied client, so a call site can use its own timeouts.
	 *
	 * @throws IOException If the request fails or returns a non-successful status code
	 */
	static String get(OkHttpClient client, String url) throws IOException {
		return execute(client, new Request.Builder().url(url).build());
	}

	private static String execute(OkHttpClient client, Request request) throws IOException {
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("HTTP request failed with status code: " + response.code());
			}

			return response.body().string();
		}
	}
}
