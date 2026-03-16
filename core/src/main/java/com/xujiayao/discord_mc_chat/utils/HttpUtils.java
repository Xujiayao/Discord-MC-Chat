package com.xujiayao.discord_mc_chat.utils;

import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static com.xujiayao.discord_mc_chat.Constants.OK_HTTP_CLIENT;

/**
 * HTTP utility class that wraps OkHttp operations.
 *
 * @author Xujiayao
 */
public class HttpUtils {

	/**
	 * Performs a GET request to the specified URL and returns the response body as a string.
	 *
	 * @param url The URL to request
	 * @return The response body as a string
	 * @throws IOException If the request fails or returns a non-successful status code
	 */
	public static String get(String url) throws IOException {
		Request request = new Request.Builder().url(url).build();

		try (Response response = OK_HTTP_CLIENT.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("HTTP request failed with status code: " + response.code());
			}

			return response.body().string();
		}
	}
}
