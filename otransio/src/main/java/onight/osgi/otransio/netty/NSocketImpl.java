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
public class NSocketImpl implements ISocket {

    private volatile IActorDispatcher dispatcher = null;
    private BundleContext context;
    private NServer server;
    volatile NSessionSets nss;
    private NTransSender sender = new NTransSender(NSocketImpl.this);

    public NSocketImpl(BundleContext context) {
        this.context = context;
        this.server = new NServer();
    }

    @Override
    public IPacketSender packetSender() {
        return sender;
    }

    @Override
    public void start(IActorDispatcher dispatcher) {
        log.debug("NSocket starting");
        this.dispatcher = dispatcher;
        this.nss = new NSessionSets(NSocketImpl.this, this.dispatcher);

        server.startServer(this.nss);
    }

    @Override
    public void stop() {
        log.debug("NSocket stopping");
        nss.shutdown();
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
