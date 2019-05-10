package onight.osgi.otransio;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.OSocketImpl;
import onight.osgi.otransio.impl.SenderPolicy;
import onight.osgi.otransio.netty.NSocketImpl;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.IPacketSender;
import onight.tfw.otransio.api.NonePackSender;
import onight.tfw.otransio.api.PSenderService;
import onight.tfw.otransio.api.session.CMDService;
import onight.tfw.outils.conf.PropHelper;
import onight.tfw.proxy.IActor;
import org.apache.felix.ipojo.annotations.*;
import org.fc.zippo.dispatcher.IActorDispatcher;
import org.osgi.framework.BundleContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;

@Component(immediate = true)
@Instantiate(name = "osocketimpl")
@Provides(specifications = { ActorService.class, IActor.class })
@Data
@Slf4j
public class OTransioStarter implements Serializable, ActorService, IActor {

    private static final long serialVersionUID = -3801574196234562354L;

    @ActorRequire(name = "zippo.ddc", scope = "global")
    IActorDispatcher dispatcher = null;

    public IActorDispatcher getDispatcher(){
        return dispatcher;
    }

    public void setDispatcher(IActorDispatcher dispatcher){
        log.debug("set dispatcher: {}", dispatcher);
        this.dispatcher = dispatcher;
    }

    private BundleContext context;
    private PropHelper params;
    private ISocket socket;
    private IPacketSender sender;

    public OTransioStarter(BundleContext context) {
        this.context = context;
        params = new PropHelper(context);

        if(!ParamConfig.SOCKET_IMPL_N.equalsIgnoreCase(ParamConfig.SOCKET_IMPL)){
            log.debug("OTransioStarter is OSocketImpl");
            socket = new OSocketImpl(this.context, params);
        }
        else{
            log.debug("OTransioStarter is NSocketImpl");
            socket = new NSocketImpl();
//            socket = new NoneSocketImpl();
        }
        sender = socket.packetSender();
    }

    @Validate
    public void start() {
        log.info("OTransioStarter begin starting...");
        new Thread(()->{
            int c = 0;
            while (dispatcher == null) {
                try{
                    Thread.sleep(50);
                    c++;
                    if(c>0&&c%100==0){
                        log.debug("wait dispatcher....");
                    }
                }
                catch(InterruptedException e){
                    log.warn("thread interrupted.", e);
                }
            }
            log.info("OTransioStarter dispatcher ready, begin socket impl.");
            socket.start(dispatcher);
            log.info("OTransioStarter socket impl started.");
        }).start();
    }

    @Invalidate
    public void stop() {
        log.debug("OTransioStarter begin stoping...");
        socket.stop();
        log.debug("OTransioStarter stopped ... OK");
    }

    @Bind(aggregate = true, optional = true)
    public void bindPSender(PSenderService pl) {
//        log.debug("OTransioStarter bind sender, real class is {}", sender.getClass().getSimpleName());
        SenderPolicy.bindPSender(pl, sender);
    }

    @Unbind(aggregate = true, optional = true)
    public void unbindPSender(PSenderService pl) {
        // log.debug("Remove PSender::" + pl);
    }

    @Bind(aggregate = true, optional = true)
    public void bindCMDService(CMDService service) {
        socket.bindCMDService(service);

    }

    @Unbind(aggregate = true, optional = true)
    public void unbindCMDService(CMDService service) {
        socket.unbindCMDService(service);
    }

    @Override
    public String[] getWebPaths() {
        return new String[] { "/nio/stat", "/nio/rhr", "/nio/pbrhr", "/nio/pbrhr.do" };
    }

    @Override
    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doSomething(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doSomething(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doSomething(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doDelete(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doSomething(httpServletRequest, httpServletResponse);
    }

    private void doSomething(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-type", "application/json;charset=UTF-8");
        if (req.getServletPath().endsWith("rhr") || req.getServletPath().endsWith("pbrhr.do")) {
            resp.getWriter().write(socket.simpleJsonInfo());
        } else {
            resp.getWriter().write(socket.jsonInfo());
        }
    }



    public static class NoneSocketImpl  implements ISocket{

        NonePackSender sender = new NonePackSender();
        @Override
        public IPacketSender packetSender() {
            return sender;
        }

        @Override
        public void start(IActorDispatcher dispatcher) {
            log.debug("start server");
        }

        @Override
        public void stop() {
            log.debug("stop server");
        }

        @Override
        public void bindCMDService(CMDService service) {
            log.debug("bind new cmd service. module= {}", service.getModule());
        }

        @Override
        public void unbindCMDService(CMDService service) {
            log.debug("un-bind cmd service, module={}", service.getModule());
        }

        @Override
        public String simpleJsonInfo() {
            return "{}";
        }

        @Override
        public String jsonInfo() {
            return "{}";
        }
    }
}
