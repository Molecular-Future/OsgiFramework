package onight.osgi.otransio.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.AllArgsConstructor;
import onight.osgi.otransio.netty.handler.*;
import onight.osgi.otransio.util.ParamConfig;

@AllArgsConstructor
public class NChanneIniter extends ChannelInitializer {

    NSessionSets nss;

    @Override
    protected void initChannel(Channel ch) throws Exception {
        //channel option opt
        ChannelConfig cfg = ch.config();
        cfg.setOption(ChannelOption.SO_TIMEOUT, ParamConfig.TCP_SOTIMEOUT);
        cfg.setOption(ChannelOption.SO_KEEPALIVE, ParamConfig.TCP_KEEPALIVE);
        cfg.setOption(ChannelOption.TCP_NODELAY, ParamConfig.TCP_NODELAY);
        cfg.setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, ParamConfig.TCP_CONNECT_TIMEOUT);
        //handlers
        ch.pipeline()
                .addLast(new LoggingHandler(LogLevel.DEBUG))
                .addLast(new FramePacketDecoder())
                .addLast(new FramePacketEncoder())
                .addLast(new ResponseDecoder(nss))
                .addLast(new CommonFPDecoder(nss))
                .addLast(new HeartBeatHandler(nss, ParamConfig.KEEPALIVE_ENABLE, ParamConfig.KEEPALIVE_TIMEOUT))
                .addLast(new PacketHandler(nss));
    }
}
