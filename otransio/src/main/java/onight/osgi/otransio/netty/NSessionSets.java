package onight.osgi.otransio.netty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.nio.PacketTuple;
import onight.osgi.otransio.sm.RemoteModuleBean;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import onight.tfw.outils.conf.PropHelper;
import onight.tfw.outils.serialize.UUIDGenerator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class NSessionSets {
    NSocket socket;
    PropHelper params;
    Cache<String, PacketTuple> packsCache;

    RemoteModuleBean self = new RemoteModuleBean();

    @Getter
    String packIDKey;

    Map<String, LocalModuleSession> localModules = new HashMap<>();
    NodeInfo nodeInfo = new NodeInfo();


    public String selfNodeName(){
        return self.getNodeInfo().getNodeName();
    }

    public PacketTuple removeCachePack(String packId){
        PacketTuple pt = packsCache.getIfPresent(packId);
        packsCache.invalidate(packId);
        return pt;
    }

    public PSession byNodeName(String nodeName){
        return null;
    }

    public void dropSession(String nodeName){
        //TODO 关闭session
    }


    NSessionSets(NSocket socket, PropHelper params){
        this.socket = socket;
        this.params = params;
        packIDKey = UUIDGenerator.generate() + ".SID";
        packsCache = buildPacketCache();
    }

    private Cache<String, PacketTuple> buildPacketCache(){
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

    LocalModuleSession addLocalModule(String module){
        LocalModuleSession lms = localModules.get(module);
        if(lms==null){
            lms = new LocalModuleSession(module);
            localModules.put(module, lms);
        }
        return lms;
    }












    String getSimpleJsonInfo(){
        return "{}";
    }
    String getJsonInfo(){
        return "{}";
    }
}
