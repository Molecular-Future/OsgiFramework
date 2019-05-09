package onight.osgi.otransio.netty;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.nio.PacketTuple;
import onight.osgi.otransio.sm.RemoteModuleBean;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.async.CallBack;
import onight.tfw.otransio.api.MessageException;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import onight.tfw.outils.serialize.UUIDGenerator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.fc.zippo.dispatcher.IActorDispatcher;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class NSessionSets {
    private NSocketImpl socket;
    private Cache<String, NPacketTuple> waitResponsePacks;
    private IActorDispatcher dispatcher;
    private EventExecutorGroup eeg;
    private LocalModuleManager localSessions;
    private RemoteSessionManager remoteSessions;
    @Getter
    private String packIDKey;

    @Getter
    private RemoteModuleBean self = new RemoteModuleBean();

    NSessionSets(NSocketImpl socket, IActorDispatcher dispatcher){
        this.socket = socket;
        this.packIDKey = UUIDGenerator.generate() + ".SID";
        this.waitResponsePacks = buildPacketCache();
        this.dispatcher =dispatcher;
        this.eeg = new DefaultEventExecutorGroup(ParamConfig.NSOCK_THREAD_COUNT);
        this.remoteSessions = new RemoteSessionManager(this, this.eeg);
        this.localSessions = new LocalModuleManager(this, this.dispatcher);
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
        if(node==null||node.getNodeName()==null){
            log.warn("nodeInfo is null or name is null.");
            return null;
        }
        if(isLocalSession(node)){
            return localSessions.session(key, node);
        }
        else{
            return remoteSessions.session(key, node);
        }
    }

    public LocalModuleSession localSession(String key){
        return (LocalModuleSession)localSessions.session(key);
    }
    public RemoteNSession remoteSession(String key){
        return (RemoteNSession)remoteSessions.get(key);
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

    private Cache<String, NPacketTuple> buildPacketCache(){
        return CacheBuilder.newBuilder()
                .maximumSize(ParamConfig.PACK_CAHCE_MAXSIZE)
                .expireAfterAccess(ParamConfig.RESEND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .removalListener(rmv->{
                    if(rmv.wasEvicted()){
                        //处理超时的请求
                        String key = (String)rmv.getKey();
                        PacketTuple pt = (PacketTuple)rmv.getValue();
                        if(pt == null || pt.getHandler() == null){
                            return;
                        }
                        if(log.isWarnEnabled()){
                            logTimeoutPacket(key, pt);
                        }
                        pt.getHandler().onFailed(new TimeoutException("pack send timeout"));
                    }
                })
                .build();
    }
    private void logTimeoutPacket(String key, PacketTuple pt) {
        try {
            String times[] = key.split("_");
            if (times.length > 2) {
                long startTime = Long.parseLong(times[times.length - 2]);
                long duration = System.currentTimeMillis() - startTime;
                if (pt != null && pt.getPackQ() != null) {
                    log.debug("remove timeout sync pack:" + key + ",past["
                            + (System.currentTimeMillis() - startTime) + "]" + ",pt,name="
                            + pt.getPackQ().getName() + ",pt.uri=" + pt.getPackQ().getCkpool().getIp() + ":"
                            + pt.getPackQ().getCkpool().getPort() + ",handler==" + pt.getHandler());
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
