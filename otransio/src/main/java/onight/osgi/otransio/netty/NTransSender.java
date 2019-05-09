package onight.osgi.otransio.netty;

import onight.tfw.async.CallBack;
import onight.tfw.async.FutureSender;
import onight.tfw.otransio.api.beans.FramePacket;

public class NTransSender extends FutureSender {
    private NSocketImpl socket;

    public NTransSender(NSocketImpl socket){
        this.socket = socket;
    }

    @Override
    public FramePacket send(FramePacket framePacket, long timeoutMs) {
        return null;
    }

    @Override
    public void asyncSend(FramePacket framePacket, CallBack<FramePacket> callBack) {

    }

    @Override
    public void post(FramePacket framePacket) {

    }

    @Override
    public void tryDropConnection(String s) {

    }

    @Override
    public void setDestURI(String s, String s1) {

    }

    @Override
    public void setCurrentNodeName(String s) {

    }

    @Override
    public void changeNodeName(String s, String s1) {

    }
}
