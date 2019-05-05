package onight.osgi.otransio.util;

import onight.tfw.outils.conf.PropHelper;

public class ParamConfig {

    //"otrans.checkhealth.size"

    private static final PropHelper params = new PropHelper(null);

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
}
