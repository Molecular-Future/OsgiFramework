package onight.osgi.otransio.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.netty.NSessionSets;
import onight.osgi.otransio.netty.RemoteNSession;
import onight.osgi.otransio.sm.RemoteModuleBean;
import onight.osgi.otransio.util.Packets;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.PSession;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public class CommonFPDecoder extends MessageToMessageDecoder<FramePacket> {

    private static final AttributeKey<RemoteModuleBean> AUTH_INFO_KEY = AttributeKey.newInstance("nsock.auth.info");

    private NSessionSets nss;

    public CommonFPDecoder(NSessionSets nss){
        this.nss = nss;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FramePacket msg, List<Object> out) throws Exception {
        String gcmd = msg.getModuleAndCMD();
        if(PackHeader.REMOTE_LOGIN.equals(gcmd)){
            processLogin(ctx, msg);
        }
        else if(Packets.DROP_CONN.equals(gcmd)) {
            processDrop(ctx, msg);
        }
        else{
            out.add(msg);
        }
    }

    private void processLogin(ChannelHandlerContext ctx, FramePacket msg){
        //TODO 处理登录消息
        RemoteModuleBean rmb = msg.parseBO(RemoteModuleBean.class);
        String nodeFrom = msg.getExtStrProp(PackHeader.PACK_FROM);
        if (StringUtils.isBlank(nodeFrom)) {
            nodeFrom = rmb.getNodeInfo().getNodeName();
        } else {
            rmb.getNodeInfo().setNodeName(nodeFrom);
        }
        if (nodeFrom != null) {
            PSession session = nss.byNodeName(nodeFrom);
            if (session != null && session instanceof RemoteNSession) {
                //如果已经有session，则把连接加入到session
                RemoteNSession rns = (RemoteNSession) session;

                // rms.getWriterQ().resendBacklogs();
            } else {
                //如果没有session，则创建一个
            }
            RemoteModuleBean old = ctx.channel().attr(AUTH_INFO_KEY).setIfAbsent(rmb);
            if(old!=null){
                //TODO  收到重复的登录消息
            }
        } else {
            log.debug("nodeFrom is null.");
        }
    }

    private void processDrop(ChannelHandlerContext ctx, FramePacket msg) {
        log.error("get drop connection message");
        RemoteModuleBean rmb = ctx.channel().attr(AUTH_INFO_KEY).get();
        if (rmb != null) {
            nss.dropSession(rmb.getNodeInfo().getNodeName());
        }
        ctx.close();
    }
}
