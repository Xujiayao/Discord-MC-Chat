package com.xujiayao.discord_mc_chat.events;

import com.xujiayao.discord_mc_chat.config.I18nManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * A simple event manager to handle event handling and posting.
 *
 * @author Xujiayao
 */
public final class EventManager {

	private static final ConcurrentHashMap<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

	private EventManager() {
	}

	/**
	 * Register a handler for a specific event type.
	 *
	 * @param eventClass The class of the event to handle
	 * @param handler    The consumer that will handle the event
	 * @param <T>        The type of the event
	 */
	public static <T> void register(Class<T> eventClass, Consumer<T> handler) {
		handlers.computeIfAbsent(eventClass, _ -> new CopyOnWriteArrayList<>()).add(handler);
	}

	/**
	 * Post an event and notify all handlers registered for the event's exact class.
	 * <p>
	 * Handlers are looked up by the event's runtime class only: a handler registered for a superclass or for an
	 * interface of the event is never invoked, and posting such a subclass silently reaches no handler. A handler
	 * that throws is logged and does not stop the remaining handlers from being notified.
	 *
	 * @param event The event object to be posted
	 * @param <T>   The type of the event
	 */
	@SuppressWarnings("unchecked")
	public static <T> void post(T event) {
		List<Consumer<?>> eventHandlers = handlers.get(event.getClass());
		if (eventHandlers != null) {
			for (Consumer<?> handler : eventHandlers) {
				try {
					((Consumer<T>) handler).accept(event);
				} catch (Exception e) {
					// One misbehaving handler must not prevent the others from receiving the event.
					LOGGER.error(I18nManager.getDmccTranslation("utils.events.handler_failed", event.getClass().getName()), e);
				}
			}
		}
	}
}
