package vn.tiki.test.curator.leadership;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import lombok.NonNull;

public abstract class AbstractEventDispatcher<EventType> implements EventDispatcher<EventType> {

    private final List<Consumer<EventType>> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public final Disposable subscribeEvent(@NonNull Consumer<EventType> listener) {
        if (subscribers.add(listener))
            return () -> subscribers.remove(listener);
        return null;
    }

    @Override
    public void publishEvent(EventType event) {
        for (var subscriber : subscribers)
            subscriber.accept(event);
    }
}
