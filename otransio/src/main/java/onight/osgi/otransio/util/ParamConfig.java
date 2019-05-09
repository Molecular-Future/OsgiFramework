package onight.osgi.otransio.util;

import onight.tfw.outils.conf.PropHelper;

public class ParamConfig {

    //"otrans.checkhealth.size"

    private static final PropHelper params = new PropHelper(null);

    public static final String SOCKET_IMPL_O = "osocket";
    public static final String SOCKET_IMPL_N = "nsocket";
    public static final String SOCKET_IMPL = params.get("org.csc.transio.impl", "osocket");

    public static final int NSOCK_THREAD_COUNT = params.get("ntrans.thread.count", 8);
    //TCP 连接参数
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
     * 客户端的线程是否共享，默认为 false
     */
    public static final boolean CLIENT_THREAD_SHARED = "true".equalsIgnoreCase(params.get("ntrans.client.thread.shared", "false"));

    /**
     * 心跳时间间隔，单位秒，默认30，最小5
     */
    public static final int HB_DELAY = params.get("otrans.checkhealth.delay", 30);

    /**
     * 请求消息最大缓存数量
     */
    public static final int PACK_CAHCE_MAXSIZE=params.get("otrans.pack.cache.size", 100000);

    /**
     * 发送超时
     */
    public static final int RESEND_TIMEOUT_MS = params.get("otrans.resend.timeoutms", 60000);

    /**
     * 每IP最大连接数
     */
    public static final int MAX_CONN_EACH_IP = params.get("otrans.max.conn.each.ip", 100);

    /**
     * 连接未认证超时时间
     */
    public static final int NO_AUTH_TIMEOUT_SEC = params.get("otrans.max.conn.timeout.sec", 30);

    public static final int RECONNECT_TIME_MS = params.get("ntrans.reconnect.ms", 5000);

    public static final int SEND_RETRY_TIMES = params.get("ntrans.retry.times", 3);

    public static final int SEND_RETRY_DELAY_MS = params.get("ntrans.retry.delay.ms", 3000);
}
