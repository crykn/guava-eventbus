/*
 * Copyright (C) 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.pustike.eventbus;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;

/**
 * Handler for dispatching events to subscribers, providing different event ordering guarantees that make sense for
 * different situations.
 *
 * <p><b>Note:</b> The dispatcher is orthogonal to the subscriber's {@code Executor}. The dispatcher controls the order
 * in which events are dispatched, while the executor controls how (i.e. on which thread) the subscriber is actually
 * called when an event is dispatched to it.
 * @author Colin Decker
 */
public abstract class Dispatcher {
    /**
     * Returns a dispatcher that queues events that are posted reentrantly on a thread that is already dispatching an
     * event, guaranteeing that all events posted on a single thread are dispatched to all subscribers in the order they
     * are posted.
     *
     * <p> When all subscribers are dispatched to using a <i>direct</i> executor (which dispatches on the same thread
     * that posts the event), this yields a breadth-first dispatch order on each thread. That is, all subscribers to a
     * single event A will be called before any subscribers to any events B and C that are posted to the event bus by
     * the subscribers to A.
     * @return the per thread dispatcher
     */
    public static Dispatcher perThreadDispatchQueue() {
        return new PerThreadQueuedDispatcher();
    }

    /**
     * Returns a dispatcher that dispatches events to subscribers immediately as they're posted without using an
     * intermediate queue to change the dispatch order. This is effectively a depth-first dispatch order, vs.
     * breadth-first when using a queue.
     * @return the immediate dispatcher
     */
    public static Dispatcher immediate() {
        return ImmediateDispatcher.INSTANCE;
    }

    /**
     * Dispatches the given {@code event} to the given {@code subscribers}.
     */
    abstract void dispatch(Object event, Iterator<Subscriber> subscribers);

    /**
     * Implementation of a {@link #perThreadDispatchQueue()} dispatcher.
     */
    private static final class PerThreadQueuedDispatcher extends Dispatcher {
        /**
         * Per-thread queue of events to dispatch.
         */
        private final ThreadLocal<Queue<Event>> queue = ThreadLocal.withInitial(ArrayDeque::new);
        /**
         * Per-thread dispatch state, used to avoid reentrant event dispatching.
         */
        private final ThreadLocal<Boolean> dispatching = ThreadLocal.withInitial(() -> false);

        @Override
        void dispatch(Object event, Iterator<Subscriber> subscribers) {
            Objects.requireNonNull(event);
            Objects.requireNonNull(subscribers);
            Queue<Event> queueForThread = queue.get();
            queueForThread.offer(new Event(event, subscribers));

            if (!dispatching.get()) {
                dispatching.set(true);
                try {
                    Event nextEvent;
                    while ((nextEvent = queueForThread.poll()) != null) {
                        while (nextEvent.subscribers.hasNext()) {
                            nextEvent.subscribers.next().dispatchEvent(nextEvent.event);
                        }
                    }
                } finally {
                    dispatching.remove();
                    queue.remove();
                }
            }
        }

        private static final class Event {
            private final Object event;
            private final Iterator<Subscriber> subscribers;

            private Event(Object event, Iterator<Subscriber> subscribers) {
                this.event = event;
                this.subscribers = subscribers;
            }
        }
    }

    /**
     * Implementation of {@link #immediate()}.
     */
    private static final class ImmediateDispatcher extends Dispatcher {
        private static final ImmediateDispatcher INSTANCE = new ImmediateDispatcher();

        @Override
        void dispatch(Object event, Iterator<Subscriber> subscribers) {
            Objects.requireNonNull(event);
            while (subscribers.hasNext()) {
                subscribers.next().dispatchEvent(event);
            }
        }
    }
}
