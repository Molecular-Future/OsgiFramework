package onight.osgi.otransio.netty.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.netty.NServer;
import onight.osgi.otransio.netty.NSocket;
import onight.tfw.mservice.NodeHelper;
import onight.tfw.outils.conf.PropHelper;

@Slf4j
public class NServerImpl implements NServer {
    private ServerBootstrap bootstrap = null;
    private EventLoopGroup parentGroup = null;
    private EventLoopGroup childGroup = null;
    @Override
    public void startServer(NSocket socket, PropHelper params) {
        int port = NodeHelper.getCurrNodeListenInPort();
        int parentCore = params.get("otrans.kernel.core", 0);
        int childCore = params.get("otrans.worker.core", 0);
        int soTimeout = params.get("otransio.client.sotimeout", 10);

        parentGroup = new NioEventLoopGroup(parentCore);
        childGroup = new NioEventLoopGroup(childCore);

        //TODO add encoders & decoders
        bootstrap = new ServerBootstrap()
                .group(parentGroup, childGroup)
                .option(ChannelOption.SO_TIMEOUT, soTimeout)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .channel(NioServerSocketChannel.class);
        bootstrap.bind(port).addListener(
                (ChannelFutureListener)f->log.debug("nserver started on {}",f.channel().localAddress()));
    }

    @Override
    public void stop() {
        if(parentGroup!=null && !parentGroup.isShutdown() && !parentGroup.isTerminated()&&!parentGroup.isShuttingDown()){
            parentGroup.shutdownGracefully().addListener(f-> log.debug("nserver parentGroup shutdown."));
        }
        if(childGroup!=null && !childGroup.isShutdown() && !childGroup.isTerminated()&&!childGroup.isShuttingDown()){
            childGroup.shutdownGracefully().addListener(f-> log.debug("nserver childGroup shutdown."));
        }
    }
}
