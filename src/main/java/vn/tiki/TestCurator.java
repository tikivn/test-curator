package vn.tiki;

import static io.gridgo.utils.ThreadUtils.isShuttingDown;
import static io.gridgo.utils.ThreadUtils.registerShutdownTask;
import static io.gridgo.utils.ThreadUtils.sleep;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import io.gridgo.bean.BObject;
import io.gridgo.utils.ThreadUtils;
import io.gridgo.utils.UuidUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestCurator extends LeaderSelectorListenerDelegate {

    public static void main(String[] args) throws Exception {
        log.info("Starting test curator...");

        log.info("*** start zooKeeper client");
        var client = CuratorFrameworkFactory.builder() //
                .connectString("127.0.0.1:2181") //
                .retryPolicy(new ExponentialBackoffRetry(1000, 3)) //
                .connectionTimeoutMs(10000) //
                .sessionTimeoutMs(5000) //
                .build();

        client.start();
        registerShutdownTask(client::close);

        log.info("*** start TestCurator application");
        var app = new TestCurator(client, "/nhb/test/curator/leader-election", UuidUtils.timebasedUUIDAsString());
        app.start();
        registerShutdownTask(app::stop);

        log.info("*** keep process alive");
        keepAlive();
    }

    private static void keepAlive() {
        new Thread(() -> {
            while (!isShuttingDown())
                sleep(100);
        }).start();
    }

    private byte[] data;

    private TestCurator(CuratorFramework client, String rootPath, String id) {
        init(client, rootPath, id);
        data = BObject.ofSequence("key", "this is value for node " + id).toJson().getBytes();
    }

    @Override
    protected void stateChanged(CuratorFramework client, ConnectionState newState) {
        log.debug("state changed, is connected: " + newState.isConnected());
    }

    @Override
    protected void takeLeadership(CuratorFramework client) throws Exception {
        log.info("Node {} has taken leadership", this.getId());
        client.create().orSetData().withProtection().withMode(CreateMode.EPHEMERAL)
                .forPath(this.getRootPath() + "/" + this.getId(), this.data);

        new Thread(() -> {
            ThreadUtils.sleep(10000);
            releaseLeadership();
        }).start();

        keepLeadership();
    }
}
