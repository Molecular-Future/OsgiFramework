package onight.osgi.otransio.netty;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.nio.PacketTuple;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.PSession;

import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteNSession extends PSession {

    private static Comparator<PacketTuple> fpComparator = new Comparator<PacketTuple>() {
        @Override
        public int compare(PacketTuple o1, PacketTuple o2) {
            return o1.getPack().getFixHead().getPrio()-o2.getPack().getFixHead().getPrio();
        }
    };
    private final String rand = "r_" + String.format("%05d", (int) (Math.random() * 100000)) + "_";
    private AtomicLong counter = new AtomicLong(0);
    private String genPackID() {
        return rand + "_" + System.currentTimeMillis() + "_" + counter.incrementAndGet();
    }
    private NodeInfo nodeInfo;
    private ChannelGroup channels;
    private NSessionSets nss;
    private EventExecutorGroup eeg;
    private AtomicReference<Iterator<Channel>> channelItRef = new AtomicReference<>();
    private PriorityBlockingQueue<PacketTuple> writeQ;

    @SuppressWarnings("unchecked")
    public RemoteNSession(NodeInfo nodeInfo, NSessionSets nss, EventExecutorGroup eeg){
        this.nodeInfo = nodeInfo;
        this.nss = nss;
        this.eeg = eeg;
        this.channels = new DefaultChannelGroup(nodeInfo.getNodeName(), eeg.next());
        this.writeQ = new PriorityBlockingQueue(100, RemoteNSession.fpComparator);
    }

    public RemoteNSession addChannel(Channel channel){
        this.channels.add(channel);
        return this;
    }

    public RemoteNSession removeChannel(Channel channel){
        this.channels.remove(channel);
        return this;
    }

    @Override
    public void onPacket(FramePacket pack, CompleteHandler handler) {
        String packId = null;
        if(pack.isSync() && handler!=null){
            packId = genPackID();
            pack.putHeader(nss.getPackIDKey(), packId);
            Object to_pack = pack.getExtHead().remove(PackHeader.PACK_TO);
            if (to_pack != null) {
                pack.getExtHead().append(PackHeader.PACK_TO + "_D", to_pack);
            }
            //TODO 发送消息时，如果为sync的，则需要缓存起来等待返回
//            nss.waitResponsePacks.put(packId,
//                    new PacketTuple(pack, handler,
//                            false, 0, -1,
//                            false, null));
        }

        Channel ch = nextChannel();
        if(ch==null){
            writeQ.offer(new PacketTuple(pack, handler,
                    false, 0, -1,
                    false, null));
        }

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
