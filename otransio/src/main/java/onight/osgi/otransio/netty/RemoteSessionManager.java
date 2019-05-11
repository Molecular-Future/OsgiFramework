package onight.osgi.otransio.netty;

import io.netty.util.concurrent.EventExecutorGroup;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.otransio.api.session.PSession;

@Slf4j
@AllArgsConstructor
public class RemoteSessionManager extends PSessionManager<NodeInfo> {

    private NSessionSets nss;
    private EventExecutorGroup eeg;

    public void putIfAbsent(String key, RemoteNSession session){
        RemoteNSession oldSession = (RemoteNSession)sessions.compute(key, (k,v)->{
            if(v==null){
                return session;
            }
            else{
                //TODO merge session
                //TODO 直接关闭新的连接是否正确？
                session.closeSession(false);
                return v;
            }

        });
    }

    @Override
    protected PSession createSession(String key, NodeInfo node) {
        return new RemoteNSession(node, nss, eeg);
    }
}
