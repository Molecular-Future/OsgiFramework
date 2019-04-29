package onight.osgi.otransio.netty.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.util.Packets;
import onight.tfw.otransio.api.beans.FramePacket;

public class HeartBeatHandler extends SimpleChannelInboundHandler<FramePacket> {

    @Getter
    private NodeInfo selfNode = new NodeInfo();

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //TODO get node name
        FramePacket hb = Packets.newHB(selfNode.getNodeName());
        ctx.writeAndFlush(hb);
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FramePacket msg) throws Exception {
        ctx.fireChannelRead(msg);
    }
}
