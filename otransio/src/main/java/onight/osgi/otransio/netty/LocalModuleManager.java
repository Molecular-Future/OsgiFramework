package onight.osgi.otransio.netty;

import onight.tfw.otransio.api.session.LocalModuleSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalModuleManager {

    private Map<String, LocalModuleSession> sessions = new HashMap<>();

    LocalModuleSession addModuleSession(String module){
        LocalModuleSession lms = sessions.get(module);
        if(lms==null){
            lms = new LocalModuleSession(module);
            sessions.put(module, lms);
        }
        return lms;
    }
}
