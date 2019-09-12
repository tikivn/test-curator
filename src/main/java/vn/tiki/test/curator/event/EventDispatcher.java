package vn.tiki.test.curator.event;

import java.util.function.Consumer;

public interface EventDispatcher<EventType> {

    Disposable subscribeEvent(Consumer<EventType> subscriber);

    void publishEvent(EventType event);
}
