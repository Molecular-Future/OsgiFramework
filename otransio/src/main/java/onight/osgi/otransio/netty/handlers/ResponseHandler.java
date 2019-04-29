package onight.osgi.otransio.netty.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import onight.tfw.otransio.api.beans.FramePacket;

import java.util.List;

public class ResponseHandler extends MessageToMessageDecoder<FramePacket> {
    @Override
    protected void decode(ChannelHandlerContext ctx, FramePacket msg, List<Object> out) throws Exception {
        //处理响应包
        if(msg.isResp() ){
//            && msg.getExtHead().isExist("")
        }
        else{
            out.add(msg);
        }
    }
}
