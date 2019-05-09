package onight.osgi.otransio.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import onight.osgi.otransio.util.ParamConfig;

public class NClient {
    private Bootstrap bs;
    private EventLoopGroup eeg;

    public NClient(NSessionSets nss) {
        eeg = new NioEventLoopGroup(ParamConfig.CLIENT_THREAD_COUNT);
        bs = new Bootstrap().group(eeg)
                .channel(NioSocketChannel.class)
                .handler(new NChanneIniter(nss));
    }

    public ChannelFuture connect(String ip, int port){
        return bs.connect(ip, port);
    }

    public Future<?> shutdown(){
        return eeg.shutdownGracefully();
    }
}
