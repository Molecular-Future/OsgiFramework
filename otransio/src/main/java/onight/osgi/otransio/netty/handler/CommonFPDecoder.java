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
        RemoteModuleBean rmb = msg.parseBO(RemoteModuleBean.class);
        String nodeFrom = msg.getExtStrProp(PackHeader.PACK_FROM);
        if (StringUtils.isBlank(nodeFrom)) {
            nodeFrom = rmb.getNodeInfo().getNodeName();
        } else {
            rmb.getNodeInfo().setNodeName(nodeFrom);
        }
        if (nodeFrom != null) {
            PSession session = nss.session(nodeFrom, rmb.getNodeInfo());
            if (session instanceof RemoteNSession) {
                //如果已经有session，则把连接加入到session
                RemoteNSession rns = (RemoteNSession) session;
                rns.addChannel(ctx.channel());
                String old = ctx.channel().attr(Packets.ATTR_AUTH_KEY).setIfAbsent(rmb.getNodeInfo().getNodeName());
                if(old!=null){
                    log.error("receive duplication login message! ch={}", ctx.channel());
                    ctx.channel().close();
                }
            }
            else{
                log.error("remote session not found: name={}, ch={}", rmb.getNodeInfo().getNodeName(), ctx.channel());
                ctx.channel().close();
            }
        } else {
            log.debug("nodeFrom is null.");
        }
    }

    private void processDrop(ChannelHandlerContext ctx, FramePacket msg) {
        log.error("get drop connection message");
        String  name = ctx.channel().attr(Packets.ATTR_AUTH_KEY).get();
        if (name != null) {
            nss.dropSession(name, false);
        }
        ctx.close();
    }
}
