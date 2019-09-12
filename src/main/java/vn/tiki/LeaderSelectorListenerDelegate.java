package vn.tiki;

import static lombok.AccessLevel.PROTECTED;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;

import lombok.Getter;
import lombok.NonNull;

public abstract class LeaderSelectorListenerDelegate {

    private final LeaderSelectorListener leaderSelectorListener = new LeaderSelectorListener() {

        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            LeaderSelectorListenerDelegate.this.stateChanged(client, newState);
        }

        @Override
        public void takeLeadership(CuratorFramework client) throws Exception {
            LeaderSelectorListenerDelegate.this.takeLeadership(client);
        }
    };

    @Getter(PROTECTED)
    private @NonNull CuratorFramework client;

    @Getter(PROTECTED)
    private @NonNull String rootPath;

    @Getter(PROTECTED)
    private LeaderSelector selector;

    @Getter(PROTECTED)
    private @NonNull String id;

    private final AtomicReference<CountDownLatch> lockHolder = new AtomicReference<>(null);

    public void init(CuratorFramework client, String rootPath, String id) {
        this.client = client;
        this.rootPath = rootPath;
        this.id = id;
    }

    public void stop() {
        selector.close();
    }

    public void start() throws Exception {
        selector = new LeaderSelector(client, rootPath, leaderSelectorListener);
        selector.setId(id);
        selector.autoRequeue();
        selector.start();
    }

    protected final void keepLeadership() {
        if (lockHolder.compareAndSet(null, new CountDownLatch(1))) {
            try {
                lockHolder.get().await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected final void releaseLeadership() {
        this.lockHolder.accumulateAndGet(null, (curr, newValue) -> {
            curr.countDown();
            return null;
        });
    }

    protected void stateChanged(CuratorFramework client, ConnectionState newState) {

    }

    protected abstract void takeLeadership(CuratorFramework client) throws Exception;
}
