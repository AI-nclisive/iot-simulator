package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans worker-side runtime events out to every open {@code RuntimeEvents} stream
 * (IS-048). The server runtime publishes start/stop here and the service publishes
 * errors; the gRPC service registers one observer per supervisor subscription.
 *
 * <p>Events are point-in-time and not buffered — one published while no stream is
 * open is dropped. The supervisor opens the stream before Start, so SOURCE_START
 * is captured. See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
final class RuntimeEventHub {

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /** Registers a supervisor's stream; it is removed when the supervisor cancels it. */
    void register(ServerCallStreamObserver<RuntimeEvent> observer) {
        Subscriber subscriber = new Subscriber(observer);
        observer.setOnCancelHandler(() -> subscribers.remove(subscriber));
        subscribers.add(subscriber);
    }

    /**
     * Publishes an event to every open stream. Callbacks can fire from different
     * threads, so each subscriber's {@code onNext} is serialized on a lock the hub
     * owns; a cancelled or failing stream is dropped.
     */
    void emit(RuntimeEvent event) {
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

        private final ServerCallStreamObserver<RuntimeEvent> observer;
        private final Object lock = new Object();

        Subscriber(ServerCallStreamObserver<RuntimeEvent> observer) {
            this.observer = observer;
        }

        /** Delivers one event under the hub-owned lock; returns false if the stream is gone. */
        boolean deliver(RuntimeEvent event) {
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
