package onight.osgi.otransio.netty;

import io.netty.util.concurrent.EventExecutorGroup;
import lombok.AllArgsConstructor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.otransio.api.session.PSession;

@AllArgsConstructor
public class RemoteSessionManager extends PSessionManager<NodeInfo> {

    private NSessionSets nss;
    private EventExecutorGroup eeg;

    public void putIfAbsent(String key, RemoteNSession session){
        RemoteNSession oldSession = (RemoteNSession)sessions.putIfAbsent(key, session);
        if(oldSession!=null){
            oldSession.closeSession(false);
        }
    }

    @Override
    protected PSession createSession(String key, NodeInfo node) {
        return new RemoteNSession(node, nss, eeg);
    }
}
