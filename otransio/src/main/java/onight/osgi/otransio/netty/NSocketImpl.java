package onight.osgi.otransio.netty;

import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.ISocket;
import onight.tfw.otransio.api.IPacketSender;
import onight.tfw.otransio.api.session.CMDService;
import onight.tfw.otransio.api.session.LocalModuleSession;
import org.fc.zippo.dispatcher.IActorDispatcher;

@Slf4j
public class NSocketImpl implements ISocket {

    private NServer server;
    volatile NSessionSets nss;
    private IPacketSender sender;

    public NSocketImpl() {
        this.server = new NServer();
        this.nss = new NSessionSets(this);
        this.sender = new NTransSender(this.nss);
    }

    @Override
    public IPacketSender packetSender() {
        return this.sender;
    }

    @Override
    public void start(IActorDispatcher dispatcher) {
        log.debug("NSocket starting");
        this.nss.setDispatcher(dispatcher);
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
