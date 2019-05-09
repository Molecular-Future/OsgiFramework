package onight.osgi.otransio;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.OSocketImpl;
import onight.osgi.otransio.impl.SenderPolicy;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.IPacketSender;
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

    private BundleContext context;
    private PropHelper params;
    private ISocket socket;
    private IPacketSender sender;

    public OTransioStarter(BundleContext context) {
        this.context = context;
        params = new PropHelper(context);

        if(ParamConfig.SOCKET_IMPL_N.equalsIgnoreCase(ParamConfig.SOCKET_IMPL)){
            //TODO use netty impl
        }
        else{
            socket = new OSocketImpl(this.context, params);
            sender = socket.packetSender();
        }
    }

    @Validate
    public void start() {
        new Thread(()->{
            while (dispatcher == null) {
                try{
                    Thread.sleep(50);
                }
                catch(InterruptedException e){
                    log.warn("thread interrupted.", e);
                }
            }
            log.info("transio startup:");
            socket.start(dispatcher);
        }).start();
    }

    @Invalidate
    public void stop() {
        log.debug("nio stoping");
        socket.stop();
        log.debug("nio stopped ... OK");
    }

    @Bind(aggregate = true, optional = true)
    public void bindPSender(PSenderService pl) {
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
}
