package onight.osgi.otransio.impl;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import onight.osgi.otransio.ISocket;
import onight.tfw.otransio.api.*;
import org.apache.commons.lang3.StringUtils;
import org.fc.zippo.dispatcher.IActorDispatcher;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.osgi.framework.BundleContext;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.ck.CKConnPool;
import onight.osgi.otransio.ck.NewConnCheckHealth;
import onight.osgi.otransio.exception.NoneServerException;
import onight.osgi.otransio.exception.TransIOException;
import onight.osgi.otransio.nio.OServer;
import onight.osgi.otransio.nio.PacketTuple;
import onight.osgi.otransio.sm.MSessionSets;
import onight.osgi.otransio.sm.OutgoingSessionManager;
import onight.osgi.otransio.sm.RemoteModuleBean;
import onight.osgi.otransio.sm.RemoteModuleSession;
import onight.tfw.async.CompleteHandler;
import onight.tfw.mservice.NodeHelper;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.beans.UnknowModuleBody;
import onight.tfw.otransio.api.session.CMDService;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import onight.tfw.outils.conf.PropHelper;
import onight.tfw.proxy.IActor;

@Slf4j
public class OSocketImpl implements Serializable, ISocket {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6301364196672462354L;

	@Getter
	private transient PropHelper params;

	public static String PACK_FROM = PackHeader.PACK_FROM;
	public static String PACK_TO = PackHeader.PACK_TO;
	public static String PACK_URI = PackHeader.PACK_URI;

	protected final Attribute<String> connectBCUID = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
			.createAttribute("osocket.connection.bcuid");

	public static String DROP_CONN = "DROP**";

	@Getter
	IActorDispatcher dispatcher = null;

	void setDispatcher(IActorDispatcher dispatcher) {
		log.info("setDispatcher==" + dispatcher);
		this.dispatcher = dispatcher;
		localProcessor.dispatcher = dispatcher;
	}

	BundleContext context;

	public OSocketImpl(BundleContext context, PropHelper helper) {
		this.context = context;
		params = helper;
		mss = new MSessionSets(OSocketImpl.this,params);
	}

	@Getter
	transient MSessionSets mss;

	transient OTransSender sender = new OTransSender(this);

	transient OServer server = new OServer();

	@Getter
	transient OutgoingSessionManager osm;
	// transient ThreadPoolExecutor localPool;

	// transient ConcurrentHashMap<String, PacketQueue> queueBybcuid = new
	// ConcurrentHashMap<>();
	transient LocalMessageProcessor localProcessor = new LocalMessageProcessor();

	public String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
		}
		return "localhost";
	}

	@Override
	public IPacketSender packetSender(){
		return sender;
	}

	@Override
	public void start(IActorDispatcher dispatcher) {
		setDispatcher(dispatcher);
		osm = new OutgoingSessionManager(OSocketImpl.this, params, mss);
		mss.setOsm(osm);
		localProcessor.poolSize = params.get("org.zippo.otransio.maxrunnerbuffer", 1000);
		server.startServer(OSocketImpl.this, params);
		// 建立外联服务
		osm.init();
	}

	@Override
	public void stop() {
		server.stop();
	}

	@Override
	public void bindCMDService(CMDService service) {
		// log.debug("Register CMDService::" + service.getModule());
		LocalModuleSession ms = mss.addLocalMoudle(service.getModule());
		for (String cmd : service.getCmds()) {
			ms.registerService(cmd, service);
		}
	}

	@Override
	public void unbindCMDService(CMDService service) {
		// log.debug("Remove ModuleSession::" + service);
	}

	@Override
	public String simpleJsonInfo(){
		return mss.getSimpleJsonInfo();
	}

	@Override
	public String jsonInfo(){
		return mss.getJsonInfo();
	}

	public void onPacket(FramePacket pack, final CompleteHandler handler, Connection<?> conn) throws TransIOException {
		/**
		 * 1 登录消息：在MSessionSets中发送
		 */

		if (PackHeader.REMOTE_LOGIN.equals(pack.getModuleAndCMD()) && conn != null) {// 来自远端的登录
			RemoteModuleBean rmb = pack.parseBO(RemoteModuleBean.class);
			String node_from = pack.getExtStrProp(PACK_FROM);
			if (StringUtils.isBlank(node_from)) {
				node_from = rmb.getNodeInfo().getNodeName();
			} else {
				rmb.getNodeInfo().setNodeName(node_from);
			}
			connectBCUID.set(conn, node_from);
			// log.debug("Get New Login Connection From:" +
			// rmb.getNodeInfo().getUname() + ",nodeid=" + node_from
			// + ",conn=" + conn);
			if (node_from != null) {
				PSession session = mss.byNodeName(node_from);
				if (session != null && session instanceof RemoteModuleSession) {
					//如果已经有session，则把连接加入到session
					RemoteModuleSession rms = (RemoteModuleSession) session;
					rms.addConnection(conn);
					CKConnPool ckpool = rms.getConnsPool();
					ckpool.setIp(rmb.getNodeInfo().getAddr());
					ckpool.setPort(rmb.getNodeInfo().getPort());
					// rms.getWriterQ().resendBacklogs();
				} else {
					//如果没有session，则创建一个
					try {
						osm.createOutgoingSSByURI(rmb.getNodeInfo());
					} catch (NoneServerException e) {
					}
				}
				conn.getAttributes().setAttribute(NewConnCheckHealth.CONN_AUTH_INFO, rmb);
			} else {
				log.debug("unknow node id_from:" + node_from);
			}
			// conn.write(mss.getLocalModulesPacketBack());
		} else if (DROP_CONN.equals(pack.getModuleAndCMD())) {// 来自远端的模块信息返回
			log.error("get drop connection message");
			osm.dropSessionByRemote(conn);
			conn.closeSilently();
		} else if (PackHeader.CMD_HB.equals(pack.getModuleAndCMD())) {// 来自远端的心跳线
			log.trace("[HB] From " + conn.getPeerAddress() + " , to " + conn.getLocalAddress());
		} else {
			routePacket(pack, handler, conn);
		}
	}

	public void routePacket(FramePacket pack, final CompleteHandler handler) throws TransIOException {
		// from local
		routePacket(pack, handler, null);
	}

	public void routePacket(FramePacket pack, final CompleteHandler handler, Connection<?> conn)
			throws TransIOException {
		if (pack.isResp() && pack.getExtHead().isExist(mss.getPackIDKey())) {
			// 检查是否为响应包
			String expackid = pack.getExtStrProp(mss.getPackIDKey());
			PacketTuple pt = mss.getPackMaps().remove(expackid);
			if (pt != null) {
				pt.getHandler().onFinished(pack);
				mss.getPackPool().retobj(pt);
			} else {
				Object opackid = pack.getExtHead().remove(mss.getPackIDKey());
				if (pack.getBody() != null && pack.getBody().length > 0) {
					log.error("unknow ack:" + opackid + ",gcmd=" + pack.getModuleAndCMD() + ",conn=" + conn + ",kvs="
							+ pack.getExtHead().getVkvs() + ",h=" + pack.getExtHead().getHiddenkvs() + ",timepost="
							+ getPackTimeout(expackid));
				} else {
					log.error("unknow ack:" + opackid + ",gcmd=" + pack.getModuleAndCMD() + ",conn=" + conn
							+ ",timepost=" + getPackTimeout(expackid));
				}
				// handler.onFinished(PacketHelper.toPBReturn(pack, new
				// LoopPackBody(mss.getPackIDKey(), pack)));
			}
			return;
		}
		String destTO = pack.getExtStrProp(PACK_TO);
		String from = pack.getExtStrProp(PACK_FROM);

		PSession ms = null;

		// 固定给某个节点id的
		if (StringUtils.isNotBlank(destTO) && conn == null) {
			//通过nodeName，发送给一个节点
			ms = mss.byNodeName(destTO);
			if (ms == null) {// not found
				String uri = pack.getExtStrProp(PACK_URI);
				if (uri == null) {
					uri = destTO;
				}
				synchronized (uri.intern()) {
					ms = mss.byNodeName(destTO);
					if (ms == null) {// second entry
						if (StringUtils.isNotBlank(uri)) {
							NodeInfo node = NodeInfo.fromURI(uri, destTO);
							try {
								if (StringUtils.equalsIgnoreCase(node.getAddr(), NodeHelper.getCurrNodeListenOutAddr())
										&& node.getPort() == NodeHelper.getCurrNodeListenOutPort()) {
									ms = mss.getLocalsessionByModule().get(pack.getModule());
									log.debug("get Local new Connection:" + uri + ":name=" + destTO + ",from=" + from);
								} else {
									log.debug("creating new Connection:" + uri + ":name=" + destTO + ",from=" + from);
									ms = osm.createOutgoingSSByURI(node);
								}
							} catch (Exception e) {
								log.error("route ERROR:" + e.getMessage(), e);
								throw new MessageException(e);
							}
						}
					}
				}
			}
		}
		else {
			// re
			if (pack.isResp() && conn != null) {
				log.error("pack respone to unknow :" + pack.getCMD() + "" + pack.getModule() + "," + pack.getExtHead()
						+ ",bodysize=" + pack.getFixHead().getBodysize());
			}
			ms = mss.getLocalsessionByModule().get(pack.getModule());
		}
		if (ms != null) {
			if (ms instanceof LocalModuleSession) {
				localProcessor.route2Local(pack, handler, ms);
			} else {
				RemoteModuleSession rms = (RemoteModuleSession) ms;
				if (StringUtils.equalsIgnoreCase(rms.getNodeInfo().getAddr(), NodeHelper.getCurrNodeListenOutAddr())
						&& rms.getNodeInfo().getPort() == NodeHelper.getCurrNodeListenOutPort()) {
					LocalModuleSession lms = mss.getLocalsessionByModule().get(pack.getModule());
					localProcessor.route2Local(pack, handler, lms);
				} else {
					if (conn != null && destTO != null) {// change node bcuid
						if (connectBCUID.isSet(conn)) {
							String orgbcuid = connectBCUID.get(conn);
							if (!StringUtils.equals(orgbcuid, destTO)) {
								log.error("connection my change pack_from:" + orgbcuid + "==>" + destTO + ",conn="
										+ conn);
								connectBCUID.set(conn, destTO);
								rms.addConnection(conn);
								PSession oldms = mss.byNodeName(orgbcuid);
								if (oldms instanceof RemoteModuleSession) {
									log.error("remove connection from" + orgbcuid + "conn=" + conn);
									((RemoteModuleSession) oldms).removeConnection(conn);
								}
							}
						} else {
							connectBCUID.set(conn, destTO);
						}
					}

					ms.onPacket(pack, handler);
				}
			}
		} else {
			// 没有找到对应的消息
			log.error("UnknowModule:" + pack.getModule() + ",CMD=" + pack.getCMD() + ",gcmd=" + pack.getModuleAndCMD()
					+ ",from=" + from + ",conn=" + conn + ",destTO=" + destTO);
			if (pack.isSync()) {
				handler.onFinished(
						PacketHelper.toPBReturn(pack, new UnknowModuleBody(pack.getModule() + ",to=" + destTO, pack)));
			}
		}

	}

	public synchronized void tryDropConnection(String packNameOrId) {
		mss.dropSession(packNameOrId, true);
		// PacketQueue queue = queueBybcuid.remove(packNameOrId);
		// if (queue != null) {
		// queue.setStop(true);
		// }
	}

	public synchronized void renameSession(String oldname, String newname) {
		if (!StringUtils.equals(oldname, newname)) {
			log.debug("renameSession:" + oldname + "==>" + newname);
			mss.renameSession(oldname, newname);
		}
	}

	public static String getPackTimeout(String key) {
		String times[] = key.split("_");
		if (times.length > 2) {
			long startTime = Long.parseLong(times[times.length - 2]);
			return "" + (System.currentTimeMillis() - startTime);
		}
		return "--not_time_pack--";
	}

}
