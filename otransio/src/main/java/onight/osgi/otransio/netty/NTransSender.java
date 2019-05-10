package onight.osgi.otransio.netty;

import io.netty.util.concurrent.Promise;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.exception.PackException;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.async.CallBack;
import onight.tfw.async.CompleteHandler;
import onight.tfw.async.FutureSender;
import onight.tfw.otransio.api.MessageException;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.PSession;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@AllArgsConstructor
@Slf4j
public class NTransSender extends FutureSender {

    private NSessionSets nss;

    @Override
    public FramePacket send(FramePacket framePacket, long timeoutMS) {
        Promise<FramePacket> promise = sendToSession(framePacket);
        FramePacket pack;
        try {
            pack = promise.get(timeoutMS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pack = PacketHelper.toPBErrorReturn(framePacket, "-101", e.getMessage());
            log.warn("package send timeout::", e);
        } catch (ExecutionException e) {
            pack = PacketHelper.toPBErrorReturn(framePacket, "-102", e.getMessage());
            log.warn("package send execution error::", e);
        } catch (InterruptedException e) {
            pack = PacketHelper.toPBErrorReturn(framePacket, "-103", e.getMessage());
            log.warn("package send interrupted error::", e);
            Thread.currentThread().interrupt();
        }
        return pack;
    }

    @Override
    public void asyncSend(FramePacket pack, CallBack<FramePacket> cb) {
        Promise<FramePacket> promise = sendToSession(pack);
        if(cb!=null){
            promise.addListener(f->{
                if(f.isSuccess()){
                    cb.onSuccess((FramePacket)f.get());
                }
                else{
                    Throwable cause = f.cause();
                    MessageException exception;
                    if(cause instanceof MessageException){
                        exception = (MessageException)cause;
                    }
                    else{
                        exception = new MessageException(cause);
                    }
                    log.debug("package asyncSend has error::", exception);
                    cb.onFailed(exception, PacketHelper.toPBErrorReturn(pack, "-100", exception.getMessage()));
                }
            });
        }
    }

    @Override
    public void post(FramePacket framePacket) {
        asyncSend(framePacket, null);
    }

    @Override
    public void tryDropConnection(String nodeName) {
        if(nss.notReady()){
            log.error("tryDropConnection failed: nss not ready.");
            return;
        }
        nss.dropSession(nodeName, true);
    }

    @Override
    public void setDestURI(String nodeName, String uri) {
        if(nss.notReady()){
            log.error("setDestURI failed: nss not ready.");
            return;
        }
        RemoteNSession session = nss.getRemoteSession(nodeName);
        if(session!=null){
            session.parseUri(uri);
        }
    }

    @Override
    public void setCurrentNodeName(String s) {
        if(nss.notReady()){
            log.error("setDestURI failed: nss not ready.");
            while(nss.notReady()){
                try {
                    Thread.sleep(50);
                }
                catch (InterruptedException e){
                    log.error("Thread interrupted::", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if(!nss.notReady()){
            nss.changeSelfNodeName(s);
        }
        else{
            throw new MessageException("nss not ready and Thread interrupted!!!");
        }
    }

    @Override
    public void changeNodeName(String oldName, String newName) {
        if(nss.notReady()){
            log.error("changeNodeName nss not ready.");
            return;
        }
        nss.changeRemoteSessionName(oldName, newName);
    }


    private Promise<FramePacket> sendToSession(FramePacket pack){
        final Promise<FramePacket> promise = nss.newPromise();

        if(nss.notReady()){
            log.error("sendToSession nss not ready");
            promise.tryFailure(new PackException("nss not ready."));
            return promise;
        }

        //查找对应的PSession
        NodeInfo node = null;
        String destTo = pack.getExtStrProp(PackHeader.PACK_TO);
        String uri = pack.getExtStrProp(PackHeader.PACK_URI);
        if (uri == null) {
            uri = destTo;
        }
        if (StringUtils.isNotBlank(uri)) {
            node = NodeInfo.fromURI(uri, destTo);
        }

        PSession ms = nss.session(destTo, node);

        if(ms!=null){
            //生成待发送数据
            pack.genBodyBytes();
            //如果找到PSession，则执行
            ms.onPacket(pack, new CompleteHandler() {
                @Override
                public void onFinished(FramePacket framePacket) {
                    promise.trySuccess(framePacket);
                }
                @Override
                public void onFailed(Exception e) {
                    promise.tryFailure(e);
                }
            });
        }
        else{
            //PSession不存在，直接失败
            String msg = String.format("session not found, to=%s, uri=%s", destTo, uri);
            promise.tryFailure(new PackException(msg));
        }
        return promise;
    }
}
