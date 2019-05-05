package onight.osgi.otransio.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.netty.NSessionSets;
import onight.osgi.otransio.nio.PacketTuple;
import onight.tfw.otransio.api.beans.FramePacket;

import java.util.List;

@Slf4j
public class ResponseDecoder extends MessageToMessageDecoder<FramePacket> {

    private NSessionSets nss;

    public ResponseDecoder(NSessionSets nss){
        this.nss = nss;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FramePacket msg, List<Object> out) throws Exception {
        //处理所有响应
        if(msg.isResp()){
            if(msg.getExtHead().isExist(nss.getPackIDKey())){
                //通过packId获取缓存的请求报文
                String packId = msg.getExtStrProp(nss.getPackIDKey());
                PacketTuple pt = nss.removeCachePack(packId);
                if(pt!=null){
                    //如果有CompleteHandler，则触发完成事件
                    if(pt.getHandler()!=null){
                        pt.getHandler().onFinished(msg);
                    }
                }
                else{
                    //如果找不到对应的请求，记录错误日志
                    logMissMessage(packId, msg, ctx.channel());
                }
            }
        }
        else{
            //非响应包，交给下一个解码器处理
            out.add(msg);
        }
    }

    private static void logMissMessage(String packId, FramePacket pack, Channel channel) {
        if (pack.getBody() != null && pack.getBody().length > 0) {
            log.error("unknow ack: packId={},gcmd={},ch={},kvs={},hidden={},timepost={}",
                    packId,
                    pack.getModuleAndCMD(),
                    channel,
                    pack.getExtHead().getVkvs(),
                    pack.getExtHead().getHiddenkvs(),
                    getPackTimeout(packId));
        } else {
            log.error("unknow ack: packId={}, gcmd={}, ch={}, timepost={}",
                    packId,
                    pack.getModuleAndCMD(),
                    channel,
                    getPackTimeout(packId));
        }
    }

    private static String getPackTimeout(String key) {
        String times[] = key.split("_");
        if (times.length > 2) {
            long startTime = Long.parseLong(times[times.length - 2]);
            return "" + (System.currentTimeMillis() - startTime);
        }
        return "--not_time_pack--";
    }
}
