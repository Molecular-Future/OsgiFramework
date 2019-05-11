package onight.osgi.otransio.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.netty.NSessionSets;
import onight.osgi.otransio.netty.RemoteNSession;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.LocalModuleSession;

@Slf4j
@AllArgsConstructor
public class PacketHandler extends SimpleChannelInboundHandler<FramePacket> {

    private NSessionSets nss;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FramePacket pack) throws Exception {
        CompleteHandler handler = null;
        log.debug("receive request pack, vkvs={}, gcmd={}",
                pack.getExtHead().getVkvs(), pack.getModuleAndCMD());
        if (pack.isSync()) {

            final String packfrom = pack.getExtStrProp(PackHeader.PACK_FROM);

            handler = new CompleteHandler() {
                @Override
                public void onFinished(FramePacket vpacket) {
                    vpacket.getExtHead().reset();
                    vpacket.getExtHead().genBytes();
                    vpacket.getFixHead().setResp(true);
                    try {
                        if (ctx.channel().isWritable()) {
                            log.debug("sync message response to vkvs={}, conn={},bcuid={},gcmd={}",
                                    vpacket.getExtHead().getVkvs(), ctx.channel(), packfrom, vpacket.getModuleAndCMD());
                            vpacket.putHeader(PackHeader.PACK_FROM, nss.selfNodeName());
                            ctx.channel().writeAndFlush(vpacket);
                        } else {
                            log.error("sync message response to new vkvs={}, conn={},,bcuid={},packgcmd={}",
                                    vpacket.getExtHead().getVkvs(), ctx.channel(), packfrom + vpacket.getModuleAndCMD());
                            // log.debug("get Pack callback from :" + packfrom);
                            vpacket.putHeader(PackHeader.PACK_TO, packfrom);
                            vpacket.getFixHead().setSync(false);
                            RemoteNSession session = nss.getRemoteSession(packfrom);
                            if (session != null) {
                                session.onPacket(vpacket, NSessionSets.NIL_COMPLETE_HANDLER);
                            } else {
                                log.error("drop response packet:" + pack.getModuleAndCMD() + ",packfrom=" + packfrom);
                            }
                        }
                        //
                    } catch (Exception e) {
                        log.error("write back error:" + vpacket + ",pack=" + pack + ",ctx=" + ctx + ",filter="
                                + ctx.channel(), e);
                    }
                }

                @Override
                public void onFailed(Exception error) {
                    log.error("sync callback error:" + pack, error);
                    ctx.channel().write(PacketHelper.toPBErrorReturn(pack, error.getLocalizedMessage(), error.getMessage()));
                }
            };
        } else {
            handler = null;
        }
        try {
            LocalModuleSession lms = nss.getLocalSession(pack.getModule());
            log.debug("get lms gcmd:{}{}, lms:{}",
                    pack.getModule(), pack.getCMD(), lms);
            if(lms!=null){
                lms.onPacket(pack, handler);
            }
            else{
                log.warn("lms not found. gcmd:{}", pack.getModuleAndCMD());
            }

        } catch (Throwable t) {
            log.error("error in process pack:" + pack.getCMD() + "" + pack.getModule() + ",conn=" + ctx.channel()
                    + ":pack=" + pack, t);
        }
    }
}
