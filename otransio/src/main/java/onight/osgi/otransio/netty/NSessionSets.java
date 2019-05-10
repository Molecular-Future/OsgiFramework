package onight.osgi.otransio.netty;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.sm.RemoteModuleBean;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import onight.tfw.outils.serialize.UUIDGenerator;
import org.apache.commons.lang3.StringUtils;
import org.fc.zippo.dispatcher.IActorDispatcher;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class NSessionSets {
    private NSocketImpl socket;
    private Cache<String, NPacketTuple> waitResponsePacks;
    private volatile IActorDispatcher dispatcher = null;
    private EventExecutorGroup eeg;
    private LocalModuleManager localSessions;
    private RemoteSessionManager remoteSessions;
    @Getter
    private String packIDKey;

    @Getter
    private RemoteModuleBean self = new RemoteModuleBean();

    NSessionSets(NSocketImpl socket){
        this.socket = socket;
        this.packIDKey = UUIDGenerator.generate() + ".SID";
        this.waitResponsePacks = buildPacketCache();
        this.eeg = new DefaultEventExecutorGroup(ParamConfig.NSOCK_THREAD_COUNT);
        this.remoteSessions = new RemoteSessionManager(this, this.eeg);
        this.localSessions = new LocalModuleManager(this, this.dispatcher, this.eeg);
    }

    void setDispatcher(IActorDispatcher dispatcher){
        this.dispatcher = dispatcher;
    }
    public boolean notReady(){
        return this.dispatcher==null;
    }

    public String selfNodeName(){
        return self.getNodeInfo().getNodeName();
    }

    public void changeSelfNodeName(String name){
        self.getNodeInfo().setNodeName(name);
    }

    public <T> Promise<T> newPromise(){
        return eeg.next().newPromise();
    }

    public void addCachePack(String packId, NPacketTuple packetTuple){
        waitResponsePacks.put(packId, packetTuple);
    }

    public NPacketTuple removeCachePack(String packId){
        NPacketTuple pt = waitResponsePacks.getIfPresent(packId);
        waitResponsePacks.invalidate(packId);
        return pt;
    }

    public PSession session(String key, NodeInfo node){
        if(isLocalSession(node)){
//            log.debug("get local session, key={}, node={}", key, node);
            return localSessions.session(key, node);
        }
        else{
//            log.debug("get remote session, key={}, node={}", key, node);
            return remoteSessions.session(key, node);
        }
    }

    public LocalModuleSession localSession(String key){
        return (LocalModuleSession)localSessions.session(key);
    }
    public LocalModuleSession getLocalSession(String key){
        return (LocalModuleSession)localSessions.get(key);
    }
    public RemoteNSession getRemoteSession(String key){
        return (RemoteNSession)remoteSessions.get(key);
    }
    public void changeRemoteSessionName(String oldName, String newName){
        if(StringUtils.isBlank(oldName)
                ||StringUtils.isBlank(newName)
                ||StringUtils.equalsIgnoreCase(oldName, newName)){
            return;
        }
        synchronized(this){
            RemoteNSession session = (RemoteNSession)remoteSessions.removeSession(oldName);
            if(session!=null){
                session.changeName(newName);
                remoteSessions.putIfAbsent(newName, session);
            }
        }
    }

    public void dropSession(String nodeName, boolean sendDDNode){
        RemoteNSession rms = (RemoteNSession)remoteSessions.get(nodeName);
        if(rms!=null){
            rms.closeSession(sendDDNode);
        }
        else{
            log.debug("drop unknown session: name={}", nodeName);
        }
    }

    public void shutdown(){
        //TODO nss shutdown
    }

    private Cache<String, NPacketTuple> buildPacketCache(){
        return CacheBuilder.newBuilder()
                .maximumSize(ParamConfig.PACK_CAHCE_MAXSIZE)
                .expireAfterAccess(ParamConfig.SEND_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .removalListener(rmv->{
                    if(rmv.wasEvicted()){
                        //处理超时的请求
                        String key = (String)rmv.getKey();
                        NPacketTuple pt = (NPacketTuple)rmv.getValue();
                        if(pt == null || pt.getHandler() == null){
                            return;
                        }
                        if(log.isWarnEnabled()){
                            logTimeoutPacket(key, pt);
                        }
                        String packId = pt.getPacket().getExtStrProp(this.getPackIDKey());
                        pt.getHandler().onFailed(new TimeoutException("pack send timeout, packId:"+packId));
                    }
                })
                .build();
    }
    private void logTimeoutPacket(String key, NPacketTuple pt) {
        try {
            String times[] = key.split("_");
            if (times.length > 2) {
                long startTime = Long.parseLong(times[times.length - 2]);
                long duration = System.currentTimeMillis() - startTime;
                if (pt != null) {
                    log.debug("remove timeout sync pack:" + key + ",past["
                            + (System.currentTimeMillis() - startTime) + "]" + ",pt,name="
                            + ",handler==" + pt.getHandler());
                } else {
                    log.debug("remove timeout sync pack:" + key + ",past["
                            + (System.currentTimeMillis() - startTime) + "]" + ",pt is null=");
                }
            }
        } catch (Exception e) {
            log.debug("get unknow error when check uri for pack.key=" + key, e);
        }
    }

    private boolean isLocalSession(NodeInfo node){
        //TODO 单机调试时会有问题
//        log.debug("is local ? local={}, node={}", this.self.getNodeInfo(), node);
        return node==null
                || (StringUtils.equalsIgnoreCase(node.getAddr(), this.self.getNodeInfo().getAddr())
                   && node.getPort() == this.self.getNodeInfo().getPort())
                || this.self.getNodeInfo().getNodeName().equals(node.getNodeName());
    }


    String getSimpleJsonInfo(){
        return "{}";
    }
    String getJsonInfo(){
        return "{}";
    }
}
