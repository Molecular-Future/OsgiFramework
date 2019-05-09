package onight.osgi.otransio.netty;

import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.otransio.api.session.PSession;

import java.util.concurrent.ConcurrentHashMap;

public abstract class PSessionManager<V> {
    protected ConcurrentHashMap<String, PSession> sessions = new ConcurrentHashMap<>();

    public PSession session(String key, V object){
        PSession session = sessions.get(key);
        if(session!=null){
            return session;
        }
        else {
            session = sessions.computeIfAbsent(key, k -> {
                if (sessions.contains(k)) {
                    return sessions.get(k);
                } else {
                    return createSession(key, object);
                }
            });
            return session;
        }
    }

    public PSession session(String key){
        return session(key, null);
    }

    public PSession get(String key){
        return sessions.get(key);
    }

    public PSession removeSession(String key){
        return sessions.remove(key);
    }

    protected abstract PSession createSession(String key, V node);
}
