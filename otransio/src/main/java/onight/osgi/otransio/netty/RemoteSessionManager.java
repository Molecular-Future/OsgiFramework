package onight.osgi.otransio.netty;

import io.netty.util.concurrent.EventExecutorGroup;
import lombok.AllArgsConstructor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.otransio.api.session.PSession;

@AllArgsConstructor
public class RemoteSessionManager extends PSessionManager<NodeInfo> {

    private NSessionSets nss;
    private EventExecutorGroup eeg;

    @Override
    protected PSession createSession(String key, NodeInfo node) {
        return new RemoteNSession(node, nss, eeg);
    }
}
