package vn.tiki.test.curator.leadership.impl;

import static io.gridgo.utils.ThreadUtils.sleepSilence;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LeaderElectionAgent extends AbstractLeadershipLocalEventDispatcher {

    private final LeaderSelectorListener leaderSelectorListener = new LeaderSelectorListener() {

        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            LeaderElectionAgent.this.stateChanged(newState);
        }

        @Override
        public void takeLeadership(CuratorFramework client) throws Exception {
            LeaderElectionAgent.this.takeLeadership();
        }
    };

    private final AtomicReference<CountDownLatch> lockHolder = new AtomicReference<>(null);

    private LeadershipGlobalEventDispatcher globalEventDispatcher;

    private @NonNull CuratorFramework client;

    private @NonNull String rootPath;

    private LeaderSelector selector;

    private @NonNull String id;

    private @NonNull byte[] data;

    private String lastLeaderId;

    @Builder
    private LeaderElectionAgent(CuratorFramework client, String rootPath, String id, byte[] data,
            LeadershipGlobalEventDispatcher globalEventDispatcher) {
        this.client = client;
        this.rootPath = rootPath;
        this.id = id;
        this.data = data;
        this.globalEventDispatcher = globalEventDispatcher;
    }

    public void stop() {
        selector.close();
    }

    public void start() {
        selector = new LeaderSelector(client, rootPath, leaderSelectorListener);
        selector.setId(id);
        selector.autoRequeue();
        selector.start();

        if (globalEventDispatcher != null)
            globalEventDispatcher.subscribeEvent(this::checkLeader);

        checkLeader(null);
    }

    private void checkLeader(String maybeLeaderId) {
        try {
            var currentLeader = findCurrentLeader(500, 30);
            if (null == currentLeader || currentLeader.isBlank()) {
                log.debug("No leader found");
                return;
            }

            if (maybeLeaderId != null && !currentLeader.equals(maybeLeaderId))
                log.debug("Inconsistent leader check, expected {}, got {} --> use response from zoo", maybeLeaderId,
                        currentLeader);

            if (!currentLeader.equals(lastLeaderId)) {
                lastLeaderId = currentLeader;
                publishEvent(LeadershipEvent.builder() //
                        .localId(id) //
                        .leaderId(currentLeader) //
                        .leaderData(getParticipantData(currentLeader)) //
                        .build());
            }
        } catch (Exception e) {
            log.error("check leader error", e);
        }
    }

    private String findCurrentLeader(long intervalMs, int maxTry) throws Exception {
        int count = 0;
        String leader;
        while ((leader = selector.getLeader().getId()) == null)
            if (!sleepSilence(100) || count++ > maxTry)
                return null;

        return leader;
    }

    private byte[] getParticipantData(@NonNull String id) throws Exception {
        if (id.equals(this.id))
            return this.data;
        if (id.isBlank())
            return null;
        return this.client.getData().forPath(rootPath + "/" + id);
    }

    protected final void keepLeadership() {
        if (lockHolder.compareAndSet(null, new CountDownLatch(1))) {
            try {
                lockHolder.get().await();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    protected final void releaseLeadership() {
        this.lockHolder.accumulateAndGet(null, (curr, newValue) -> {
            if (curr != null)
                curr.countDown();
            return null;
        });
    }

    private void stateChanged(ConnectionState newState) {
        log.debug("state changed, is connected: " + newState.isConnected());
    }

    private void takeLeadership() throws Exception {
        client.create().orSetData().withMode(CreateMode.EPHEMERAL).forPath(rootPath + "/" + id, data);
        if (globalEventDispatcher != null) {
            globalEventDispatcher.publishEvent(id);
        } else {
            checkLeader(id);
        }
        keepLeadership();
    }
}
