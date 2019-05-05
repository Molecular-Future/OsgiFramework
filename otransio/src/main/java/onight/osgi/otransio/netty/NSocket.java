package onight.osgi.otransio.netty;

import io.netty.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.LocalMessageProcessor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.osgi.otransio.impl.OSocketImpl;
import onight.osgi.otransio.impl.SenderPolicy;
import onight.osgi.otransio.netty.NServer;
import onight.osgi.otransio.netty.NSocket;
import onight.osgi.otransio.netty.impl.NServerImpl;
import onight.osgi.otransio.sm.MSessionSets;
import onight.tfw.mservice.NodeHelper;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.IPacketSender;
import onight.tfw.otransio.api.PSender;
import onight.tfw.otransio.api.PSenderService;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.CMDService;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.outils.conf.PropHelper;
import onight.tfw.proxy.IActor;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.*;
import org.fc.zippo.dispatcher.IActorDispatcher;
import org.osgi.framework.BundleContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component(immediate = true)
@Instantiate(name = "nsocketimpl")
@Provides(specifications = { ActorService.class, IActor.class })
@Slf4j
public class NSocket implements  ActorService, IActor {



    @ActorRequire(name = "zippo.ddc", scope = "global")
    IActorDispatcher dispatcher = null;

    private EventExecutorGroup eeg;
    private BundleContext context;
    private PropHelper params;
    private NServer server;
    private NSessionSets nss;
    private LocalModuleManager lmm;
    private IPacketSender sender;

    transient LocalMessageProcessor localProcessor = new LocalMessageProcessor();


    public NSocket(BundleContext context) {
        this.context = context;
        params = new PropHelper(context);
        //TODO 需要配置线程数量
        eeg = new DefaultEventExecutorGroup(8);
        this.nss = new NSessionSets(NSocket.this, params);
        this.server = new NServerImpl();
    }

    @Validate
    public void startup(){
        AtomicBoolean dispatcherReady = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture> ref = new AtomicReference<>(null);
        ScheduledFuture sf = eeg.next().scheduleAtFixedRate(()->{
            if(this.dispatcher==null){
                return;
            }
            if(dispatcherReady.get()){
                return;
            }
            if(dispatcherReady.compareAndSet(false, true)){
                log.debug("dispatcher is ready, NSocket startup begin.");
                doInit();
                log.debug("NSocket startup finished");
            }
            if(ref.get()!=null&&!ref.get().isCancelled()){
                ref.get().cancel(false);
            }
        }, 10, 100, TimeUnit.MILLISECONDS);
        ref.set(sf);
    }

    private void doInit(){
//        localProcessor.poolSize = params.get("org.zippo.otransio.maxrunnerbuffer", 1000);
        server.startServer(this, this.params);
    }

    /**
     * 发送消息
     * @param fp 消息
     * @return
     */
    Promise<FramePacket> sendPacket(FramePacket fp){
        String destTo = fp.getExtStrProp(PackHeader.PACK_TO);
        String uri = fp.getExtStrProp(PackHeader.PACK_URI);
        if (uri == null) {
            uri = destTo;
        }
        Promise<FramePacket> promise = eeg.next().newPromise();
        //先通过destTo查询是否已存在连接
        //如果不存在，通过uri来创建一个连接
        if (StringUtils.isNotBlank(uri)) {
            NodeInfo node = NodeInfo.fromURI(uri, destTo);
            if(isLocalNode(node)){
                //当前节点的Module，直接调用
            }
            else{
                //远程节点，创建连出连接
            }
        }
        return promise;
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



    @Invalidate
    public void shutdown() {
        log.debug("NSocket stopping");
        server.stop();
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
        LocalModuleSession ms = nss.addLocalModule(service.getModule());
        for (String cmd : service.getCmds()) {
            ms.registerService(cmd, service);
        }
    }

    @Unbind(aggregate = true, optional = true)
    public void unbindCMDService(CMDService service) {
        // log.debug("Remove ModuleSession::" + service);
    }

    @Override
    public String[] getWebPaths() {
        return new String[]{"/nio/stat", "/nio/rhr", "/nio/pbrhr", "/nio/pbrhr.do"};
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

    public void doSomething(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-type", "application/json;charset=UTF-8");
        if (req.getServletPath().endsWith("rhr") || req.getServletPath().endsWith("pbrhr.do")) {
            resp.getWriter().write(nss.getSimpleJsonInfo());
        } else {
            resp.getWriter().write(nss.getJsonInfo());
        }
    }

    private boolean isLocalNode(NodeInfo node){
        return (StringUtils.equalsIgnoreCase(node.getAddr(), nss.self.getNodeInfo().getAddr())
                && node.getPort() == nss.self.getNodeInfo().getPort())
                || nss.self.getNodeInfo().getNodeName().equals(node.getNodeName());
    }
}
