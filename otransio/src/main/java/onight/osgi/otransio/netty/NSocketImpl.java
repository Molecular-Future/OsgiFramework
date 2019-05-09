package onight.osgi.otransio.netty;

import io.netty.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.ISocket;
import onight.osgi.otransio.exception.NoneServerException;
import onight.osgi.otransio.exception.PackException;
import onight.osgi.otransio.exception.TransIOException;
import onight.osgi.otransio.impl.LocalMessageProcessor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.async.CallBack;
import onight.tfw.async.CompleteHandler;
import onight.tfw.async.FutureSender;
import onight.tfw.otransio.api.IPacketSender;
import onight.tfw.otransio.api.MessageException;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.CMDService;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import onight.tfw.outils.conf.PropHelper;
import org.apache.commons.lang3.StringUtils;
import org.fc.zippo.dispatcher.IActorDispatcher;
import org.osgi.framework.BundleContext;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class NSocketImpl extends FutureSender implements ISocket {

    private IActorDispatcher dispatcher = null;
    private BundleContext context;
    private NServer server;
    private volatile NSessionSets nss;

    transient LocalMessageProcessor localProcessor = new LocalMessageProcessor();

    public NSocketImpl(BundleContext context) {
        this.context = context;
        this.server = new NServer();
    }

    /**
     * 发送消息
     * @param fp 消息
     * @return
     */
    Promise<FramePacket> sendPacket(FramePacket fp){
        return null;
    }

    /**
     * 发送消息，无需等待响应
     * @param fp 消息
     */
    void postPacket(FramePacket fp){

    }

    /**
     * 断开session的所有链接
     * @param nodeName
     */
    void dropSession(String nodeName){

    }

    /**
     * 重命名session
     * @param oldName 原名称
     * @param newName 新名称
     */
    void renameSession(String oldName, String newName){

    }

    @Override
    public IPacketSender packetSender() {
        return this;
    }

    @Override
    public void start(IActorDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.nss = new NSessionSets(NSocketImpl.this, this.dispatcher);
        server.startServer(this.nss);
    }

    @Override
    public void stop() {
        log.debug("NSocket stopping");
        server.stop();
    }

    @Override
    public void bindCMDService(CMDService service) {
        LocalModuleSession ms = nss.localSession(service.getModule());
        for (String cmd : service.getCmds()) {
            ms.registerService(cmd, service);
        }
    }

    @Override
    public void unbindCMDService(CMDService service) {
        //do nothing
    }

    @Override
    public String simpleJsonInfo() {
        return nss.getSimpleJsonInfo();
    }

    @Override
    public String jsonInfo() {
        return nss.getJsonInfo();
    }

    private Promise<FramePacket> sendToSession(FramePacket pack){
        final Promise<FramePacket> promise = nss.newPromise();

        if(nss==null){
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

    @Override
    public FramePacket send(FramePacket framePacket, long timeoutMS) {
        Promise<FramePacket> promise = sendToSession(framePacket);
        FramePacket pack;
        try {
            pack = promise.get(timeoutMS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException  e) {
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
        if(nss==null){
            log.error("tryDropConnection failed: nss not ready.");
            return;
        }
        nss.dropSession(nodeName, true);
    }

    @Override
    public void setDestURI(String nodeName, String s1) {
        if(nss==null){
            log.error("setDestURI failed: nss not ready.");
            return;
        }
        RemoteNSession session = nss.remoteSession(nodeName);
        if(session!=null){

        }
    }

    @Override
    public void setCurrentNodeName(String s) {
        if(nss==null){
            log.error("setDestURI failed: nss not ready.");
            while(nss==null){
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

        if(nss!=null){
            nss.changeSelfNodeName(s);
        }
        else{
            throw new MessageException("nss not ready and Thread interrupted!!!");
        }
    }

    @Override
    public void changeNodeName(String oldName, String newName) {

    }


    //    @Validate
//    public void startup(){
//        AtomicBoolean dispatcherReady = new AtomicBoolean(false);
//        AtomicReference<ScheduledFuture> ref = new AtomicReference<>(null);
//        ScheduledFuture sf = eeg.next().scheduleAtFixedRate(()->{
//            if(this.dispatcher==null){
//                return;
//            }
//            if(dispatcherReady.get()){
//                return;
//            }
//            if(dispatcherReady.compareAndSet(false, true)){
//                log.debug("dispatcher is ready, NSocket startup begin.");
//                doInit();
//                log.debug("NSocket startup finished");
//            }
//            if(ref.get()!=null&&!ref.get().isCancelled()){
//                ref.get().cancel(false);
//            }
//        }, 10, 100, TimeUnit.MILLISECONDS);
//        ref.set(sf);
//    }
}
