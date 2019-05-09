package onight.osgi.otransio;

import onight.tfw.otransio.api.IPacketSender;
import onight.tfw.otransio.api.session.CMDService;
import org.fc.zippo.dispatcher.IActorDispatcher;

public interface ISocket {

    IPacketSender packetSender();
    void start(IActorDispatcher dispatcher);
    void stop();
    void bindCMDService(CMDService service);
    void unbindCMDService(CMDService service);
    String simpleJsonInfo();
    String jsonInfo();
}
