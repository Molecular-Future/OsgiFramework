package onight.osgi.otransio.netty;

import onight.tfw.outils.conf.PropHelper;

public interface NServer {
    /**
     * 开始服务
     * @param socket
     * @param params
     */
    void startServer(NSocket socket, PropHelper params);
    void stop();
}
