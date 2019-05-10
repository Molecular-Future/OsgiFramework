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
        if (pack.isSync() && !pack.isResp()) {// 需要等待回应的

            final String packfrom = pack.getExtStrProp(PackHeader.PACK_FROM);

            handler = new CompleteHandler() {
                @Override
                public void onFinished(FramePacket vpacket) {
                    vpacket.getExtHead().reset();
                    vpacket.getExtHead().genBytes();
                    vpacket.getFixHead().setResp(true);
                    try {
                        if (ctx.channel().isWritable()) {
                            log.debug("sync message response to conn=" + ctx.channel() + ",bcuid=" + packfrom + ",packgcmd="
                                    + vpacket.getModuleAndCMD() + "/" + pack.getModuleAndCMD());
                            vpacket.putHeader(PackHeader.PACK_FROM, nss.selfNodeName());
                            ctx.channel().write(vpacket);
                        } else {
                            log.error("sync message response to new conn=" + ctx.channel() + ",bcuid=" + packfrom + ",packgcmd="
                                    + vpacket.getModuleAndCMD() + "/" + pack.getModuleAndCMD());
                            // log.debug("get Pack callback from :" + packfrom);
                            vpacket.putHeader(PackHeader.PACK_TO, packfrom);
                            vpacket.getFixHead().setSync(false);
                            vpacket.getFixHead().setResp(true);
                            RemoteNSession session = nss.getRemoteSession(packfrom);
                            if (session != null) {
                                session.onPacket(vpacket, null);
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
            if(lms!=null){
                lms.onPacket(pack, handler);
            }
        } catch (Throwable t) {
            log.error("error in process pack:" + pack.getCMD() + "" + pack.getModule() + ",conn=" + ctx.channel()
                    + ":pack=" + pack, t);
        }
    }
}
