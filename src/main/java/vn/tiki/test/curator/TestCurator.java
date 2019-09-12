package vn.tiki.test.curator;

import static io.gridgo.utils.ThreadUtils.isShuttingDown;
import static io.gridgo.utils.ThreadUtils.registerShutdownTask;
import static io.gridgo.utils.ThreadUtils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

import io.gridgo.bean.BObject;
import io.gridgo.utils.UuidUtils;
import lombok.extern.slf4j.Slf4j;
import vn.tiki.test.curator.event.leadership.HzLeadershipGlobalEventDispatcher;
import vn.tiki.test.curator.event.leadership.LeaderElectionAgent;
import vn.tiki.test.curator.event.leadership.LeadershipEvent;
import vn.tiki.test.curator.event.leadership.LeadershipGlobalEventDispatcher;

@Slf4j
public class TestCurator {

    private LeaderElectionAgent agent;

    public static void main(String[] args) throws Exception {
        var id = UuidUtils.timebasedUUIDAsString();
        log.info("Starting test curator...");

        log.info("*** start zooKeeper client");
        var client = CuratorFrameworkFactory.builder() //
                .connectString("127.0.0.1:2181") //
                .retryPolicy(new ExponentialBackoffRetry(1000, 3)) //
                .connectionTimeoutMs(10000) //
                .sessionTimeoutMs(5000) //
                .build();

        client.start();
        client.blockUntilConnected(15, SECONDS);
        registerShutdownTask(client::close);

        log.info("*** start hazelcast");
        var config = new Config();
        config.getProperties().put("hazelcast.logging.type", "slf4j");
//        var networkConfig = config.getNetworkConfig();
//        networkConfig.getInterfaces().clear().addInterface("127.0.0.1").setEnabled(true);
//        networkConfig.getJoin().getMulticastConfig().setEnabled(false);
//        networkConfig.getJoin().getTcpIpConfig().addMember("127.0.0.1");

        var hazelcast = Hazelcast.newHazelcastInstance(config);
        var topicName = "vn::tiki:test::curator::leader-election";
        var dispatcher = HzLeadershipGlobalEventDispatcher.builder().topicName(topicName).hazelcast(hazelcast).build();

        log.info("*** start TestCurator application with id: {}", id);
        var rootPath = "/vn/tiki/test/curator/leader-election";
        var app = new TestCurator(client, rootPath, id, dispatcher);
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

    private TestCurator(CuratorFramework client, String rootPath, String id,
            LeadershipGlobalEventDispatcher globalEventDispatcher) {
        var data = BObject.ofSequence("key", "this is value for node " + id).toBytes();
        this.agent = LeaderElectionAgent.builder() //
                .client(client) //
                .rootPath(rootPath) //
                .id(id)//
                .data(data) //
                .globalEventDispatcher(globalEventDispatcher) //
                .build();

        this.agent.subscribeEvent(this::onLeadershipEvent);
    }

    private void onLeadershipEvent(LeadershipEvent event) {
        if (event.getLocalId().equals(event.getLeaderId())) {
            log.debug("I'm the leader");
        } else {
            log.debug("The leader: {}", event.getLeaderId());
        }
    }

    private void start() {
        this.agent.start();
    }

    private void stop() {
        this.agent.stop();
    }
}
