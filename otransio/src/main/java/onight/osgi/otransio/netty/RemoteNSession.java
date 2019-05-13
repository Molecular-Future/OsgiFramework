package onight.osgi.otransio.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.exception.TransIOException;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.util.Packets;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.ExtHeader;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.PSession;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
public class RemoteNSession extends PSession {

    private final TransIOException CLOSED_EX = new TransIOException("session closed.");
    private static final Integer DEF_V = 1;
    private static Comparator<NPacketTuple> fpComparator = Comparator.comparingInt(o -> o.getPacket().getFixHead().getPrio());
    private final String rand = "r_" + String.format("%05d", (int) (Math.random() * 100000)) + "_";
    private AtomicLong idCounter = new AtomicLong(0);

    private String genPackID() {
        return rand + "_" + System.currentTimeMillis() + "_" + idCounter.incrementAndGet();
    }
    private NodeInfo nodeInfo;
    private ChannelGroup channels;
    private NSessionSets nss;
    private EventExecutorGroup eeg;
    @Getter
    private final long createTimestamp = System.currentTimeMillis();

    private PriorityBlockingQueue<NPacketTuple> writeQ;
    private NClient client;
    private AtomicBoolean connecting= new AtomicBoolean(false);
    private AtomicInteger reConnectCount = new AtomicInteger(ParamConfig.RECONNECT_COUNT);
    private final boolean reConnectHasLimit = (ParamConfig.RECONNECT_COUNT>0);
    @Getter
    private volatile boolean isClosed = false;
    private LongAdder qCounter = new LongAdder();
    private ArrayList<NodeInfo> subNodes = new ArrayList<>();

//    private AtomicReference<Iterator<Channel>> channelItRef = new AtomicReference<>();
//    private LongAdder idxAdder = new LongAdder();
//    private ConcurrentMap<ChannelId, Integer> activeChannels = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public RemoteNSession(NodeInfo nodeInfo, NSessionSets nss, EventExecutorGroup eeg){
        this.nodeInfo = nodeInfo;
        this.nss = nss;
        this.eeg = eeg;
        this.channels = new DefaultChannelGroup(nodeInfo.getNodeName(), eeg.next());
        this.writeQ = new PriorityBlockingQueue(ParamConfig.SEND_WRITEQ_INIT, RemoteNSession.fpComparator);
        this.client = new NClient(this.nss);
        //处理连接和重连
        this.eeg.scheduleAtFixedRate(()->{
            if(isClosed||channels.size()>0){
                return;
            }
            connect();
        }, 0, ParamConfig.RECONNECT_TIME_MS, TimeUnit.MILLISECONDS);
        //处理待发送队列
        this.eeg.scheduleWithFixedDelay(()->{
            if(isClosed||channels.size()==0){
                return;
            }
            Channel ch = nextChannel();
            if(ch!=null){
                flushWriteQ(ch);
            }
        }, ParamConfig.SEND_WRITEQ_CHECK_DELAY, ParamConfig.SEND_WRITEQ_CHECK_DELAY, TimeUnit.MILLISECONDS);
    }

    public void changeName(String name){
        nodeInfo.setNodeName(name);
    }
    private void connect() {
        if(!connecting.compareAndSet(false, true)){
            return;
        }
        if(reConnectHasLimit && reConnectCount.getAndDecrement()<=0){
            //如果达到重试次数，则关闭session，并且清理空的session
            closeSession(false);
            nss.cleanRemoteSession();
        }
        log.debug("begin connect node:{}",nodeInfo);
        this.client.connect(nodeInfo.getAddr(), nodeInfo.getPort()).addListener(
                (ChannelFutureListener) f -> {
                    try {
                        if (f.isSuccess()) {
                            Channel ch = f.channel();
                            log.debug("connect success, node:{}, ch:{}", nodeInfo, ch);
                            //发送登录消息
                            ch.attr(Packets.ATTR_AUTH_KEY).set(nss.selfNodeName());
                            ch.writeAndFlush(Packets.newLogin(nss.getSelf()));
                            channels.add(f.channel());
                            eeg.submit(() -> flushWriteQ(f.channel()));
                            //重置重试次数
                            if(reConnectHasLimit){
                                reConnectCount.set(ParamConfig.RECONNECT_COUNT);
                            }
                        } else {
                            log.debug("connecting to {}:{} failed: {}", nodeInfo.getAddr(), nodeInfo.getPort(), f.cause());
                        }
                    }
                    finally {
                        connecting.set(false);
                    }
                });
    }

    private void flushWriteQ(Channel ch){
        if(isClosed||ch==null||writeQ.size()==0){
//            log.debug("rns state invalid");
            return;
        }
        log.debug("do flush write q, size:{}",writeQ.size());
        NPacketTuple pt = null;
        boolean hasWrite = false;
        while((pt=writeQ.poll())!=null){
            qCounter.decrement();
            writeToChannel(ch, pt, false);
            hasWrite = true;
        }
        if(hasWrite){
            ch.flush();
        }
    }

    private void writeToChannel(Channel ch, NPacketTuple pt, boolean withFlush) {
        if(isClosed||pt.isWrited()){
            log.debug("rns state invalid");
            return;
        }
        ch.write(pt.getPacket()).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("write pack success, packid:{}", getPacketId(pt));
                pt.setWrited(true);
            }
            else{
                log.debug("write to channel failed", f.cause());
                if(pt.compareAndIncRewriteTImes(ParamConfig.SEND_RETRY_COUNT)) {
                    if(!isClosed) {
                        eeg.schedule(
                                () -> {
                                    log.debug("pack resend begin, packid:{}", getPacketId(pt));
                                    //未达到重试次数则重新加入待发送队列
                                    pt.setWrited(false);
                                    nss.removeCachePack(getPacketId(pt));
                                    String newId = genPackID();
                                    changePacketId(pt.getPacket(), newId);
                                    nss.addCachePack(newId, pt);

                                    log.debug("pack resend write to queue, packid:{}", getPacketId(pt));

                                    writeQ.offer(pt);
                                    qCounter.increment();
                                }, ParamConfig.SEND_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                    }
                    else{
                        if(pt.getHandler()!=null){
                            pt.getHandler().onFailed(CLOSED_EX);
                        }
                    }
                }
                else{
                    CompleteHandler completeHandler = pt.getHandler();
                    if(completeHandler!=null){
                        FramePacket retPkg = PacketHelper.toPBErrorReturn(
                                pt.getPacket(), "-104", "package send and retry, failed.");
                        completeHandler.onFinished(retPkg);
                    }
                }
            }
        });
        if(withFlush){
            ch.flush();
        }
    }
    private void writeToChannel(Channel ch, NPacketTuple pt){
        writeToChannel(ch, pt, true);
    }

    public RemoteNSession addChannel(Channel channel) {
        log.debug("add to session, node:{}, ch:{}", nodeInfo, channel);
//        activeChannels.put(channel.id(), DEF_V);
        this.channels.add(channel);
        //处理登录超时
        this.eeg.schedule(() -> {
                    if(!channel.hasAttr(Packets.ATTR_AUTH_KEY)){
                        channel.close();
                    }
                },
                ParamConfig.NO_AUTH_TIMEOUT_SEC, TimeUnit.SECONDS);
        return this;
    }

    public void mergeChannels(RemoteNSession session){
        channels.addAll(session.channels);
        session.channels.clear();
    }

    public void parseUri(String uri){
        log.debug("parse uri: {}", uri);
        subNodes.clear();
        for (String str : uri.split(",")) {
            if (!StringUtils.isBlank(str.trim())) {
                try {
                    NodeInfo newin = NodeInfo.fromURI(str, this.nodeInfo.getNodeName());
                    subNodes.add(newin);
                } catch (Exception e) {
                    log.debug("add sub nodes error::", e);
                }
            }
        }
    }

    public void closeSession(boolean sendDDNode){
        log.debug("session closing, node:{}, sendDDNode:{}", nodeInfo, sendDDNode);
        isClosed = true;
        if(!sendDDNode){
            channels.close();
        }
        else {
            channels.writeAndFlush(Packets.DROP_CONN);
        }
        NPacketTuple pt = null;
        while((pt = writeQ.poll())!=null){
            if(pt.getHandler()!=null){
                pt.getHandler().onFailed(CLOSED_EX);
            }
        }
    }

    public int channelCount(){
        return this.channels.size();
    }

    @Override
    public void onPacket(FramePacket pack, CompleteHandler handler) {
        if(isClosed){
            log.debug("rns state invalid");
            if (handler!=null){
                handler.onFailed(new TransIOException("node:"+nodeInfo+",pack:"+pack.getExtHead().getVkvs()));
            }
            return;
        }
//        log.debug("begin on packet");
        String packId = null;
        NPacketTuple pt = new NPacketTuple(pack, handler);
        if(pack.isSync() && handler!=null){
            packId = genPackID();
            changePacketId(pack, packId);
            nss.addCachePack(packId, pt);
        }
        Channel ch = nextChannel();
        if(ch!=null){
            if(qCounter.longValue()==0){
                log.debug("direct send, node:{}, gcmd:{}{}, packid:{}, ch:{}",
                        nodeInfo, pt.getPacket().getModule(), pt.getPacket().getCMD(), getPacketId(pt), ch);
                writeToChannel(ch, pt);
            }
            else{
                log.debug("queue send and channel not null, node:{}, gcmd:{}{}, packid:{}, ch:{}",
                        nodeInfo, pt.getPacket().getModule(), pt.getPacket().getCMD(), getPacketId(pt),ch);
                writeQ.offer(pt);
                qCounter.increment();
                eeg.submit(()->flushWriteQ(ch));
            }
        }
        else{
            log.debug("queue send and channel is null, node:{}, gcmd:{}{}, packid:{}",
                    nodeInfo, pt.getPacket().getModule(), pt.getPacket().getCMD(), getPacketId(pt));
            writeQ.offer(pt);
            qCounter.increment();
        }
    }

    private void changePacketId(FramePacket pack, String packId){
        ExtHeader extHeader = pack.getExtHead();
        if(extHeader!=null){
            extHeader.remove(nss.getPackIDKey());
        }
        pack.putHeader(nss.getPackIDKey(), packId);
        Object to_pack = pack.getExtHead().remove(PackHeader.PACK_TO);
        if (to_pack != null) {
            pack.getExtHead().append(PackHeader.PACK_TO + "_D", to_pack);
        }
    }
    private String getPacketId(NPacketTuple pt){
        return (String)pt.getPacket().getExtHead().get(nss.getPackIDKey());
    }

    @Override
    public String toString() {
        return "RemoteNSession(" + nodeInfo.getNodeName() + ")";
    }

    private Channel nextChannel() {

        try {
            Channel ch = channels.iterator().next();
            if (ch != null && ch.isActive()) {
                return ch;
            }
        } catch (Exception e) {
            log.debug("get channel ex:{}", e.getMessage());
        }
        return null;
    }


    public String getJsonStr(){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(nodeInfo.getNodeName()).append("\"");
        sb.append(",\"addr\":\"").append(nodeInfo.getAddr()).append("\"");
        sb.append(",\"port\":").append(nodeInfo.getPort());
        sb.append(",\"qsize\":").append(writeQ.size());
        int sz = channels.size();
        sb.append(",\"chsize\":").append(sz);
        if(sz>0) {
            sb.append(",\"channels\":[");
            int i = 0;
            for (Channel ch : channels) {
                if (ch != null) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    i++;
                    sb.append("{");
                    sb.append("\"id\":\"").append(ch.id()).append("\"");
                    sb.append(",\"local\":\"").append(ch.localAddress()).append("\"");
                    sb.append(",\"remote\":\"").append(ch.remoteAddress()).append("\"");
                    sb.append(",\"isOpen\":\"").append(ch.isOpen()).append("\"");
                    sb.append("}");
                }
            }
            sb.append("]");
        }
        sb.append("}");

        return sb.toString();
    }
}
