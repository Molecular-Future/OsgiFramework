package onight.osgi.otransio.netty;

import lombok.AllArgsConstructor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import org.fc.zippo.dispatcher.IActorDispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
public class LocalModuleManager extends PSessionManager<NodeInfo> {
    private NSessionSets nss;
    private IActorDispatcher dispatcher;

    @Override
    protected PSession createSession(String key, NodeInfo node) {
        return new LocalModuleSession(key);
    }
}
