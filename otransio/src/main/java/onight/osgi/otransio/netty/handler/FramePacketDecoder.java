package onight.osgi.otransio.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.util.Packets;
import onight.tfw.otransio.api.MessageException;
import onight.tfw.otransio.api.beans.ExtHeader;
import onight.tfw.otransio.api.beans.FixHeader;
import onight.tfw.otransio.api.beans.FramePacket;

import java.util.List;

@Slf4j
public class FramePacketDecoder extends ReplayingDecoder<FramePacketDecoderState> {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final ExtHeader EMPTY_EXT_HEADER = new ExtHeader();


    private FixHeader header = null;
    private ExtHeader extHeader = null;
    private byte[] body = null;

    public FramePacketDecoder(){
        super(FramePacketDecoderState.READ_HEADER);
    }
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("decode begin, state:{}, ch:{}, in:{}",
                    state(), ctx.channel(), in);
        }

        switch(state()){
            //解析消息头
            case READ_HEADER: {
                boolean headerCheckResult;
                try {
                    header = FixHeader.parseFrom(ByteBufUtil.getBytes(in.readBytes(FixHeader.LENGTH)));
                    headerCheckResult = checkFixHeader();
                } catch (MessageException me) {
                    log.warn("fixHeader invalid, error::", me);
                    headerCheckResult = false;
                }
                //如果头不正确，则直接关闭连接
                if (!headerCheckResult) {
                    log.warn("frame format invalid, close connection: {}", ctx.channel());
                    ctx.close();
                    break;
                }
                //判断是否需要解析扩展头和消息体，不需要的话直接返回消息
                if (header.getExtsize() > 0) {
                    checkpoint(FramePacketDecoderState.READ_EXT_HEADER);
                } else if (header.getBodysize() > 0) {
                    checkpoint(FramePacketDecoderState.READ_CONTENT);
                } else {
                    decodeDone(ctx.channel(),out);
                }
            } break;
            //解析扩展头
            case READ_EXT_HEADER: {
                extHeader = ExtHeader.buildFrom(ByteBufUtil.getBytes(in.readBytes(header.getExtsize())));
                //判断是否需要解析消息体，不需要的话直接返回
                if (header.getBodysize() > 0) {
                    checkpoint(FramePacketDecoderState.READ_CONTENT);
                } else {
                    decodeDone(ctx.channel(),out);
                }
            } break;
            //解析消息体
            case READ_CONTENT: {
                ByteBuf bf = in.readBytes(header.getBodysize());
                body = ByteBufUtil.getBytes(bf);
                bf.readBytes(body);
                decodeDone(ctx.channel(),out);
            } break;
            default:
                log.error("unknown state error, closed connection:{}", ctx.channel());
                ctx.close();
        }
    }

    private boolean checkFixHeader(){
        if (header.getTotalSize() > 1024 * 1024 * 128 || header.getExtsize() < 0 || header.getBodysize() < 0) {
            return false;
        }
        else{
            return true;
        }
    }

    private void decodeDone(Channel ch, List<Object> out){
        //如果扩展头或消息体不存在，使用空数据
        if(extHeader==null){
            extHeader = EMPTY_EXT_HEADER;
        }
        if(body==null){
            body = EMPTY_BYTES;
        }
        //返回解析好的消息
        FramePacket fp = new FramePacket(header, extHeader, body, header.getCmd() + header.getModule());
        out.add(fp);

        //for debug
        if(log.isDebugEnabled()){
            String sendtime = (String) fp.getExtHead().get(Packets.LOG_TIME_SENT);
            log.debug("netty trans recv gcmd:{}{},bodysize:{},extsize:{}, cost:{} ms,sent={},resp={},sync={},pio={},vkvs={},ch:{}",
                    header.getCmd(), header.getModule(),
                    header.getBodysize(), header.getExtsize(),
                    (System.currentTimeMillis() - Long.parseLong(sendtime)), sendtime, header.isResp(),
                    header.isSync(), header.getPrio(),fp.getExtHead().getVkvs(), ch);
        }

        //重置状态
        header = null;
        extHeader = null;
        body = null;
        checkpoint(FramePacketDecoderState.READ_HEADER);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(log.isDebugEnabled()){
            log.debug("decode exceptions::", cause);
        }
        ctx.close();
        //super.exceptionCaught(ctx, cause);
    }
}
