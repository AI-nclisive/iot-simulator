package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans worker-side client-activity events out to every open {@code ClientEvents}
 * stream (IS-047). The OPC UA server runtime publishes connect/disconnect events
 * here; the gRPC service registers one observer per supervisor subscription.
 *
 * <p>Events are point-in-time and not buffered — one published while no stream is
 * open is dropped. In practice the supervisor opens the stream as soon as the
 * worker reaches RUNNING, before any protocol client can connect.
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
final class ClientEventHub {

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /** Registers a supervisor's stream; it is removed when the supervisor cancels it. */
    void register(ServerCallStreamObserver<ClientEvent> observer) {
        Subscriber subscriber = new Subscriber(observer);
        observer.setOnCancelHandler(() -> subscribers.remove(subscriber));
        subscribers.add(subscriber);
    }

    /**
     * Publishes an event to every open stream. Session callbacks can fire from
     * different Milo threads, so each subscriber's {@code onNext} is serialized on a
     * lock the hub owns; a cancelled or failing stream is dropped.
     */
    void emit(ClientEvent event) {
        for (Subscriber subscriber : subscribers) {
            if (!subscriber.deliver(event)) {
                subscribers.remove(subscriber);
            }
        }
    }

    /** Number of open supervisor streams (introspection/tests). */
    int openStreamCount() {
        return subscribers.size();
    }

    /** One supervisor stream plus a hub-owned lock that serializes delivery to it. */
    private static final class Subscriber {

        private final ServerCallStreamObserver<ClientEvent> observer;
        private final Object lock = new Object();

        Subscriber(ServerCallStreamObserver<ClientEvent> observer) {
            this.observer = observer;
        }

        /** Delivers one event under the hub-owned lock; returns false if the stream is gone. */
        boolean deliver(ClientEvent event) {
            synchronized (lock) {
                if (observer.isCancelled()) {
                    return false;
                }
                try {
                    observer.onNext(event);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }
        }
    }
}
