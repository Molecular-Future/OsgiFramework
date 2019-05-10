package onight.osgi.otransio.util;

import io.netty.util.AttributeKey;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.sm.RemoteModuleBean;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FixHeader;
import onight.tfw.otransio.api.beans.FramePacket;

public final class Packets {
    public static final String DROP_CONN = "DROP**";
    public static final String LOG_TIME_SENT = "T__LOG_SENT";
    public static final AttributeKey<String> ATTR_AUTH_KEY = AttributeKey.newInstance("ntrans.rns.auth");

    /**
     * 创建心跳消息
     * @param nodeName 节点名称
     * @return 心跳消息
     */
    public static FramePacket newHB(String nodeName){
//        PacketHelper.genPack(
//                PackHeader.CMD_HB.substring(0,3),
//                PackHeader.CMD_HB.substring(3),null,false,(byte)1);
        FramePacket hbpack = new FramePacket();
        FixHeader header = new FixHeader();
        header.setCmd(PackHeader.CMD_HB.substring(0, 3));
        header.setModule(PackHeader.CMD_HB.substring(3));
        header.setBodysize(0);
        header.setExtsize(0);
        header.setEnctype('T');
        header.genBytes();
        hbpack.setFixHead(header);
        hbpack.putHeader(PackHeader.PACK_FROM, nodeName);
        return hbpack;
    }

    public static FramePacket newLogin(RemoteModuleBean node){
        FramePacket pack = PacketHelper.genSyncPack(PackHeader.REMOTE_LOGIN, PackHeader.REMOTE_MODULE, node);
        pack.putHeader(PackHeader.PACK_FROM, node.getNodeInfo().getNodeName());
        return pack;
    }

    public static FramePacket newDrop(NodeInfo localInfo){
        return null;
    }
}
