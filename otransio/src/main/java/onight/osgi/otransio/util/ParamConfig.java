package onight.osgi.otransio.util;

import onight.tfw.outils.conf.PropHelper;

public class ParamConfig {

    private static final PropHelper params = new PropHelper(null);

    public static final String SOCKET_IMPL_O = "grizzly";
    public static final String SOCKET_IMPL_N = "netty";
    /**
     * transio层的实现，支持：netty 和 grizzly，默认为：grizzly
     */
    public static final String SOCKET_IMPL = params.get("org.csc.transio.impl", SOCKET_IMPL_O);
    /**
     * ntransio内部使用的线程数，默认为处理器核心数*2
     */
    public static final int NSOCK_THREAD_COUNT = params.get("ntrans.thread.count", Runtime.getRuntime().availableProcessors()*2);
    /**
     * tcp 读超时时间，单位毫秒，默认60秒
     */
    public static final int TCP_SOTIMEOUT = params.get("ntrans.tcp.sotimeout", 60*1000);
    /**
     * tcp 连接超时时间，单位毫秒，默认10秒
     */
    public static final int TCP_CONNECT_TIMEOUT = params.get("ntrans.tcp.connect.timeout", 10*1000);
    /**
     * tcp keep-alive，默认为 true
     */
    public static final boolean TCP_KEEPALIVE = "true".equalsIgnoreCase(params.get("ntrans.tcp.keepalive", "true"));
    /**
     * tcp no-delay，默认为 true
     */
    public static final boolean TCP_NODELAY = "true".equalsIgnoreCase(params.get("ntrans.tcp.nodelay", "true"));
    /**
     * 服务的parent线程数，默认为 0（使用Netty默认配置）
     */
    public static final int SERVER_PARENT_THREAD_COUNT = params.get("ntrans.server.parent", 0);
    /**
     * 服务的children线程数，默认为 0（使用Netty默认配置）
     */
    public static final int SERVER_CHILDREN_THREAD_COUNT = params.get("ntrans.server.children", 0);
    /**
     * 客户端的线程数，默认为 0（使用Netty默认配置）
     */
    public static final int CLIENT_THREAD_COUNT = params.get("ntrans.client.thread.count", 0);
    /**
     * 是否保持连接，如果为true，则当空闲时间达到KEEPALIVE_TIMEOUT，发送HB消息。默认：true
     */
    public static final boolean KEEPALIVE_ENABLE = "true".equalsIgnoreCase(params.get("ntrans.keepalive.enable", "true"));
    /**
     * 空闲超时时间，单位秒，默认30，最小5
     */
    public static final int KEEPALIVE_TIMEOUT = params.get("ntrans.keepalive.timeout", 30);

    /**
     * 请求消息最大缓存数量，默认：10万
     */
    public static final int PACK_CAHCE_MAXSIZE = params.get("ntrans.pack.cache.size", 100000);
    /**
     * 连接未认证超时时间，单位：秒，默认：30秒
     */
    public static final int NO_AUTH_TIMEOUT_SEC = params.get("ntrans.noauth.timeout", 30);
    /**
     * 重连时间间隔，单位：毫秒，默认：5秒
     */
    public static final int RECONNECT_TIME_MS = params.get("ntrans.reconnect.ms", 5000);
    /**
     * 重连次数限制，当为0或负数时表示无限制。默认为：-1
     */
    public static final int RECONNECT_COUNT = params.get("ntrans.reconnect.count", -1);
    /**
     * 响应等待时间，单位：毫秒，默认：60秒
     */
    public static final int SEND_WAIT_TIMEOUT_MS = params.get("ntrans.send.wait.ms", 60000);
    /**
     * 发送队列的初始大小，默认：1000
     */
    public static final int SEND_WRITEQ_INIT = params.get("ntrans.send.writeq.init", 1000);
    /**
     * 待发送队列检查时间间隔，单位：毫秒，默认：50毫秒
     */
    public static final int SEND_WRITEQ_CHECK_DELAY = params.get("ntrans.send.writeq.delay", 50);
    /**
     * 发送重试次数，默认：3次
     */
    public static final int SEND_RETRY_COUNT = params.get("ntrans.retry.count", 3);
    /**
     * 发送重试间隔，单位：毫秒，默认：3秒
     */
    public static final int SEND_RETRY_DELAY_MS = params.get("ntrans.retry.delay.ms", 3000);
    /**
     * 清理空的session的时间间隔，单位：秒，默认：30分钟
     */
    public static final int CLEAN_EMPTY_SESSION = params.get("ntrans.clean.empty.session", 1800);
}
