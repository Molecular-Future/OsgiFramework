package onight.osgi.otransio.util;

import onight.tfw.outils.conf.PropHelper;

public class ParamConfig {

    //"otrans.checkhealth.size"

    private static final PropHelper params = new PropHelper(null);

    /**
     * 心跳时间间隔，单位秒，默认30，最小5
     */
    public static final int HB_DELAY = params.get("otrans.checkhealth.delay", 30);

    public static final int PACK_CAHCE_MAXSIZE=params.get("otrans.pack.cache.size", 100000);

    public static final int RESEND_TIMEOUT_MS= params.get("otrans.resend.timeoutms", 60000);
}
