package com.xujiayao.discord_mc_chat.common.utils.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A simple event manager to handle event handling and dispatching.
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
		handlers.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(handler);
	}

	/**
	 * Dispatch an event and notify all registered handlers.
	 *
	 * @param event The event object to be dispatched
	 * @param <T>   The type of the event
	 */
	@SuppressWarnings("unchecked")
	public static <T> void dispatch(T event) {
		List<Consumer<?>> eventHandlers = handlers.get(event.getClass());
		if (eventHandlers != null) {
			for (Consumer<?> handler : eventHandlers) {
				((Consumer<T>) handler).accept(event);
			}
		}
	}
}
