package onight.osgi.otransio.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.exception.TransIOException;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.nio.PacketTuple;
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
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
public class RemoteNSession extends PSession {

    private final TransIOException CLOSED_EX = new TransIOException("session closed.");

    private static Comparator<PacketTuple> fpComparator = new Comparator<PacketTuple>() {
        @Override
        public int compare(PacketTuple o1, PacketTuple o2) {
            return o1.getPack().getFixHead().getPrio()-o2.getPack().getFixHead().getPrio();
        }
    };
    private final String rand = "r_" + String.format("%05d", (int) (Math.random() * 100000)) + "_";
    private AtomicLong idCounter = new AtomicLong(0);
    private String genPackID() {
        return rand + "_" + System.currentTimeMillis() + "_" + idCounter.incrementAndGet();
    }
    private NodeInfo nodeInfo;
    private ChannelGroup channels;
    private NSessionSets nss;
    private EventExecutorGroup eeg;
    private AtomicReference<Iterator<Channel>> channelItRef = new AtomicReference<>();
    private PriorityBlockingQueue<NPacketTuple> writeQ;
    private NClient client;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean isClosed = false;
    private LongAdder qCounter = new LongAdder();
    private ArrayList<NodeInfo> subNodes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public RemoteNSession(NodeInfo nodeInfo, NSessionSets nss, EventExecutorGroup eeg){
        this.nodeInfo = nodeInfo;
        this.nss = nss;
        this.eeg = eeg;
        this.channels = new DefaultChannelGroup(nodeInfo.getNodeName(), eeg.next());
        this.writeQ = new PriorityBlockingQueue(100, RemoteNSession.fpComparator);
        this.client = new NClient(this.nss);
        connect();
    }

    public void changeName(String name){
        nodeInfo.setNodeName(name);
    }
    private void connect() {
        this.client.connect(nodeInfo.getAddr(), nodeInfo.getPort()).addListener(
                (ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        Channel ch = f.channel();
                        //发送登录消息
                        ch.attr(Packets.ATTR_AUTH_KEY).set(nss.selfNodeName());
                        ch.writeAndFlush(Packets.newLogin(nss.getSelf()));
                        channels.add(f.channel());
                        connected.set(true);
                        eeg.submit(() -> flushWriteQ(f.channel()));
                    } else {
                        log.warn("connecting to " + nodeInfo.getAddr() + ":" + nodeInfo.getPort() + " failed", f.cause());
                        if (!isClosed&&!connected.get()) {
                            eeg.next().schedule(this::connect, ParamConfig.RECONNECT_TIME_MS, TimeUnit.MILLISECONDS);
                        }
                    }
                });
    }

    private void flushWriteQ(Channel ch){
        if(isClosed||ch==null){
            return;
        }
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
            return;
        }
        ch.write(pt.getPacket()).addListener(f -> {
            if (f.isSuccess()) {
                pt.setWrited(true);
            }
            else{
                log.debug("write to channel failed", f.cause());
                if(pt.compareAndIncRewriteTImes(ParamConfig.SEND_RETRY_TIMES)) {
                    if(!isClosed) {
                        eeg.schedule(
                                () -> {
                                    //未达到重试次数则重新加入待发送队列
                                    pt.setWrited(false);
                                    nss.removeCachePack(getPacketId(pt));
                                    String newId = genPackID();
                                    changePacketId(pt.getPacket(), newId);
                                    nss.addCachePack(newId, pt);
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

    public RemoteNSession addChannel(Channel channel){
        this.channels.add(channel);
        return this;
    }

//    public RemoteNSession removeChannel(Channel channel){
//        this.channels.remove(channel);
//        return this;
//    }

    public void parseUri(String uri){
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

    @Override
    public void onPacket(FramePacket pack, CompleteHandler handler) {
        if(isClosed){
            if (handler!=null){
                handler.onFailed(new TransIOException("s"));
            }
            return;
        }
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
                writeToChannel(ch, pt);
            }
            else{
                writeQ.offer(pt);
                qCounter.increment();
                eeg.submit(()->flushWriteQ(ch));
            }
        }
        else{
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

    private Channel nextChannel(){
        Iterator<Channel> it = channelItRef.get();
        if(it==null){
            it = this.channels.iterator();
            boolean ret = channelItRef.compareAndSet(null, it);
            if(!ret){
                it = channelItRef.get();
            }
        }
        Channel ch = it.next();
        if(ch==null||!ch.isActive()){
            it = this.channels.iterator();
            channelItRef.set(it);
        }
        else{
            return ch;
        }
        ch = it.next();
        return ch;
    }
}
