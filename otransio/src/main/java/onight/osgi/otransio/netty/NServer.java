package onight.osgi.otransio.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.util.ParamConfig;
import onight.tfw.mservice.NodeHelper;

@Slf4j
public class NServer {


    private ServerBootstrap bootstrap = null;
    private EventLoopGroup parentGroup = null;
    private EventLoopGroup childGroup = null;

    public void startServer(NSessionSets nss) {
        int port = NodeHelper.getCurrNodeListenInPort();

        parentGroup = new NioEventLoopGroup(ParamConfig.SERVER_PARENT_THREAD_COUNT);
        childGroup = new NioEventLoopGroup(ParamConfig.SERVER_CHILDREN_THREAD_COUNT);

        bootstrap = new ServerBootstrap()
                .group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
        .childHandler(new NChanneIniter(nss));

        bootstrap.bind(port).addListener(
                (ChannelFutureListener)f->log.debug("netty server started on {}",f.channel().localAddress()));
    }


    public void stop() {
        if(parentGroup!=null && !parentGroup.isShutdown() && !parentGroup.isTerminated()&&!parentGroup.isShuttingDown()){
            parentGroup.shutdownGracefully().addListener(f-> log.debug("netty server parentGroup shutdown."));
        }
        if(childGroup!=null && !childGroup.isShutdown() && !childGroup.isTerminated()&&!childGroup.isShuttingDown()){
            childGroup.shutdownGracefully().addListener(f-> log.debug("netty server childGroup shutdown."));
        }
    }
}
