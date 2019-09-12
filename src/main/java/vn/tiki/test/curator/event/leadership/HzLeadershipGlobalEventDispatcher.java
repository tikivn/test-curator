package vn.tiki.test.curator.event.leadership;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;

import lombok.Builder;
import lombok.NonNull;
import vn.tiki.test.curator.event.AbstractEventDispatcher;

public class HzLeadershipGlobalEventDispatcher extends AbstractEventDispatcher<String>
        implements LeadershipGlobalEventDispatcher {

    private final @NonNull ITopic<String> topic;

    @Builder
    private HzLeadershipGlobalEventDispatcher(@NonNull HazelcastInstance hazelcast, @NonNull String topicName) {
        this.topic = hazelcast.getTopic(topicName);
        this.topic.addMessageListener(this::onGlobalEvent);
    }

    @Override
    public void publishEvent(String id) {
        this.topic.publish(id);
    }

    private void onGlobalEvent(Message<String> message) {
        super.publishEvent(message.getMessageObject());
    }
}
