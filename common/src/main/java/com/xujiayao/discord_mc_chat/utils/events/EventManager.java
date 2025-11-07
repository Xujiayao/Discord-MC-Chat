package com.xujiayao.discord_mc_chat.utils.events;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A simple event manager to handle event handling and posting.
 *
 * @author Xujiayao
 */
public class EventManager {

	private static final ConcurrentHashMap<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

	/**
	 * Register a handler for a specific event type.
	 *
	 * @param eventClass The class of the event to handle
	 * @param handler    The consumer that will handle the event
	 * @param <T>        The type of the event
	 */
	public static <T> void register(Class<T> eventClass, Consumer<T> handler) {
		handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(handler);
	}

	/**
	 * Post an event and notify all registered handlers.
	 *
	 * @param event The event object to be posted
	 * @param <T>   The type of the event
	 */
	@SuppressWarnings("unchecked")
	public static <T> void post(T event) {
		List<Consumer<?>> eventHandlers = handlers.get(event.getClass());
		if (eventHandlers != null) {
			for (Consumer<?> handler : eventHandlers) {
				((Consumer<T>) handler).accept(event);
			}
		}
	}

	/**
	 * Clears all registered event handlers.
	 */
	public static void clear() {
		handlers.clear();
	}
}
