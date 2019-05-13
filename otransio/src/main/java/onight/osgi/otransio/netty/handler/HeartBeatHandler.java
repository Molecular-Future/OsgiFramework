package onight.osgi.otransio.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.netty.NSessionSets;
import onight.osgi.otransio.util.Packets;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.beans.FramePacket;

@Slf4j
public class HeartBeatHandler extends IdleStateHandler {

    private NSessionSets nss;
    private boolean enable;

    public HeartBeatHandler(NSessionSets nss, boolean enable, int idleTimeSeconds) {
        super(0, 0, idleTimeSeconds);
        this.enable = enable;
        this.nss = nss;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            if(enable) {
                //发送心跳消息
                FramePacket hb = Packets.newHB(nss.selfNodeName());
                ctx.writeAndFlush(hb);
                super.userEventTriggered(ctx, evt);
            }
            else{
                //如果enable为false，则在空闲时关闭连接
                log.debug("channel idle timeout close.ch:{}",ctx.channel());
                ctx.channel().close();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof FramePacket){
            FramePacket fp = (FramePacket)msg;
            if(PackHeader.CMD_HB.equals(fp.getModuleAndCMD())){
                //TODO 处理心跳消息
                if(log.isDebugEnabled()){
                    log.debug("[HB] From {} to {}", ctx.channel().remoteAddress(), ctx.channel().localAddress());
                }
                return;
            }
        }
        //如果为其他消息则不处理
        super.channelRead(ctx, msg);
    }
}
