package onight.osgi.otransio.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import onight.osgi.otransio.netty.NSessionSets;
import onight.tfw.otransio.api.beans.FramePacket;

@AllArgsConstructor
public class PacketHandler extends SimpleChannelInboundHandler<FramePacket> {

    private NSessionSets nss;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FramePacket msg) throws Exception {
        //TODO 处理CMD消息
    }
}
