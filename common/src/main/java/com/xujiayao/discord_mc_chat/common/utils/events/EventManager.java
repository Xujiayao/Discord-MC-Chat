package com.xujiayao.discord_mc_chat.common.utils.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A simple event manager to handle event listening and dispatching.
 *
 * @author Xujiayao
 */
public class EventManager {

	private static final ConcurrentHashMap<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

	/**
	 * Register a listener for a specific event type.
	 *
	 * @param eventClass The class of the event to listen for
	 * @param listener   The consumer that will handle the event
	 * @param <T>        The type of the event
	 */
	public static <T> void register(Class<T> eventClass, Consumer<T> listener) {
		listeners.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(listener);
	}

	/**
	 * Dispatch an event and notify all registered listeners.
	 *
	 * @param event The event object to be dispatched
	 * @param <T>   The type of the event
	 */
	@SuppressWarnings("unchecked")
	public static <T> void dispatch(T event) {
		List<Consumer<?>> eventListeners = listeners.get(event.getClass());
		if (eventListeners != null) {
			for (Consumer<?> listener : eventListeners) {
				((Consumer<T>) listener).accept(event);
			}
		}
	}
}
