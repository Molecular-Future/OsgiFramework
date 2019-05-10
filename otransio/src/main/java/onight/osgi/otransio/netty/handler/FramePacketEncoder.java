package onight.osgi.otransio.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import onight.tfw.otransio.api.beans.FramePacket;

@Slf4j
public class FramePacketEncoder extends MessageToByteEncoder<FramePacket> {
    private static final String LOG_TIME_SENT = "T__LOG_SENT";
    @Override
    protected void encode(ChannelHandlerContext ctx, FramePacket msg, ByteBuf out) throws Exception {
        //序列化消息体
        byte[] body = msg.genBodyBytes();

        //扩展头中补充发送时间
        long senttime = System.currentTimeMillis();
        msg.putHeader(LOG_TIME_SENT, "" + senttime);
        //序列化扩展头
        byte[] ext = msg.genExtBytes();

        //处理消息头，更新扩展头和消息体的字节数
        msg.getFixHead().setExtsize(ext.length);
        msg.getFixHead().setBodysize(body.length);
        //序列化消息头
        byte[] header = msg.getFixHead().genBytes();

        //写入到发送缓存
        out.writeBytes(header);
        if(ext.length>0){
            out.writeBytes(ext);
        }
        if(body.length>0){
            out.writeBytes(ext);
        }

        //for debug
        if(log.isDebugEnabled()){
            log.debug("netty trans send gcmd:{}{},bodysize {},sent@{},resp={},sync={},prio={}",
                    msg.getFixHead().getCmd(), msg.getFixHead().getModule(),
                    msg.getFixHead().getBodysize(), senttime, msg.getFixHead().isResp(),
                    msg.getFixHead().isSync(),msg.getFixHead().getPrio());
        }
    }
}
